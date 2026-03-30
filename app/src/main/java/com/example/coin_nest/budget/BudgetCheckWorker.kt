package com.example.coin_nest.budget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.coin_nest.di.ServiceLocator
import com.example.coin_nest.util.DateRangeUtils

class BudgetCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        ServiceLocator.init(applicationContext)
        BudgetNotifier.ensureChannel(applicationContext)
        val repository = ServiceLocator.repository()
        val monthKey = DateRangeUtils.currentMonthKey()
        val monthRange = DateRangeUtils.monthRange()
        val (expense, budget) = repository.currentMonthBudgetUsage(
            monthKey = monthKey,
            startInclusive = monthRange.first,
            endExclusive = monthRange.last + 1
        )
        if (budget != null && budget > 0) {
            val ratio = expense.toDouble() / budget.toDouble()
            if (ratio >= 1.0) {
                BudgetNotifier.notifyBudgetStatus(applicationContext, expense, budget, exceeded = true)
            } else if (ratio >= 0.8) {
                BudgetNotifier.notifyBudgetStatus(applicationContext, expense, budget, exceeded = false)
            }
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "budget-check-work"
    }
}

