package com.example.coin_nest.autobook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.coin_nest.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PaymentActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val txId = intent.getLongExtra(PaymentActionNotifier.EXTRA_TX_ID, -1L)
        if (txId <= 0L) return
        ServiceLocator.init(context.applicationContext)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    PaymentActionNotifier.ACTION_CONFIRM -> {
                        ServiceLocator.repository().confirmPendingTransaction(txId)
                    }

                    PaymentActionNotifier.ACTION_CANCEL -> {
                        ServiceLocator.repository().ignorePendingTransaction(txId)
                    }
                }
                NotificationManagerCompat.from(context).cancel(txId.toInt())
            } finally {
                pendingResult.finish()
            }
        }
    }
}
