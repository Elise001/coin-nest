package com.example.coin_nest

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.coin_nest.budget.BudgetCheckWorker
import com.example.coin_nest.di.ServiceLocator
import java.util.concurrent.TimeUnit

class CoinNestApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        scheduleBudgetWorker()
    }

    private fun scheduleBudgetWorker() {
        val request = PeriodicWorkRequestBuilder<BudgetCheckWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            BudgetCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
