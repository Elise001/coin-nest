package com.example.coin_nest.autobook

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.example.coin_nest.di.ServiceLocator

class PaymentNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        scope.launch {
            val insertedId = ServiceLocator.repository().addAutoExpense(
                amountCents = parsed.amountCents,
                source = parsed.source,
                note = parsed.note,
                fingerprint = parsed.fingerprint
            )
            if (insertedId != null) {
                PaymentActionNotifier.notifyPendingPayment(
                    context = applicationContext,
                    txId = insertedId,
                    amountCents = parsed.amountCents,
                    source = parsed.source,
                    note = parsed.note
                )
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
