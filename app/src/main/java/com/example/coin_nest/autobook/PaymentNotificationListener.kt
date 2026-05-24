package com.example.coin_nest.autobook

import android.content.ComponentName
import android.app.Notification
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast
import com.example.coin_nest.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PaymentNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastAutoHintMs: Long = 0L

    // Layer 1: whitelist + ignore self app notifications.
    private val allowedPackages = setOf(
        "com.eg.android.AlipayGphone",
        "com.tencent.mm",
        "com.taobao.taobao",
        "com.jingdong.app.mall",
        "com.xunmeng.pinduoduo",
        "com.sankuai.meituan",
        "com.unionpay",
        "cmb.pb",
        "com.chinamworld.main",
        "com.icbc"
    )

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(applicationContext)
        PaymentActionNotifier.ensureChannel(applicationContext)
        AutoBookTelemetry.track(applicationContext, event = "listener_create")
        debugPopup("AUTOBK service started")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        runCatching {
            val n = sbn?.notification ?: return
            val packageName = sbn.packageName.orEmpty()
            if (!isWhitelistedPaymentNotification(packageName)) return

            val extras = n.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = buildMergedNotificationText(extras)
            val preview = listOfNotNull(title, text)
                .joinToString(" ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(800)
            AutoBookTelemetry.track(
                applicationContext,
                event = "notify_received",
                packageName = packageName,
                reason = preview
            )
            val summaryTag = if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) "[SUMMARY]" else ""
            debugPopup("PAYMENT_NOTIFY$summaryTag: $packageName ${title.orEmpty().take(12)}")

            scope.launch {
                runCatching {
                    AutoBookProcessor.process(
                        context = applicationContext,
                        repository = ServiceLocator.repository(),
                        event = AutoBookRawEvent(
                            packageName = packageName,
                            title = title,
                            text = text,
                            occurredAtEpochMs = sbn.postTime,
                            channel = AutoBookChannel.NOTIFY
                        )
                    )
                }.onSuccess { result ->
                    when (result) {
                        is AutoBookProcessResult.Rejected -> {
                            debugPopup("IGNORE_NOTIFY($packageName): ${result.reason}")
                        }
                        is AutoBookProcessResult.Dropped -> {
                            debugPopup("AUTOBOOK_DROP: reason=${result.reason}")
                        }
                        is AutoBookProcessResult.Inserted -> {
                            val insertResult = result.insertResult
                            val parsed = result.payment
                            when {
                                insertResult.insertedId != null && insertResult.shouldNotify -> {
                                    showAutoDetectedToast()
                                    debugPopup("AUTOBOOK_OK: ${parsed.source} ${parsed.amountCents / 100.0}")
                                    PaymentActionNotifier.notifyPendingPayment(
                                        context = applicationContext,
                                        txId = insertResult.insertedId,
                                        amountCents = parsed.amountCents,
                                        type = parsed.type.name,
                                        source = parsed.source,
                                        note = parsed.note
                                    )
                                }
                                insertResult.insertedId != null -> {
                                    debugPopup("AUTOBOOK_LINKED: reason=${insertResult.reason}")
                                }
                                else -> {
                                    debugPopup("AUTOBOOK_DROP: reason=${insertResult.reason}")
                                }
                            }
                        }
                    }
                }.onFailure { error ->
                    AutoBookTelemetry.track(
                        applicationContext,
                        event = "listener_error",
                        packageName = packageName,
                        reason = error.message ?: error.javaClass.simpleName
                    )
                    Log.e("AutoBookDebug", "notification processing failed", error)
                }
            }
        }.onFailure { error ->
            AutoBookTelemetry.track(
                applicationContext,
                event = "listener_error",
                packageName = sbn?.packageName,
                reason = error.message ?: error.javaClass.simpleName
            )
            Log.e("AutoBookDebug", "onNotificationPosted failed", error)
        }
    }

    override fun onDestroy() {
        AutoBookTelemetry.track(applicationContext, event = "listener_destroy")
        debugPopup("AUTOBK service destroyed")
        scope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        AutoBookTelemetry.track(applicationContext, event = "listener_connected")
        debugPopup("Listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        AutoBookTelemetry.track(applicationContext, event = "listener_disconnected")
        debugPopup("Listener disconnected, rebind")
        runCatching {
            requestRebind(ComponentName(applicationContext, PaymentNotificationListener::class.java))
        }
    }

    private fun isWhitelistedPaymentNotification(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (packageName == applicationContext.packageName) return false
        if (allowedPackages.contains(packageName)) return true
        return PaymentNotificationParser.isSupportedPackage(packageName)
    }

    private fun debugPopup(msg: String) {
        Log.d("AutoBookDebug", msg)
    }

    private fun showAutoDetectedToast() {
        val now = System.currentTimeMillis()
        if (now - lastAutoHintMs < 1500L) return
        lastAutoHintMs = now
        mainHandler.post {
            runCatching {
                Toast.makeText(applicationContext, "自动记账已识别，已放入待确认", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun buildMergedNotificationText(extras: android.os.Bundle?): String {
        if (extras == null) return ""
        val pieces = buildList<String> {
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
            extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
            extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { line ->
                line?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }
        return pieces.joinToString(" ")
    }
}
