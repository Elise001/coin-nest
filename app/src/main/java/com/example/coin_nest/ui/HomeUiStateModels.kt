package com.example.coin_nest.ui

import com.example.coin_nest.data.db.CategoryBudgetEntity
import com.example.coin_nest.data.db.TransactionEntity
import com.example.coin_nest.data.model.BalanceSummary
import com.example.coin_nest.data.model.CategoryItem
import java.time.YearMonth

internal const val DEV_REWARD_ACTIVE_DAYS = 999

data class HomeUiState(
    val daily: BalanceSummary = BalanceSummary(),
    val monthly: BalanceSummary = BalanceSummary(),
    val yearly: BalanceSummary = BalanceSummary(),
    val previousYearSummary: BalanceSummary = BalanceSummary(),
    val selectedMonth: YearMonth = YearMonth.now(),
    val selectedMonthSummary: BalanceSummary = BalanceSummary(),
    val previousMonthSummary: BalanceSummary = BalanceSummary(),
    val monthBudgetCents: Long? = null,
    val categories: List<CategoryItem> = emptyList(),
    val selectedMonthCategoryBudgets: List<CategoryBudgetEntity> = emptyList(),
    val todayTransactions: List<TransactionEntity> = emptyList(),
    val monthTransactions: List<TransactionEntity> = emptyList(),
    val yearTransactions: List<TransactionEntity> = emptyList(),
    val monthTrendPoints: List<TrendPoint> = emptyList(),
    val yearTrendPoints: List<TrendPoint> = emptyList(),
    val monthCategoryShare: List<CategoryShare> = emptyList(),
    val yearCategoryShare: List<CategoryShare> = emptyList(),
    val selectedYearSummary: BalanceSummary = BalanceSummary(),
    val monthHasMore: Boolean = false,
    val yearHasMore: Boolean = false,
    val pendingAutoTransactions: List<TransactionEntity> = emptyList(),
    val smartLearningStatus: SmartLearningStatus = SmartLearningStatus(),
    val activeBookkeepingDays: Int = DEV_REWARD_ACTIVE_DAYS
)

data class SmartLearningKeyword(
    val keyword: String,
    val hitCount: Int,
    val categoryPath: String
)

data class SmartLearningStatus(
    val totalRules: Int = 0,
    val highConfidenceRules: Int = 0,
    val topKeywords: List<SmartLearningKeyword> = emptyList(),
    val recent7DayHits: List<Int> = List(7) { 0 }
)

internal data class SummaryAndCategory(
    val daily: BalanceSummary,
    val monthly: BalanceSummary,
    val yearly: BalanceSummary,
    val previousYearly: BalanceSummary,
    val monthBudgetCents: Long?,
    val categories: List<CategoryItem>
)

internal data class SelectedMonthData(
    val month: YearMonth,
    val summary: BalanceSummary,
    val previousMonthSummary: BalanceSummary,
    val transactions: List<TransactionEntity>,
    val categoryBudgets: List<CategoryBudgetEntity>
)

internal data class InsightAggregation(
    val monthTrend: List<TrendPoint>,
    val yearTrend: List<TrendPoint>,
    val monthShare: List<CategoryShare>,
    val yearShare: List<CategoryShare>,
    val selectedYearSummary: BalanceSummary,
    val monthTotalCount: Int,
    val yearTotalCount: Int
)

internal data class TrendAndShareAggregation(
    val monthTrend: List<TrendPoint>,
    val yearTrend: List<TrendPoint>,
    val monthShare: List<CategoryShare>,
    val yearShare: List<CategoryShare>
)

internal data class BaseUiSlice(
    val summary: SummaryAndCategory,
    val todayTxs: List<TransactionEntity>,
    val yearTxs: List<TransactionEntity>,
    val selectedMonthData: SelectedMonthData
)
