package com.example.coin_nest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun InsightOverviewRoute(
    state: HomeUiState,
    chartMode: OverviewTabMode,
    onModeChange: (OverviewTabMode) -> Unit,
    summary: InsightSummary,
    anomalies: List<AnomalyInsight>,
    spendingAiInsight: SpendingAiInsight,
    focusInsight: SpendingFocusInsight?,
    overviewTrend: List<TrendPoint>,
    overviewShares: List<CategoryShare>,
    onOpenDetail: () -> Unit,
    onOpenAnomaly: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenBudgetSettings: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            InsightCommandCard(
                detail = summary.detail,
                nudge = summary.nudge,
                focusInsight = focusInsight,
                income = state.selectedMonthSummary.incomeCents,
                expense = state.selectedMonthSummary.expenseCents,
                balance = state.selectedMonthSummary.balanceCents,
                anomalyCount = anomalies.size
            )
        }
        item {
            InsightSnapshotCard(
                mode = chartMode,
                onModeChange = onModeChange,
                points = overviewTrend,
                shares = overviewShares
            )
        }
        item {
            SpendingAiInsightCard(
                insight = spendingAiInsight,
                onOpenBudgetSettings = onOpenBudgetSettings
            )
        }
        item {
            InsightActionListCard(
                mode = chartMode,
                anomalyCount = anomalies.size,
                onOpenDetail = onOpenDetail,
                onOpenAnomaly = onOpenAnomaly,
                onOpenSearch = onOpenSearch
            )
        }
    }
}
