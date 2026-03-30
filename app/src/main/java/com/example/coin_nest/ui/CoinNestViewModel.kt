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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val daily: BalanceSummary = BalanceSummary(),
    val monthly: BalanceSummary = BalanceSummary(),
    val yearly: BalanceSummary = BalanceSummary(),
    val monthBudgetCents: Long? = null,
    val categories: List<CategoryItem> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList()
)

private data class SummaryAndCategory(
    val daily: BalanceSummary,
    val monthly: BalanceSummary,
    val yearly: BalanceSummary,
    val monthBudgetCents: Long?,
    val categories: List<CategoryItem>
)

class CoinNestViewModel(
    private val repository: CoinNestRepository
) : ViewModel() {
    private val dayRange = DateRangeUtils.todayRange()
    private val monthRange = DateRangeUtils.monthRange()
    private val yearRange = DateRangeUtils.yearRange()
    private val monthKey = DateRangeUtils.currentMonthKey()

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

    val uiState: StateFlow<HomeUiState> = combine(
        summaryAndCategoryFlow,
        repository.observeRecentTransactions()
    ) { summary, txs ->
        HomeUiState(
            daily = summary.daily,
            monthly = summary.monthly,
            yearly = summary.yearly,
            monthBudgetCents = summary.monthBudgetCents,
            categories = summary.categories,
            transactions = txs
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
                    parentCategory = parentCategory.ifBlank { if (isIncome) "收入" else "生活" },
                    childCategory = childCategory.ifBlank { if (isIncome) "其他" else "未分类" },
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
}

class CoinNestViewModelFactory(
    private val repository: CoinNestRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CoinNestViewModel(repository) as T
    }
}

