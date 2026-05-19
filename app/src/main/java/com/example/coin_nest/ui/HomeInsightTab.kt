package com.example.coin_nest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    onOpenBudgetSettings: () -> Unit = {}
) {
    val nav = rememberNavController()
    var lastHandledMonthDetailToken by rememberSaveable { mutableIntStateOf(0) }
    var chartMode by rememberSaveable { mutableStateOf(OverviewTabMode.Monthly) }
    var selectedWeekStart by rememberSaveable { mutableStateOf(LocalDate.now().minusDays((LocalDate.now().dayOfWeek.value - 1).toLong())) }
    var selectedWeekDate by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var selectedMonthDate by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var deleteTx by remember { mutableStateOf<TransactionEntity?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchType by rememberSaveable { mutableStateOf(LocalSearchType.All) }

    val selectedMonth = state.selectedMonth
    val monthTx = state.monthTransactions
    val yearTx = state.yearTransactions
    val today = remember { LocalDate.now() }

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

    NavHost(navController = nav, startDestination = "overview", modifier = Modifier.fillMaxSize()) {
        composable("overview") {
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
                        onModeChange = { chartMode = it },
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
                        onOpenDetail = {
                            when (chartMode) {
                                OverviewTabMode.Weekly -> nav.navigate("week_detail")
                                OverviewTabMode.Monthly -> nav.navigate("month_detail")
                                OverviewTabMode.Yearly -> nav.navigate("year_detail")
                            }
                        },
                        onOpenAnomaly = { nav.navigate("anomaly_detail") },
                        onOpenSearch = { nav.navigate("local_search") }
                    )
                }
            }
        }

        composable("local_search") {
            val results = remember(yearTx, searchQuery, searchType) {
                filterLocalTransactions(
                    transactions = yearTx,
                    query = searchQuery,
                    type = searchType
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    LocalSearchControlCard(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        type = searchType,
                        onTypeChange = { searchType = it }
                    )
                }
                item {
                    LocalSearchSummaryCard(
                        query = searchQuery,
                        results = results,
                        hasMore = state.yearHasMore,
                        onLoadMore = onLoadMoreYearTransactions
                    )
                }
                if (results.isEmpty()) {
                    item {
                        GlassCard {
                            Text("没有找到匹配流水", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "可尝试输入金额、来源、分类或备注关键词；如果年份数据较多，也可以先加载更多。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(results.take(80), key = { it.id }) { tx ->
                        InsightTransactionRow(
                            tx = tx,
                            categories = state.categories,
                            onUpdateTransaction = onUpdateTransactionDetails,
                            onDelete = { deleteTx = tx }
                        )
                    }
                    if (results.size > 80) {
                        item {
                            GlassCard {
                                Text("已显示前 80 条，请增加关键词缩小范围。", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        composable("week_detail") {
            val weekTrend = remember(weekTx, selectedWeekStart) { buildWeekLineTrend(weekTx, selectedWeekStart) }
            val weekShare = remember(weekTx) { buildCategoryShare(weekTx) }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    GlassCard {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { selectedWeekStart = selectedWeekStart.minusWeeks(1) },
                                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                            ) { Text("上周") }
                            Text("${selectedWeekStart.monthValue}/${selectedWeekStart.dayOfMonth} - ${selectedWeekStart.plusDays(6).monthValue}/${selectedWeekStart.plusDays(6).dayOfMonth}", fontWeight = FontWeight.SemiBold)
                            OutlinedButton(
                                onClick = { selectedWeekStart = selectedWeekStart.plusWeeks(1) },
                                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                            ) { Text("下周") }
                        }
                    }
                }
                item { InsightBarTrendCard(title = "周支出柱状图", points = weekTrend.take(7)) }
                item { InsightPieCard(title = "周分类饼图", shares = weekShare) }
                item {
                    GlassCard {
                        Text("周内日期", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (0..6).forEach { idx ->
                                val day = selectedWeekStart.plusDays(idx.toLong())
                                val selected = day == selectedWeekDate
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface)
                                        .border(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                        .clickable { selectedWeekDate = day }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${day.dayOfMonth}", color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                if (selectedWeekDayTx.isEmpty()) {
                    item { GlassCard { Text("${selectedWeekDate.monthValue}/${selectedWeekDate.dayOfMonth} 暂无流水") } }
                    if (state.yearHasMore) {
                        item {
                            OutlinedButton(
                                onClick = onLoadMoreYearTransactions,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("加载更多历史流水") }
                        }
                    }
                } else {
                    items(selectedWeekDayTx, key = { it.id }) { tx ->
                        InsightTransactionRow(
                            tx = tx,
                            categories = state.categories,
                            onUpdateTransaction = onUpdateTransactionDetails,
                            onDelete = { deleteTx = tx }
                        )
                    }
                }
            }
        }

        composable("month_detail") {
            val monthTrend = state.monthTrendPoints
            val monthShare = state.monthCategoryShare
            val monthSummaryByDay = remember(monthByDate) { calculateDaySummary(monthByDate) }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    GlassCard {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { onSelectMonth(selectedMonth.minusMonths(1)) },
                                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                            ) { Text("上月") }
                            Text("${selectedMonth.year}年${selectedMonth.monthValue}月", fontWeight = FontWeight.SemiBold)
                            OutlinedButton(
                                onClick = { onSelectMonth(selectedMonth.plusMonths(1)) },
                                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                            ) { Text("下月") }
                        }
                    }
                }
                item { InsightBarTrendCard(title = "月支出柱状图", points = monthTrend) }
                item { InsightPieCard(title = "月分类饼图", shares = monthShare) }
                item {
                    InsightMonthCalendarCard(
                        month = selectedMonth,
                        selectedDate = selectedMonthDate,
                        dailySummary = monthSummaryByDay,
                        onSelectDate = { selectedMonthDate = it }
                    )
                }
                if (selectedMonthDayTx.isEmpty()) {
                    item { GlassCard { Text("${selectedMonthDate.monthValue}/${selectedMonthDate.dayOfMonth} 暂无流水") } }
                    if (state.monthHasMore) {
                        item {
                            OutlinedButton(
                                onClick = onLoadMoreMonthTransactions,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("加载更多本月流水") }
                        }
                    }
                } else {
                    items(selectedMonthDayTx, key = { it.id }) { tx ->
                        InsightTransactionRow(
                            tx = tx,
                            categories = state.categories,
                            onUpdateTransaction = onUpdateTransactionDetails,
                            onDelete = { deleteTx = tx }
                        )
                    }
                    if (state.monthHasMore) {
                        item {
                            OutlinedButton(
                                onClick = onLoadMoreMonthTransactions,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("加载更多本月流水") }
                        }
                    }
                }
            }
        }

        composable("year_detail") {
            val yearTrend = state.yearTrendPoints
            val yearShare = state.yearCategoryShare
            val yearSummary = state.selectedYearSummary
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    GlassCard {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { onSelectMonth(YearMonth.of(selectedMonth.year - 1, selectedMonth.monthValue)) },
                                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                            ) { Text("上一年") }
                            Text("${selectedMonth.year} 年分析", fontWeight = FontWeight.SemiBold)
                            OutlinedButton(
                                onClick = { onSelectMonth(YearMonth.of(selectedMonth.year + 1, selectedMonth.monthValue)) },
                                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                            ) { Text("下一年") }
                        }
                    }
                }
                item {
                    InsightMetricStrip(
                        income = yearSummary.incomeCents,
                        expense = yearSummary.expenseCents,
                        balance = yearSummary.balanceCents
                    )
                }
                item { InsightBarTrendCard(title = "年内月度支出柱状图", points = yearTrend) }
                item { InsightPieCard(title = "年度分类饼图", shares = yearShare) }
                if (state.yearHasMore) {
                    item {
                        OutlinedButton(
                            onClick = onLoadMoreYearTransactions,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("加载更多历史流水（用于周视图）") }
                    }
                }
            }
        }

        composable("anomaly_detail") {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (anomalies.isEmpty()) {
                    item {
                        GlassCard {
                            Text("本月暂无明显异常，继续保持当前记账节奏。")
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                spendingAiInsight.anomalyExplanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    item {
                        GlassCard(tone = GlassCardTone.Warning) {
                            Text("本地异常解释", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                spendingAiInsight.anomalyExplanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(anomalies, key = { it.id }) { anomaly ->
                        GlassCard(tone = GlassCardTone.Warning) {
                            Text(anomaly.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(anomaly.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(6.dp))
                            anomaly.suggestions.take(3).forEach { s ->
                                Text("• $s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (deleteTx != null) {
        AlertDialog(
            onDismissRequest = { deleteTx = null },
            title = { Text("删除流水") },
            text = { Text("确认删除该流水记录？") },
            dismissButton = { TextButton(onClick = { deleteTx = null }) { Text("取消") } },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteTransaction(deleteTx!!.id)
                    deleteTx = null
                }) { Text("删除", color = DangerColor) }
            }
        )
    }
}

