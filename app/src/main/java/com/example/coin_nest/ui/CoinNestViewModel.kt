package com.example.coin_nest.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.coin_nest.data.CoinNestRepository
import com.example.coin_nest.data.db.CategoryBudgetEntity
import com.example.coin_nest.data.db.TransactionEntity
import com.example.coin_nest.data.model.BalanceSummary
import com.example.coin_nest.data.model.CategoryItem
import com.example.coin_nest.data.model.TransactionInput
import com.example.coin_nest.data.model.TransactionType
import com.example.coin_nest.util.DateRangeUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    val retentionFeedback: RetentionFeedbackState = RetentionFeedbackState()
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

data class RetentionFeedbackState(
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val activeDaysInSelectedMonth: Int = 0,
    val unlockedBadges: List<String> = emptyList(),
    val celebrationMessage: String? = null
)

private data class SummaryAndCategory(
    val daily: BalanceSummary,
    val monthly: BalanceSummary,
    val yearly: BalanceSummary,
    val previousYearly: BalanceSummary,
    val monthBudgetCents: Long?,
    val categories: List<CategoryItem>
)

private data class SelectedMonthData(
    val month: YearMonth,
    val summary: BalanceSummary,
    val previousMonthSummary: BalanceSummary,
    val transactions: List<TransactionEntity>,
    val categoryBudgets: List<CategoryBudgetEntity>
)

private data class InsightAggregation(
    val monthTrend: List<TrendPoint>,
    val yearTrend: List<TrendPoint>,
    val monthShare: List<CategoryShare>,
    val yearShare: List<CategoryShare>,
    val selectedYearSummary: BalanceSummary,
    val monthTotalCount: Int,
    val yearTotalCount: Int
)

private data class TrendAndShareAggregation(
    val monthTrend: List<TrendPoint>,
    val yearTrend: List<TrendPoint>,
    val monthShare: List<CategoryShare>,
    val yearShare: List<CategoryShare>
)

private data class BaseUiSlice(
    val summary: SummaryAndCategory,
    val todayTxs: List<TransactionEntity>,
    val yearTxs: List<TransactionEntity>,
    val selectedMonthData: SelectedMonthData
)

@OptIn(ExperimentalCoroutinesApi::class)
class CoinNestViewModel(
    private val repository: CoinNestRepository
) : ViewModel() {
    private companion object {
        const val MONTH_PAGE_SIZE = 300
        const val YEAR_PAGE_SIZE = 500
    }

    private val zone: ZoneId = ZoneId.systemDefault()
    private val dayRange = DateRangeUtils.todayRange()
    private val monthRange = DateRangeUtils.monthRange()
    private val yearRange = DateRangeUtils.yearRange()
    private val previousYearRange = run {
        val today = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate()
        val first = LocalDate.of(today.year - 1, 1, 1)
        val start = first.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = first.plusYears(1).atStartOfDay(zone).toInstant().toEpochMilli()
        start until end
    }
    private val monthKey = DateRangeUtils.currentMonthKey()
    private val selectedMonthFlow = MutableStateFlow(YearMonth.now())
    private val monthPageFlow = MutableStateFlow(1)
    private val yearPageFlow = MutableStateFlow(1)

    private val summaryBaseFlow = combine(
        repository.observeSummary(dayRange.first, dayRange.last + 1),
        repository.observeSummary(monthRange.first, monthRange.last + 1),
        repository.observeSummary(yearRange.first, yearRange.last + 1),
        repository.observeSummary(previousYearRange.first, previousYearRange.last + 1),
        repository.observeMonthBudget(monthKey)
    ) { daily, monthly, yearly, previousYearly, budget ->
        arrayOf(daily, monthly, yearly, previousYearly, budget)
    }

    private val summaryAndCategoryBaseFlow = combine(
        summaryBaseFlow,
        repository.observeCategories()
    ) { base, categories ->
        val daily = base[0] as BalanceSummary
        val monthly = base[1] as BalanceSummary
        val yearly = base[2] as BalanceSummary
        val previousYearly = base[3] as BalanceSummary
        val budget = base[4] as com.example.coin_nest.data.db.MonthlyBudgetEntity?
        SummaryAndCategory(
            daily = daily,
            monthly = monthly,
            yearly = yearly,
            previousYearly = previousYearly,
            monthBudgetCents = budget?.limitCents,
            categories = categories
        )
    }
    private val summaryAndCategoryFlow = summaryAndCategoryBaseFlow
    private val smartRuleFlow = repository.observeSmartCategoryRules(limit = 300)
    private val recentConfirmedTransactionsFlow = repository.observeRecentTransactions(limit = 5000)

    private val selectedMonthSummaryFlow = selectedMonthFlow.flatMapLatest { ym ->
        val range = DateRangeUtils.monthRange(ym)
        repository.observeSummary(range.first, range.last + 1)
    }

    private val selectedMonthTransactionsFlow = combine(selectedMonthFlow, monthPageFlow) { ym, page ->
        ym to page
    }.flatMapLatest { (ym, page) ->
        val range = DateRangeUtils.monthRange(ym)
        repository.observeTransactionsInRange(
            startInclusive = range.first,
            endExclusive = range.last + 1,
            limit = page * MONTH_PAGE_SIZE
        )
    }

    private val previousMonthSummaryFlow = selectedMonthFlow.flatMapLatest { ym ->
        val range = DateRangeUtils.monthRange(ym.minusMonths(1))
        repository.observeSummary(range.first, range.last + 1)
    }

    private val selectedMonthCategoryBudgetsFlow = selectedMonthFlow.flatMapLatest { ym ->
        repository.observeCategoryBudgets(DateRangeUtils.monthKey(ym))
    }
    private val selectedYearTransactionsFlow = combine(selectedMonthFlow, yearPageFlow) { ym, page ->
        ym to page
    }.flatMapLatest { (ym, page) ->
        val start = LocalDate.of(ym.year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(ym.year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        repository.observeTransactionsInRange(start, end, limit = page * YEAR_PAGE_SIZE)
    }

    private val selectedYearSummaryFlow = selectedMonthFlow.flatMapLatest { ym ->
        val start = LocalDate.of(ym.year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(ym.year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        repository.observeSummary(start, end)
    }

    private val selectedMonthTotalCountFlow = selectedMonthFlow.flatMapLatest { ym ->
        val range = DateRangeUtils.monthRange(ym)
        repository.observeConfirmedTransactionCountInRange(range.first, range.last + 1)
    }

    private val selectedYearTotalCountFlow = selectedMonthFlow.flatMapLatest { ym ->
        val start = LocalDate.of(ym.year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(ym.year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        repository.observeConfirmedTransactionCountInRange(start, end)
    }

    private val selectedMonthTrendFlow = selectedMonthFlow.flatMapLatest { ym ->
        val range = DateRangeUtils.monthRange(ym)
        repository.observeExpenseByDayInRange(range.first, range.last + 1).map { rows ->
            val byDay = rows.associate { it.bucket to it.amountCents }
            val allDays = (1..ym.lengthOfMonth()).toList()
            val maxBars = 15
            val step = kotlin.math.ceil(allDays.size / maxBars.toDouble()).toInt().coerceAtLeast(1)
            allDays.chunked(step).map { chunk ->
                TrendPoint(
                    label = chunk.first().toString(),
                    expenseCents = chunk.sumOf { day -> byDay[day] ?: 0L }
                )
            }
        }
    }

    private val selectedYearTrendFlow = selectedMonthFlow.flatMapLatest { ym ->
        val start = LocalDate.of(ym.year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(ym.year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        repository.observeExpenseByMonthInRange(start, end).map { rows ->
            val byMonth = rows.associate { it.bucket to it.amountCents }
            (1..12).map { month ->
                TrendPoint(label = "$month", expenseCents = byMonth[month] ?: 0L)
            }
        }
    }

    private val selectedMonthCategoryShareFlow = selectedMonthFlow.flatMapLatest { ym ->
        val range = DateRangeUtils.monthRange(ym)
        repository.observeCategoryExpenseInRange(range.first, range.last + 1).map { rows ->
            val total = rows.sumOf { it.amountCents }.coerceAtLeast(1L)
            rows.map {
                CategoryShare(
                    name = it.category,
                    amountCents = it.amountCents,
                    ratio = it.amountCents.toFloat() / total.toFloat()
                )
            }
        }
    }

    private val selectedYearCategoryShareFlow = selectedMonthFlow.flatMapLatest { ym ->
        val start = LocalDate.of(ym.year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(ym.year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        repository.observeCategoryExpenseInRange(start, end).map { rows ->
            val total = rows.sumOf { it.amountCents }.coerceAtLeast(1L)
            rows.map {
                CategoryShare(
                    name = it.category,
                    amountCents = it.amountCents,
                    ratio = it.amountCents.toFloat() / total.toFloat()
                )
            }
        }
    }

    private val trendAndShareAggregationFlow = combine(
        selectedMonthTrendFlow,
        selectedYearTrendFlow,
        selectedMonthCategoryShareFlow,
        selectedYearCategoryShareFlow
    ) { monthTrend, yearTrend, monthShare, yearShare ->
        TrendAndShareAggregation(
            monthTrend = monthTrend,
            yearTrend = yearTrend,
            monthShare = monthShare,
            yearShare = yearShare
        )
    }

    private val insightAggregationFlow = combine(
        trendAndShareAggregationFlow,
        selectedYearSummaryFlow,
        selectedMonthTotalCountFlow,
        selectedYearTotalCountFlow
    ) { trendAndShare, selectedYearSummary, monthTotalCount, yearTotalCount ->
        InsightAggregation(
            monthTrend = trendAndShare.monthTrend,
            yearTrend = trendAndShare.yearTrend,
            monthShare = trendAndShare.monthShare,
            yearShare = trendAndShare.yearShare,
            selectedYearSummary = selectedYearSummary,
            monthTotalCount = monthTotalCount,
            yearTotalCount = yearTotalCount
        )
    }

    private val selectedMonthDataFlow = combine(
        selectedMonthFlow,
        selectedMonthSummaryFlow,
        previousMonthSummaryFlow,
        selectedMonthTransactionsFlow,
        selectedMonthCategoryBudgetsFlow
    ) { month, summary, previousSummary, transactions, categoryBudgets ->
        SelectedMonthData(
            month = month,
            summary = summary,
            previousMonthSummary = previousSummary,
            transactions = transactions,
            categoryBudgets = categoryBudgets
        )
    }

    private val baseUiSliceFlow = combine(
        summaryAndCategoryFlow,
        repository.observeTransactionsInRange(dayRange.first, dayRange.last + 1, limit = 200),
        selectedYearTransactionsFlow,
        selectedMonthDataFlow
    ) { summary, todayTxs, yearTxs, selectedMonthData ->
        BaseUiSlice(
            summary = summary,
            todayTxs = todayTxs,
            yearTxs = yearTxs,
            selectedMonthData = selectedMonthData
        )
    }

    private val baseUiStateFlow = combine(
        baseUiSliceFlow,
        insightAggregationFlow,
        repository.observePendingAutoTransactions(limit = 100)
    ) { baseSlice, insightAgg, pendingTxs ->
        HomeUiState(
            daily = baseSlice.summary.daily,
            monthly = baseSlice.summary.monthly,
            yearly = baseSlice.summary.yearly,
            previousYearSummary = baseSlice.summary.previousYearly,
            selectedMonth = baseSlice.selectedMonthData.month,
            selectedMonthSummary = baseSlice.selectedMonthData.summary,
            previousMonthSummary = baseSlice.selectedMonthData.previousMonthSummary,
            monthBudgetCents = baseSlice.summary.monthBudgetCents,
            categories = baseSlice.summary.categories,
            selectedMonthCategoryBudgets = baseSlice.selectedMonthData.categoryBudgets,
            todayTransactions = baseSlice.todayTxs,
            monthTransactions = baseSlice.selectedMonthData.transactions,
            yearTransactions = baseSlice.yearTxs,
            monthTrendPoints = insightAgg.monthTrend,
            yearTrendPoints = insightAgg.yearTrend,
            monthCategoryShare = insightAgg.monthShare,
            yearCategoryShare = insightAgg.yearShare,
            selectedYearSummary = insightAgg.selectedYearSummary,
            monthHasMore = baseSlice.selectedMonthData.transactions.size < insightAgg.monthTotalCount,
            yearHasMore = baseSlice.yearTxs.size < insightAgg.yearTotalCount,
            pendingAutoTransactions = pendingTxs
        )
    }

    private val retentionFeedbackFlow = combine(
        recentConfirmedTransactionsFlow,
        selectedMonthDataFlow
    ) { recentTxs, selectedMonthData ->
        buildRetentionFeedback(
            recentTransactions = recentTxs,
            selectedMonthTransactions = selectedMonthData.transactions
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        baseUiStateFlow,
        smartRuleFlow,
        retentionFeedbackFlow
    ) { base, smartRules, retentionFeedback ->
        base.copy(
            smartLearningStatus = buildSmartLearningStatus(smartRules),
            retentionFeedback = retentionFeedback
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch {
            repository.ensureDefaultCategories()
        }
    }

    fun addTransaction(
        amountYuan: String,
        isIncome: Boolean,
        parentCategory: String,
        childCategory: String,
        note: String,
        occurredAtEpochMs: Long = System.currentTimeMillis()
    ) {
        val amount = amountYuan.toDoubleOrNull() ?: return
        if (amount <= 0.0) return
        viewModelScope.launch {
            repository.addTransaction(
                TransactionInput(
                    amountCents = (amount * 100).toLong(),
                    type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
                    parentCategory = parentCategory.ifBlank { if (isIncome) "\u6536\u5165" else "\u751f\u6d3b" },
                    childCategory = childCategory.ifBlank { if (isIncome) "\u5176\u4ed6" else "\u672a\u5206\u7c7b" },
                    source = "MANUAL",
                    note = note,
                    occurredAtEpochMs = occurredAtEpochMs
                )
            )
        }
    }

    fun addCategory(parent: String, child: String) {
        if (parent.isBlank() || child.isBlank()) return
        viewModelScope.launch { repository.addCategory(parent, child) }
    }

    fun setCurrentMonthBudget(amountYuan: String) {
        val amount = amountYuan.toDoubleOrNull() ?: return
        if (amount <= 0.0) return
        viewModelScope.launch {
            repository.upsertMonthBudget(monthKey, (amount * 100).toLong())
        }
    }

    fun setSelectedMonthCategoryBudget(parentCategory: String, childCategory: String, amountYuan: String) {
        val amount = amountYuan.toDoubleOrNull() ?: return
        if (amount <= 0.0) return
        val targetMonthKey = DateRangeUtils.monthKey(selectedMonthFlow.value)
        viewModelScope.launch {
            repository.upsertCategoryBudget(
                monthKey = targetMonthKey,
                parentCategory = parentCategory,
                childCategory = childCategory,
                limitCents = (amount * 100).toLong()
            )
        }
    }


    fun selectMonth(month: YearMonth) {
        selectedMonthFlow.value = month
        monthPageFlow.value = 1
        yearPageFlow.value = 1
    }

    fun loadMoreSelectedMonthTransactions() {
        monthPageFlow.value = monthPageFlow.value + 1
    }

    fun loadMoreSelectedYearTransactions() {
        yearPageFlow.value = yearPageFlow.value + 1
    }

    fun confirmPendingAutoTransaction(id: Long) {
        if (id <= 0L) return
        viewModelScope.launch {
            repository.confirmPendingTransaction(id)
        }
    }

    fun ignorePendingAutoTransaction(id: Long) {
        if (id <= 0L) return
        viewModelScope.launch {
            repository.ignorePendingTransaction(id)
        }
    }

    fun updateTransactionCategory(id: Long, parentCategory: String, childCategory: String) {
        if (id <= 0L) return
        viewModelScope.launch {
            repository.updateTransactionCategory(id, parentCategory, childCategory)
        }
    }

    fun deleteTransaction(id: Long) {
        if (id <= 0L) return
        viewModelScope.launch {
            repository.deleteTransaction(id)
        }
    }

    fun exportBackupJson(onResult: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.exportBackupJson() }
                .onSuccess(onResult)
                .onFailure { onError(it.message ?: "瀵煎嚭澶辫触") }
        }
    }

    fun importBackupJson(
        json: String,
        replaceExisting: Boolean,
        onResult: (Int, Int) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching { repository.importBackupJson(json, replaceExisting) }
                .onSuccess { (txCount, catCount) -> onResult(txCount, catCount) }
                .onFailure { onError(it.message ?: "瀵煎叆澶辫触") }
        }
    }

    fun clearSmartLearningRules() {
        viewModelScope.launch {
            repository.clearSmartCategoryRules()
        }
    }

    private fun buildSmartLearningStatus(
        rules: List<com.example.coin_nest.data.db.SmartCategoryRuleEntity>
    ): SmartLearningStatus {
        if (rules.isEmpty()) return SmartLearningStatus()
        val today = LocalDate.now(zone)
        val recent7DayMap = rules
            .groupBy { Instant.ofEpochMilli(it.updatedAtEpochMs).atZone(zone).toLocalDate() }
            .mapValues { (_, groupedRules) -> groupedRules.sumOf { it.hitCount.coerceAtLeast(1) } }
        val recent7DayHits = (6 downTo 0).map { diff ->
            recent7DayMap[today.minusDays(diff.toLong())] ?: 0
        }
        val topKeywords = rules
            .groupBy { it.keyword }
            .map { (keyword, groupedRules) ->
                val totalHit = groupedRules.sumOf { it.hitCount }
                val topRule = groupedRules.maxByOrNull { it.hitCount } ?: groupedRules.first()
                SmartLearningKeyword(
                    keyword = keyword,
                    hitCount = totalHit,
                    categoryPath = "${topRule.parentCategory}/${topRule.childCategory}"
                )
            }
            .sortedByDescending { it.hitCount }
            .take(5)
        return SmartLearningStatus(
            totalRules = rules.size,
            highConfidenceRules = rules.count { it.hitCount >= 3 },
            topKeywords = topKeywords,
            recent7DayHits = recent7DayHits
        )
    }

    private fun buildRetentionFeedback(
        recentTransactions: List<TransactionEntity>,
        selectedMonthTransactions: List<TransactionEntity>
    ): RetentionFeedbackState {
        val allDates = recentTransactions
            .map { Instant.ofEpochMilli(it.occurredAtEpochMs).atZone(zone).toLocalDate() }
            .distinct()
            .sorted()
        if (allDates.isEmpty()) {
            return RetentionFeedbackState()
        }

        val today = LocalDate.now(zone)
        val dateSet = allDates.toSet()
        var currentStreak = 0
        var cursor = today
        while (dateSet.contains(cursor)) {
            currentStreak++
            cursor = cursor.minusDays(1)
        }

        var longestStreak = 0
        var streak = 0
        var previousDate: LocalDate? = null
        allDates.forEach { date ->
            if (previousDate == null || date == previousDate!!.plusDays(1)) {
                streak += 1
            } else {
                streak = 1
            }
            if (streak > longestStreak) longestStreak = streak
            previousDate = date
        }

        val activeDaysInSelectedMonth = selectedMonthTransactions
            .map { Instant.ofEpochMilli(it.occurredAtEpochMs).atZone(zone).toLocalDate() }
            .distinct()
            .size

        val badgeThresholds = listOf(
            3 to "连续3天记录",
            7 to "连续7天记录",
            14 to "连续14天记录",
            30 to "连续30天记录"
        )
        val unlockedBadges = badgeThresholds
            .filter { (threshold, _) -> longestStreak >= threshold }
            .map { (_, name) -> name }

        val celebrationMessage = badgeThresholds
            .firstOrNull { (threshold, _) -> currentStreak == threshold }
            ?.second
            ?.let { "达成成就：$it，保持节奏很棒。" }

        return RetentionFeedbackState(
            currentStreakDays = currentStreak,
            longestStreakDays = longestStreak,
            activeDaysInSelectedMonth = activeDaysInSelectedMonth,
            unlockedBadges = unlockedBadges,
            celebrationMessage = celebrationMessage
        )
    }

}

class CoinNestViewModelFactory(
    private val repository: CoinNestRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CoinNestViewModel(repository) as T
    }
}
