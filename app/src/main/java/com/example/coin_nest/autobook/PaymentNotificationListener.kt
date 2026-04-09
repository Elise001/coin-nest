package com.example.coin_nest.autobook

import android.content.ComponentName
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
    private var lastPopupMs: Long = 0L

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
        debugPopup("AUTOBK service started")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn?.notification ?: return
        val packageName = sbn.packageName.orEmpty()
        if (!isWhitelistedPaymentNotification(packageName)) return

        val extras = n.extras
        val title = extras?.getString("android.title")
        val text = extras?.getCharSequence("android.text")?.toString()
        val summaryTag = if (n.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) "[SUMMARY]" else ""
        debugPopup("PAYMENT_NOTIFY$summaryTag: $packageName ${title.orEmpty().take(12)}")

        val parsedResult = PaymentNotificationParser.parseWithDebug(packageName, title, text, sbn.postTime)
        val parsed = parsedResult.payment
        if (parsed == null) {
            debugPopup("IGNORE_NOTIFY($packageName): ${parsedResult.reason}")
            return
        }

        scope.launch {
            val insertResult = ServiceLocator.repository().addAutoTransaction(
                amountCents = parsed.amountCents,
                type = parsed.type,
                source = parsed.source,
                note = parsed.note,
                fingerprint = parsed.fingerprint,
                occurredAtEpochMs = parsed.occurredAtEpochMs,
                parent = parsed.parentCategory,
                child = parsed.childCategory
            )
            when {
                insertResult.insertedId != null && insertResult.shouldNotify -> {
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

    override fun onDestroy() {
        debugPopup("AUTOBK service destroyed")
        scope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        debugPopup("Listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
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
        val now = System.currentTimeMillis()
        if (now - lastPopupMs < 500) return
        lastPopupMs = now
        mainHandler.post {
            runCatching {
                Toast.makeText(applicationContext, msg.take(80), Toast.LENGTH_LONG).show()
            }
        }
    }
}
