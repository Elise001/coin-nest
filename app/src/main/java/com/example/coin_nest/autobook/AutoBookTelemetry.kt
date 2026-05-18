package com.example.coin_nest.autobook

import android.content.Context
import android.util.Log
import com.example.coin_nest.util.MoneyFormat
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class AutoBookAuditEvent(
    val event: String,
    val reason: String,
    val packageName: String,
    val occurredAtEpochMs: Long
)

object AutoBookTelemetry {
    private const val TAG = "AutoBookTrace"
    private const val PREF_NAME = "autobook_diagnostics"
    private const val KEY_LAST_EVENT = "last_event"
    private const val KEY_LAST_REASON = "last_reason"
    private const val KEY_LAST_PACKAGE = "last_package"
    private const val KEY_LAST_EVENT_MS = "last_event_ms"
    private const val KEY_LAST_LISTENER_CONNECTED_MS = "last_listener_connected_ms"
    private const val KEY_LAST_NOTIFY_RECEIVED_MS = "last_notify_received_ms"
    private const val KEY_LAST_NOTIFY_PACKAGE = "last_notify_package"
    private const val KEY_LAST_NOTIFY_PREVIEW = "last_notify_preview"
    private const val KEY_RECENT_EVENTS = "recent_events"
    private const val MAX_RECENT_EVENTS = 80
    private const val MAX_REASON_LENGTH = 800
    private val logTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun track(
        context: Context,
        event: String,
        reason: String? = null,
        packageName: String? = null
    ) {
        val now = System.currentTimeMillis()
        val safeReason = reason.orEmpty().take(MAX_REASON_LENGTH)
        val safePkg = packageName.orEmpty().take(120)
        Log.i(TAG, "event=$event pkg=$safePkg reason=$safeReason ts=$now")
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val recentEvents = appendRecentEvent(
            existingJson = prefs.getString(KEY_RECENT_EVENTS, null),
            event = event,
            reason = safeReason,
            packageName = safePkg,
            occurredAtEpochMs = now
        )
        prefs.edit().apply {
            putString(KEY_LAST_EVENT, event)
            putString(KEY_LAST_REASON, safeReason)
            putString(KEY_LAST_PACKAGE, safePkg)
            putLong(KEY_LAST_EVENT_MS, now)
            putString(KEY_RECENT_EVENTS, recentEvents.toString())
            if (event == "listener_connected") {
                putLong(KEY_LAST_LISTENER_CONNECTED_MS, now)
            }
            if (event == "notify_received") {
                putLong(KEY_LAST_NOTIFY_RECEIVED_MS, now)
                putString(KEY_LAST_NOTIFY_PACKAGE, safePkg)
                putString(KEY_LAST_NOTIFY_PREVIEW, safeReason)
            }
        }.apply()
    }

    fun trackRecognizedPayment(
        context: Context,
        packageName: String,
        channel: String,
        payment: ParsedPayment
    ) {
        track(
            context = context,
            event = "payment_recognized",
            packageName = packageName,
            reason = payment.toDebugReason(channel)
        )
    }

    fun readRecentAuditEvents(context: Context): List<AutoBookAuditEvent> {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_RECENT_EVENTS, null)
            ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    add(
                        AutoBookAuditEvent(
                            event = obj.optString("event"),
                            reason = obj.optString("reason"),
                            packageName = obj.optString("package"),
                            occurredAtEpochMs = obj.optLong("ts")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun clearRecentAuditEvents(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_RECENT_EVENTS)
            .apply()
    }

    fun readLastReason(context: Context): String? {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_REASON, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun readLastListenerConnectedMs(context: Context): Long {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_LISTENER_CONNECTED_MS, 0L)
    }

    fun readLastNotifyReceivedMs(context: Context): Long {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_NOTIFY_RECEIVED_MS, 0L)
    }

    fun readLastNotifyPackage(context: Context): String? {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_NOTIFY_PACKAGE, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun readLastNotifyPreview(context: Context): String? {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_NOTIFY_PREVIEW, null)
            ?.takeIf { it.isNotBlank() }
    }

    private fun appendRecentEvent(
        existingJson: String?,
        event: String,
        reason: String,
        packageName: String,
        occurredAtEpochMs: Long
    ): JSONArray {
        val newItem = JSONObject().apply {
            put("event", event)
            put("reason", reason)
            put("package", packageName)
            put("ts", occurredAtEpochMs)
        }
        val previous = runCatching { JSONArray(existingJson.orEmpty()) }.getOrNull() ?: JSONArray()
        val output = JSONArray().put(newItem)
        var index = 0
        while (index < previous.length() && output.length() < MAX_RECENT_EVENTS) {
            previous.optJSONObject(index)?.let { output.put(it) }
            index++
        }
        return output
    }

    private fun ParsedPayment.toDebugReason(channel: String): String {
        val occurredAt = Instant.ofEpochMilli(occurredAtEpochMs)
            .atZone(ZoneId.systemDefault())
            .format(logTimeFormatter)
        val amountPrefix = if (type.name.equals("INCOME", ignoreCase = true)) "+" else "-"
        return buildString {
            append("channel=").append(channel)
            append(" source=").append(source)
            append(" type=").append(type.name)
            append(" amount=").append(amountPrefix).append(MoneyFormat.fromCents(amountCents))
            append(" category=").append(parentCategory).append('/').append(childCategory)
            append(" occurred=").append(occurredAt)
            append(" ref=").append(transactionRef ?: "-")
            append(" fingerprint=").append(fingerprint ?: "-")
            append(" note=").append(note)
        }
    }
}
