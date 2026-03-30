package com.example.coin_nest.autobook

import android.content.Context
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
    private val prefs by lazy { applicationContext.getSharedPreferences("auto_book_seen", Context.MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn?.notification ?: return
        if (n.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras
        val title = extras?.getString("android.title")
        val text = extras?.getCharSequence("android.text")?.toString()
        val parsed = PaymentNotificationParser.parse(sbn.packageName, title, text, sbn.postTime) ?: return
        if (isDuplicate(parsed.fingerprint)) return
        scope.launch {
            ServiceLocator.repository().addAutoExpense(
                amountCents = parsed.amountCents,
                source = parsed.source,
                note = parsed.note
            )
            markSeen(parsed.fingerprint)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun isDuplicate(fingerprint: String): Boolean {
        val set = prefs.getStringSet(KEY_SEEN, emptySet()).orEmpty()
        return set.contains(fingerprint)
    }

    private fun markSeen(fingerprint: String) {
        val current = prefs.getStringSet(KEY_SEEN, emptySet()).orEmpty().toMutableSet()
        current.add(fingerprint)
        while (current.size > 300) {
            current.remove(current.first())
        }
        prefs.edit().putStringSet(KEY_SEEN, current).apply()
    }

    companion object {
        private const val KEY_SEEN = "seen_fingerprints"
    }
}
