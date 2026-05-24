package com.example.coin_nest.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.coin_nest.data.db.TransactionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

@Composable
internal fun InsightTab(
    state: HomeUiState,
    onSelectMonth: (YearMonth) -> Unit,
    onUpdateTransactionDetails: (Long, String, String, String) -> Unit,
    onDeleteTransaction: (Long) -> Unit,
    onLoadMoreMonthTransactions: () -> Unit,
    onLoadMoreYearTransactions: () -> Unit,
    openMonthDetailAtTodayToken: Int = 0,
    onMonthDetailJumpHandled: () -> Unit = {},
    openSearchAtToken: Int = 0,
    openSearchType: LocalSearchType = LocalSearchType.All,
    openSearchScope: LocalSearchScope = LocalSearchScope.Year,
    onSearchJumpHandled: () -> Unit = {},
    onOpenBudgetSettings: () -> Unit = {}
) {
    val initialRoute = remember { if (openSearchAtToken > 0) "local_search" else "overview" }
    val nav = rememberNavController()
    var lastHandledMonthDetailToken by rememberSaveable { mutableIntStateOf(0) }
    var lastHandledSearchToken by rememberSaveable { mutableIntStateOf(openSearchAtToken) }
    var chartMode by rememberSaveable { mutableStateOf(OverviewTabMode.Monthly) }
    var selectedWeekStart by rememberSaveable { mutableStateOf(LocalDate.now().minusDays((LocalDate.now().dayOfWeek.value - 1).toLong())) }
    var selectedWeekDate by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var selectedMonthDate by rememberSaveable { mutableStateOf(LocalDate.now()) }
    val pendingDeleteTx = remember { mutableStateOf<TransactionEntity?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchType by rememberSaveable { mutableStateOf(openSearchType) }
    var searchScope by rememberSaveable { mutableStateOf(openSearchScope) }

    val selectedMonth = state.selectedMonth
    val monthTx = state.monthTransactions
    val yearTx = state.yearTransactions
    val today = remember { LocalDate.now() }

    LaunchedEffect(Unit) {
        if (openSearchAtToken > 0) onSearchJumpHandled()
    }

    val weekTx = remember(yearTx, selectedWeekStart) {
        val end = selectedWeekStart.plusDays(7)
        yearTx.filter {
            val d = Instant.ofEpochMilli(it.occurredAtEpochMs).atZone(zone).toLocalDate()
            d >= selectedWeekStart && d < end
        }
    }
    val selectedWeekDayTx = remember(weekTx, selectedWeekDate) {
        weekTx.filter { Instant.ofEpochMilli(it.occurredAtEpochMs).atZone(zone).toLocalDate() == selectedWeekDate }
            .sortedByDescending { it.occurredAtEpochMs }
    }
    val monthByDate = remember(monthTx) {
        monthTx.groupBy { Instant.ofEpochMilli(it.occurredAtEpochMs).atZone(zone).toLocalDate() }
    }
    val selectedMonthDayTx = remember(monthByDate, selectedMonthDate) {
        monthByDate[selectedMonthDate].orEmpty().sortedByDescending { it.occurredAtEpochMs }
    }

    val summary = remember(state.selectedMonthSummary, state.previousMonthSummary, state.monthBudgetCents) {
        buildInsightSummary(
            currentExpenseCents = state.selectedMonthSummary.expenseCents,
            previousExpenseCents = state.previousMonthSummary.expenseCents,
            budgetCents = state.monthBudgetCents
        )
    }
    val anomalies = remember(monthTx, state.previousMonthSummary.expenseCents, state.monthBudgetCents, state.selectedMonthCategoryBudgets) {
        buildMonthlyAnomalies(
            monthTx = monthTx,
            previousMonthExpenseCents = state.previousMonthSummary.expenseCents,
            monthBudgetCents = state.monthBudgetCents,
            categoryBudgets = state.selectedMonthCategoryBudgets
        )
    }
    val spendingAiInsight = remember(monthTx, state.previousMonthSummary.expenseCents, state.monthBudgetCents, anomalies) {
        buildSpendingAiInsight(
            monthTx = monthTx,
            previousMonthExpenseCents = state.previousMonthSummary.expenseCents,
            monthBudgetCents = state.monthBudgetCents,
            anomalies = anomalies
        )
    }
    val overviewTrend = remember(chartMode, weekTx, selectedWeekStart, state.monthTrendPoints, state.yearTrendPoints) {
        when (chartMode) {
            OverviewTabMode.Weekly -> buildWeekLineTrend(weekTx, selectedWeekStart)
            OverviewTabMode.Monthly -> state.monthTrendPoints
            OverviewTabMode.Yearly -> state.yearTrendPoints
        }
    }
    val overviewShares = remember(chartMode, weekTx, state.monthCategoryShare, state.yearCategoryShare) {
        when (chartMode) {
            OverviewTabMode.Weekly -> buildCategoryShare(weekTx)
            OverviewTabMode.Monthly -> state.monthCategoryShare
            OverviewTabMode.Yearly -> state.yearCategoryShare
        }
    }
    val focusInsight = remember(overviewShares) {
        buildSpendingFocusInsight(overviewShares)
    }

    LaunchedEffect(selectedMonth) {
        if (YearMonth.from(selectedMonthDate) != selectedMonth) {
            selectedMonthDate = selectedMonth.atDay(1)
        }
    }
    LaunchedEffect(selectedWeekStart) {
        if (selectedWeekDate < selectedWeekStart || selectedWeekDate > selectedWeekStart.plusDays(6)) {
            selectedWeekDate = selectedWeekStart
        }
    }
    LaunchedEffect(openMonthDetailAtTodayToken) {
        if (openMonthDetailAtTodayToken > lastHandledMonthDetailToken) {
            chartMode = OverviewTabMode.Monthly
            val targetMonth = YearMonth.from(today)
            if (selectedMonth != targetMonth) onSelectMonth(targetMonth)
            selectedMonthDate = today
            nav.navigate("month_detail") { launchSingleTop = true }
            lastHandledMonthDetailToken = openMonthDetailAtTodayToken
            onMonthDetailJumpHandled()
        }
    }
    LaunchedEffect(openSearchAtToken, openSearchType, openSearchScope) {
        if (openSearchAtToken > lastHandledSearchToken) {
            searchQuery = ""
            searchType = openSearchType
            searchScope = openSearchScope
            nav.navigate("local_search") { launchSingleTop = true }
            lastHandledSearchToken = openSearchAtToken
            onSearchJumpHandled()
        }
    }

    NavHost(navController = nav, startDestination = initialRoute, modifier = Modifier.fillMaxSize()) {
        composable("overview") {
            InsightOverviewRoute(
                state = state,
                chartMode = chartMode,
                onModeChange = { chartMode = it },
                summary = summary,
                anomalies = anomalies,
                spendingAiInsight = spendingAiInsight,
                focusInsight = focusInsight,
                overviewTrend = overviewTrend,
                overviewShares = overviewShares,
                onOpenDetail = {
                    when (chartMode) {
                        OverviewTabMode.Weekly -> nav.navigate("week_detail")
                        OverviewTabMode.Monthly -> nav.navigate("month_detail")
                        OverviewTabMode.Yearly -> nav.navigate("year_detail")
                    }
                },
                onOpenAnomaly = { nav.navigate("anomaly_detail") },
                onOpenSearch = { nav.navigate("local_search") },
                onOpenBudgetSettings = onOpenBudgetSettings
            )
        }

        composable("local_search") {
            InsightSearchRoute(
                state = state,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                searchType = searchType,
                onSearchTypeChange = { searchType = it },
                searchScope = searchScope,
                onSearchScopeChange = { searchScope = it },
                onUpdateTransactionDetails = onUpdateTransactionDetails,
                onDelete = { pendingDeleteTx.value = it },
                onLoadMoreYearTransactions = onLoadMoreYearTransactions
            )
        }

        composable("week_detail") {
            InsightWeekDetailRoute(
                state = state,
                weekTx = weekTx,
                selectedWeekStart = selectedWeekStart,
                selectedWeekDate = selectedWeekDate,
                selectedWeekDayTx = selectedWeekDayTx,
                onSelectedWeekStartChange = { selectedWeekStart = it },
                onSelectedWeekDateChange = { selectedWeekDate = it },
                onUpdateTransactionDetails = onUpdateTransactionDetails,
                onDelete = { pendingDeleteTx.value = it },
                onLoadMoreYearTransactions = onLoadMoreYearTransactions
            )
        }

        composable("month_detail") {
            InsightMonthDetailRoute(
                state = state,
                selectedMonth = selectedMonth,
                monthByDate = monthByDate,
                selectedMonthDate = selectedMonthDate,
                selectedMonthDayTx = selectedMonthDayTx,
                onSelectMonth = onSelectMonth,
                onSelectedMonthDateChange = { selectedMonthDate = it },
                onUpdateTransactionDetails = onUpdateTransactionDetails,
                onDelete = { pendingDeleteTx.value = it },
                onLoadMoreMonthTransactions = onLoadMoreMonthTransactions
            )
        }

        composable("year_detail") {
            InsightYearDetailRoute(
                state = state,
                selectedMonth = selectedMonth,
                onSelectMonth = onSelectMonth,
                onLoadMoreYearTransactions = onLoadMoreYearTransactions
            )
        }

        composable("anomaly_detail") {
            InsightAnomalyRoute(
                anomalies = anomalies,
                spendingAiInsight = spendingAiInsight
            )
        }
    }

    pendingDeleteTx.value?.let { tx ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTx.value = null },
            title = { Text("删除流水") },
            text = { Text("确认删除该流水记录？") },
            dismissButton = { TextButton(onClick = { pendingDeleteTx.value = null }) { Text("取消") } },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteTransaction(tx.id)
                    pendingDeleteTx.value = null
                }) { Text("删除", color = DangerColor) }
            }
        )
    }
}
