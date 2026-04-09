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
    val topKeywords: List<SmartLearningKeyword> = emptyList()
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

@OptIn(ExperimentalCoroutinesApi::class)
class CoinNestViewModel(
    private val repository: CoinNestRepository
) : ViewModel() {
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

    private val selectedMonthTransactionsFlow = selectedMonthFlow.flatMapLatest { ym ->
        val range = DateRangeUtils.monthRange(ym)
        repository.observeTransactionsInRange(range.first, range.last + 1, limit = 4000)
    }

    private val previousMonthSummaryFlow = selectedMonthFlow.flatMapLatest { ym ->
        val range = DateRangeUtils.monthRange(ym.minusMonths(1))
        repository.observeSummary(range.first, range.last + 1)
    }

    private val selectedMonthCategoryBudgetsFlow = selectedMonthFlow.flatMapLatest { ym ->
        repository.observeCategoryBudgets(DateRangeUtils.monthKey(ym))
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

    private val baseUiStateFlow = combine(
        summaryAndCategoryFlow,
        repository.observeTransactionsInRange(dayRange.first, dayRange.last + 1, limit = 200),
        repository.observeTransactionsInRange(yearRange.first, yearRange.last + 1, limit = 12000),
        selectedMonthDataFlow,
        repository.observePendingAutoTransactions(limit = 100)
    ) { summary, todayTxs, yearTxs, selectedMonthData, pendingTxs ->
        HomeUiState(
            daily = summary.daily,
            monthly = summary.monthly,
            yearly = summary.yearly,
            previousYearSummary = summary.previousYearly,
            selectedMonth = selectedMonthData.month,
            selectedMonthSummary = selectedMonthData.summary,
            previousMonthSummary = selectedMonthData.previousMonthSummary,
            monthBudgetCents = summary.monthBudgetCents,
            categories = summary.categories,
            selectedMonthCategoryBudgets = selectedMonthData.categoryBudgets,
            todayTransactions = todayTxs,
            monthTransactions = selectedMonthData.transactions,
            yearTransactions = yearTxs,
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
            topKeywords = topKeywords
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
