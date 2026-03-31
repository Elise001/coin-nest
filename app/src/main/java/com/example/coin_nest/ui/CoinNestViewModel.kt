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
    val selectedMonth: YearMonth = YearMonth.now(),
    val selectedMonthSummary: BalanceSummary = BalanceSummary(),
    val monthBudgetCents: Long? = null,
    val categories: List<CategoryItem> = emptyList(),
    val todayTransactions: List<TransactionEntity> = emptyList(),
    val monthTransactions: List<TransactionEntity> = emptyList(),
    val yearTransactions: List<TransactionEntity> = emptyList()
)

private data class SummaryAndCategory(
    val daily: BalanceSummary,
    val monthly: BalanceSummary,
    val yearly: BalanceSummary,
    val monthBudgetCents: Long?,
    val categories: List<CategoryItem>
)

private data class SelectedMonthData(
    val month: YearMonth,
    val summary: BalanceSummary,
    val transactions: List<TransactionEntity>
)

@OptIn(ExperimentalCoroutinesApi::class)
class CoinNestViewModel(
    private val repository: CoinNestRepository
) : ViewModel() {
    private val dayRange = DateRangeUtils.todayRange()
    private val monthRange = DateRangeUtils.monthRange()
    private val yearRange = DateRangeUtils.yearRange()
    private val monthKey = DateRangeUtils.currentMonthKey()
    private val selectedMonthFlow = MutableStateFlow(YearMonth.now())

    private val summaryAndCategoryFlow = combine(
        repository.observeSummary(dayRange.first, dayRange.last + 1),
        repository.observeSummary(monthRange.first, monthRange.last + 1),
        repository.observeSummary(yearRange.first, yearRange.last + 1),
        repository.observeMonthBudget(monthKey),
        repository.observeCategories()
    ) { daily, monthly, yearly, budget, categories ->
        SummaryAndCategory(
            daily = daily,
            monthly = monthly,
            yearly = yearly,
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

    private val selectedMonthDataFlow = combine(
        selectedMonthFlow,
        selectedMonthSummaryFlow,
        selectedMonthTransactionsFlow
    ) { month, summary, transactions ->
        SelectedMonthData(
            month = month,
            summary = summary,
            transactions = transactions
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        summaryAndCategoryFlow,
        repository.observeTransactionsInRange(dayRange.first, dayRange.last + 1, limit = 200),
        repository.observeTransactionsInRange(yearRange.first, yearRange.last + 1, limit = 12000),
        selectedMonthDataFlow
    ) { summary, todayTxs, yearTxs, selectedMonthData ->
        HomeUiState(
            daily = summary.daily,
            monthly = summary.monthly,
            yearly = summary.yearly,
            selectedMonth = selectedMonthData.month,
            selectedMonthSummary = selectedMonthData.summary,
            monthBudgetCents = summary.monthBudgetCents,
            categories = summary.categories,
            todayTransactions = todayTxs,
            monthTransactions = selectedMonthData.transactions,
            yearTransactions = yearTxs
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
        note: String
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
                    occurredAtEpochMs = System.currentTimeMillis()
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
}

class CoinNestViewModelFactory(
    private val repository: CoinNestRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CoinNestViewModel(repository) as T
    }
}
