package com.example.coin_nest.autobook

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.coin_nest.MainActivity
import com.example.coin_nest.R
import com.example.coin_nest.util.MoneyFormat

object PaymentActionNotifier {
    const val CHANNEL_ID = "payment_auto_book_v3_quiet"
    private const val CHANNEL_NAME = "\u81ea\u52a8\u8bb0\u8d26\u786e\u8ba4"
    private const val PREF_NAME = "payment_pending_notice"
    private const val KEY_PENDING_NOTICE_COUNT = "pending_notice_count"
    private const val KEY_LAST_NOTICE_MS = "last_notice_ms"
    private const val KEY_FIRST_PENDING_MS = "first_pending_ms"
    const val AGGREGATE_NOTIFICATION_ID = 20260413
    private const val MIN_PENDING_FOR_NOTICE = 3
    private const val NOTICE_INTERVAL_MS = 30 * 60_000L
    const val ACTION_CONFIRM = "com.example.coin_nest.action.CONFIRM_PENDING"
    const val ACTION_CANCEL = "com.example.coin_nest.action.CANCEL_PENDING"
    const val EXTRA_TX_ID = "extra_tx_id"
    const val EXTRA_OPEN_TAB = "extra_open_tab"
    const val TAB_RECORD = 1

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "\u81ea\u52a8\u8bb0\u8d26\u5f85\u786e\u8ba4\u7684\u4f4e\u6253\u6270\u6c47\u603b\u63d0\u9192"
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun notifyPendingPayment(
        context: Context,
        txId: Long,
        amountCents: Long,
        type: String,
        source: String,
        note: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val aggregate = recordPendingNoticeEvent(context)
        if (!aggregate.shouldNotify) return

        ensureChannel(context)
        val openIntent = Intent(context, MainActivity::class.java)
            .apply {
                putExtra(EXTRA_OPEN_TAB, TAB_RECORD)
                putExtra(EXTRA_TX_ID, txId)
            }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            txId.toInt(),
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val amountPrefix = if (type.equals("INCOME", ignoreCase = true)) "+" else "-"
        val content = "$source  $amountPrefix${MoneyFormat.fromCents(amountCents)}"
        val body = note.take(80)
        val title = if (aggregate.pendingCount <= 1) {
            "\u6709 1 \u7b14\u81ea\u52a8\u8bb0\u8d26\u5f85\u786e\u8ba4"
        } else {
            "\u6709 ${aggregate.pendingCount} \u7b14\u81ea\u52a8\u8bb0\u8d26\u5f85\u786e\u8ba4"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("\u6700\u8fd1\u4e00\u7b14\uff1a$content")
            .setSubText("\u70b9\u51fb\u8fdb\u5165\u5f85\u786e\u8ba4\u961f\u5217")
            .setStyle(NotificationCompat.BigTextStyle().bigText("\u6700\u8fd1\u4e00\u7b14\uff1a$content\n$body\n\n\u70b9\u51fb\u540e\u53ef\u5728 App \u5185\u6279\u91cf\u786e\u8ba4\u6216\u53d6\u6d88\u3002"))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setContentIntent(openPendingIntent)
            .build()
        NotificationManagerCompat.from(context).notify(AGGREGATE_NOTIFICATION_ID, notification)
    }

    fun clearPendingAggregateNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(AGGREGATE_NOTIFICATION_ID)
        resetPendingNoticeState(context)
    }

    fun resetPendingNoticeState(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_PENDING_NOTICE_COUNT)
            .remove(KEY_FIRST_PENDING_MS)
            .apply()
    }

    private fun recordPendingNoticeEvent(context: Context): PendingNoticeAggregate {
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val firstPendingMs = prefs.getLong(KEY_FIRST_PENDING_MS, 0L).takeIf { it > 0L } ?: now
        val pendingCount = prefs.getInt(KEY_PENDING_NOTICE_COUNT, 0) + 1
        val lastNoticeMs = prefs.getLong(KEY_LAST_NOTICE_MS, 0L)
        val enoughPending = pendingCount >= MIN_PENDING_FOR_NOTICE
        val intervalReached = lastNoticeMs > 0L && now - lastNoticeMs >= NOTICE_INTERVAL_MS
        val hasWaited = now - firstPendingMs >= NOTICE_INTERVAL_MS
        val shouldNotify = enoughPending && (lastNoticeMs == 0L || intervalReached || hasWaited)
        prefs.edit().apply {
            putInt(KEY_PENDING_NOTICE_COUNT, pendingCount)
            putLong(KEY_FIRST_PENDING_MS, firstPendingMs)
            if (shouldNotify) {
                putLong(KEY_LAST_NOTICE_MS, now)
                putInt(KEY_PENDING_NOTICE_COUNT, 0)
                putLong(KEY_FIRST_PENDING_MS, now)
            }
        }.apply()
        return PendingNoticeAggregate(
            pendingCount = pendingCount,
            shouldNotify = shouldNotify
        )
    }
}

private data class PendingNoticeAggregate(
    val pendingCount: Int,
    val shouldNotify: Boolean
)
