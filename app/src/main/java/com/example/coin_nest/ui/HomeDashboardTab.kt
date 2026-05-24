package com.example.coin_nest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun HomeDashboardTab(
    state: HomeUiState,
    onOpenRecord: () -> Unit,
    onOpenInsight: () -> Unit,
    onUpdateTransaction: (Long, String, String, String) -> Unit,
    onOpenInsightMonthCalendar: () -> Unit,
    onOpenIncomeSearch: () -> Unit,
    onOpenExpenseSearch: () -> Unit,
    onOpenBudgetSettings: () -> Unit
) {
    val anomalies = remember(
        state.monthTransactions,
        state.previousMonthSummary.expenseCents,
        state.monthBudgetCents,
        state.selectedMonthCategoryBudgets
    ) {
        buildMonthlyAnomalies(
            monthTx = state.monthTransactions,
            previousMonthExpenseCents = state.previousMonthSummary.expenseCents,
            monthBudgetCents = state.monthBudgetCents,
            categoryBudgets = state.selectedMonthCategoryBudgets
        )
    }
    val keyAnomaly = remember(anomalies) { anomalies.firstOrNull() }
    val topCategory = remember(state.monthCategoryShare) { state.monthCategoryShare.maxByOrNull { it.amountCents } }
    val todayPreviewTransactions = remember(state.todayTransactions) {
        state.todayTransactions
            .sortedByDescending { it.occurredAtEpochMs }
            .take(3)
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item {
            MoneyHeroCard(
                month = state.selectedMonth,
                income = state.selectedMonthSummary.incomeCents,
                expense = state.selectedMonthSummary.expenseCents,
                balance = state.selectedMonthSummary.balanceCents,
                budget = state.monthBudgetCents,
                pendingCount = state.pendingAutoTransactions.size,
                onRecord = onOpenRecord,
                onOpenCalendar = onOpenInsightMonthCalendar,
                onOpenIncome = onOpenIncomeSearch,
                onOpenExpense = onOpenExpenseSearch,
                onOpenBudgetSettings = onOpenBudgetSettings
            )
        }
        item {
            SignalCard(
                icon = if (keyAnomaly == null) Icons.Filled.Savings else Icons.Filled.Warning,
                title = keyAnomaly?.title ?: "节奏正常",
                detail = keyAnomaly?.detail ?: "本月暂无明显异常，保持自动记账和每天看一眼即可。",
                warning = keyAnomaly != null,
                onClick = onOpenInsight
            )
        }
        item {
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Receipt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("今日流水", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = onOpenInsight, modifier = Modifier) { Text("更多") }
                }
                Spacer(modifier = Modifier.height(6.dp))
                if (todayPreviewTransactions.isEmpty()) {
                    Text(
                        "今天还没有记录，点上方“记一笔”补上即可。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    todayPreviewTransactions.forEachIndexed { index, tx ->
                        TransactionRow(
                            tx = tx,
                            categories = state.categories,
                            allowCategoryEdit = true,
                            onUpdateTransaction = onUpdateTransaction
                        )
                        if (index != todayPreviewTransactions.lastIndex) Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
        item {
            TopCategoryCard(topCategory = topCategory, onOpenInsight = onOpenInsight)
        }
    }
}

