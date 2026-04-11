package com.example.coin_nest.autobook

import android.content.Context
import android.util.Log

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

    fun track(
        context: Context,
        event: String,
        reason: String? = null,
        packageName: String? = null
    ) {
        val now = System.currentTimeMillis()
        val safeReason = reason.orEmpty().take(180)
        val safePkg = packageName.orEmpty().take(120)
        Log.i(TAG, "event=$event pkg=$safePkg reason=$safeReason ts=$now")
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_LAST_EVENT, event)
            putString(KEY_LAST_REASON, safeReason)
            putString(KEY_LAST_PACKAGE, safePkg)
            putLong(KEY_LAST_EVENT_MS, now)
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
}
