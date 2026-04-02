package com.example.coin_nest.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.coin_nest.data.CoinNestRepository
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
    val todayTransactions: List<TransactionEntity> = emptyList(),
    val monthTransactions: List<TransactionEntity> = emptyList(),
    val yearTransactions: List<TransactionEntity> = emptyList(),
    val pendingAutoTransactions: List<TransactionEntity> = emptyList()
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
    val transactions: List<TransactionEntity>
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

    private val summaryAndCategoryFlow = combine(
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

    private val selectedMonthDataFlow = combine(
        selectedMonthFlow,
        selectedMonthSummaryFlow,
        previousMonthSummaryFlow,
        selectedMonthTransactionsFlow
    ) { month, summary, previousSummary, transactions ->
        SelectedMonthData(
            month = month,
            summary = summary,
            previousMonthSummary = previousSummary,
            transactions = transactions
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
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
            todayTransactions = todayTxs,
            monthTransactions = selectedMonthData.transactions,
            yearTransactions = yearTxs,
            pendingAutoTransactions = pendingTxs
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
                .onFailure { onError(it.message ?: "导出失败") }
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
                .onFailure { onError(it.message ?: "导入失败") }
        }
    }

    fun importTransactions(
        drafts: List<ImportTransactionDraft>,
        onResult: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                var count = 0
                drafts.forEach { draft ->
                    val amount = draft.amountYuan.toDoubleOrNull() ?: return@forEach
                    if (amount <= 0.0) return@forEach
                    repository.addTransaction(
                        TransactionInput(
                            amountCents = (amount * 100).toLong(),
                            type = if (draft.isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
                            parentCategory = draft.parentCategory,
                            childCategory = draft.childCategory,
                            source = draft.source,
                            note = draft.note,
                            occurredAtEpochMs = draft.occurredAtEpochMs
                        )
                    )
                    count++
                }
                count
            }.onSuccess(onResult)
                .onFailure { onError(it.message ?: "批量导入失败") }
        }
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
