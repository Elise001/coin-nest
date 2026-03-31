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
    const val CHANNEL_ID = "payment_auto_book_v2"
    private const val CHANNEL_NAME = "\u81ea\u52a8\u8bb0\u8d26\u786e\u8ba4"
    const val ACTION_CONFIRM = "com.example.coin_nest.action.CONFIRM_PENDING"
    const val ACTION_CANCEL = "com.example.coin_nest.action.CANCEL_PENDING"
    const val EXTRA_TX_ID = "extra_tx_id"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        )
        manager.createNotificationChannel(channel)
    }

    fun notifyPendingPayment(
        context: Context,
        txId: Long,
        amountCents: Long,
        source: String,
        note: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        val openIntent = Intent(context, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            context,
            txId.toInt(),
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val confirmIntent = Intent(context, PaymentActionReceiver::class.java).apply {
            action = ACTION_CONFIRM
            putExtra(EXTRA_TX_ID, txId)
        }
        val confirmPendingIntent = PendingIntent.getBroadcast(
            context,
            (txId * 10 + 1).toInt(),
            confirmIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val cancelIntent = Intent(context, PaymentActionReceiver::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_TX_ID, txId)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            (txId * 10 + 2).toInt(),
            cancelIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val content = "$source  ${MoneyFormat.fromCents(amountCents)}"
        val body = note.take(80)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("\u68c0\u6d4b\u5230\u652f\u4ed8\uff0c\u8bf7\u4e0b\u62c9\u5c55\u5f00\u786e\u8ba4")
            .setContentText("$content  \u00b7  \u4e0b\u62c9\u5c55\u5f00")
            .setSubText("\u4e0b\u62c9\u5c55\u5f00\u540e\u53ef\u786e\u8ba4/\u53d6\u6d88")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$content\n$body"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setContentIntent(openPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "\u786e\u8ba4", confirmPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "\u53d6\u6d88", cancelPendingIntent)
            .build()
        NotificationManagerCompat.from(context).notify(txId.toInt(), notification)
    }
}
