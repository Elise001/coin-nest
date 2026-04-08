package com.example.coin_nest.autobook

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.example.coin_nest.di.ServiceLocator
import java.util.concurrent.ConcurrentHashMap

class PaymentNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recentFingerprintCache = ConcurrentHashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(applicationContext)
        PaymentActionNotifier.ensureChannel(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn?.notification ?: return
        if (n.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras
        val title = extras?.getString("android.title")
        val text = extras?.getCharSequence("android.text")?.toString()
        val parsed = PaymentNotificationParser.parse(sbn.packageName, title, text, sbn.postTime) ?: return
        if (isLikelyDuplicate(parsed.fingerprint, sbn.postTime)) return
        scope.launch {
            val insertedId = ServiceLocator.repository().addAutoTransaction(
                amountCents = parsed.amountCents,
                type = parsed.type,
                source = parsed.source,
                note = parsed.note,
                fingerprint = parsed.fingerprint,
                occurredAtEpochMs = parsed.occurredAtEpochMs,
                parent = parsed.parentCategory,
                child = parsed.childCategory
            )
            if (insertedId != null) {
                PaymentActionNotifier.notifyPendingPayment(
                    context = applicationContext,
                    txId = insertedId,
                    amountCents = parsed.amountCents,
                    type = parsed.type.name,
                    source = parsed.source,
                    note = parsed.note
                )
            }
        }
    }

    override fun onDestroy() {
        recentFingerprintCache.clear()
        scope.cancel()
        super.onDestroy()
    }

    private fun isLikelyDuplicate(fingerprint: String, now: Long): Boolean {
        val cachedAt = recentFingerprintCache[fingerprint]
        if (cachedAt != null && now - cachedAt in 0..90_000) {
            return true
        }
        recentFingerprintCache[fingerprint] = now
        if (recentFingerprintCache.size > 120) {
            val threshold = now - 10 * 60_000
            recentFingerprintCache.entries.removeIf { it.value < threshold }
        }
        return false
    }
}
