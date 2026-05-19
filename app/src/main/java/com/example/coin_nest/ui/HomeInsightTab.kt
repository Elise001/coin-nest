package com.example.coin_nest.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.coin_nest.data.db.TransactionEntity
import com.example.coin_nest.util.MoneyFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

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
    var mode by rememberSaveable { mutableStateOf(OverviewTabMode.Monthly) }
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
            mode = OverviewTabMode.Monthly
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
                item { InsightModeSelector(mode = mode, onModeChange = { mode = it }) }
                item {
                    InsightCommandCard(
                        mode = mode,
                        title = summary.title,
                        detail = summary.detail,
                        nudge = summary.nudge,
                        income = state.selectedMonthSummary.incomeCents,
                        expense = state.selectedMonthSummary.expenseCents,
                        balance = state.selectedMonthSummary.balanceCents,
                        anomalyCount = anomalies.size
                    )
                }
                item {
                    val focusShares = when (mode) {
                        OverviewTabMode.Weekly -> state.monthCategoryShare
                        OverviewTabMode.Monthly -> state.monthCategoryShare
                        OverviewTabMode.Yearly -> state.yearCategoryShare
                    }
                    SpendingFocusCard(mode = mode, shares = focusShares, onOpenBudgetSettings = onOpenBudgetSettings)
                }
                item {
                    SpendingAiInsightCard(
                        insight = spendingAiInsight,
                        onOpenBudgetSettings = onOpenBudgetSettings
                    )
                }
                item {
                    when (mode) {
                        OverviewTabMode.Weekly -> InsightEntryCard(
                            title = "周流水",
                            subtitle = "周趋势 + 分类饼图 + 周内任意天明细"
                        ) { nav.navigate("week_detail") }

                        OverviewTabMode.Monthly -> InsightEntryCard(
                            title = "月日历与流水",
                            subtitle = "月趋势 + 分类饼图 + 日历选日明细"
                        ) { nav.navigate("month_detail") }

                        OverviewTabMode.Yearly -> InsightEntryCard(
                            title = "年流水分析",
                            subtitle = "年度趋势 + 分类占比 + 历史年份分析"
                        ) { nav.navigate("year_detail") }
                    }
                }
                item {
                    InsightEntryCard(
                        title = "异常与建议",
                        subtitle = if (anomalies.isEmpty()) "暂无异常" else "发现 ${anomalies.size} 条风险提示"
                    ) { nav.navigate("anomaly_detail") }
                }
                item {
                    InsightEntryCard(
                        title = "本地找账",
                        subtitle = "按金额、分类、来源、备注快速定位流水"
                    ) { nav.navigate("local_search") }
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

private data class InsightSummary(val title: String, val detail: String, val nudge: String)

@Composable
private fun InsightCommandCard(
    mode: OverviewTabMode,
    title: String,
    detail: String,
    nudge: String,
    income: Long,
    expense: Long,
    balance: Long,
    anomalyCount: Int
) {
    GlassCard {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${mode.title}度指挥台",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.86f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (anomalyCount > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (anomalyCount > 0) "$anomalyCount 条异常" else "无异常",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (anomalyCount > 0) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = nudge,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricOnDark("收入", MoneyFormat.fromCents(income), Modifier.weight(1f))
                    MetricOnDark("支出", MoneyFormat.fromCents(expense), Modifier.weight(1f))
                    MetricOnDark("结余", MoneyFormat.fromCents(balance), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MetricOnDark(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.13f))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f), fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(3.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

private fun buildInsightSummary(
    currentExpenseCents: Long,
    previousExpenseCents: Long,
    budgetCents: Long?
): InsightSummary {
    val nudge = when {
        budgetCents != null && budgetCents > 0L -> {
            val ratio = currentExpenseCents.toDouble() / budgetCents.toDouble()
            when {
                ratio >= 1.0 -> "预算超额，建议本周收缩非必要消费。"
                ratio >= 0.8 -> "预算已使用 ${(ratio * 100).roundToInt()}%，请关注剩余天数。"
                else -> "预算使用率 ${(ratio * 100).roundToInt()}%，节奏正常。"
            }
        }
        else -> "尚未设置预算，建议先设本月上限。"
    }
    val detail = if (previousExpenseCents > 0L) {
        val delta = currentExpenseCents - previousExpenseCents
        if (delta >= 0L) "较上月多支出 ${MoneyFormat.fromCents(delta)}" else "较上月少支出 ${MoneyFormat.fromCents(-delta)}"
    } else {
        "暂无可对比上月数据"
    }
    return InsightSummary(title = "智能总结", detail = detail, nudge = nudge)
}

@Composable
private fun InsightModeSelector(mode: OverviewTabMode, onModeChange: (OverviewTabMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f), RoundedCornerShape(10.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OverviewTabMode.entries.forEach { item ->
            val selected = item == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                    .clickable { onModeChange(item) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    item.title,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun InsightSummaryCard(title: String, detail: String, nudge: String) {
    GlassCard(tone = GlassCardTone.Warning) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        Text(nudge, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InsightMetricStrip(income: Long, expense: Long, balance: Long) {
    GlassCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricPill("收入", MoneyFormat.fromCents(income), Modifier.weight(1f))
            MetricPill("支出", MoneyFormat.fromCents(expense), Modifier.weight(1f))
            MetricPill("结余", MoneyFormat.fromCents(balance), Modifier.weight(1f))
        }
    }
}

@Composable
private fun SpendingFocusCard(
    mode: OverviewTabMode,
    shares: List<CategoryShare>,
    onOpenBudgetSettings: () -> Unit
) {
    val insight = remember(mode, shares) { buildSpendingFocusInsight(mode, shares) }
    GlassCard {
        Text("优先控制分类", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        if (insight == null) {
            Text(
                "暂无足够分类数据，先保持自动记账，形成一周以上样本后再看重点。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@GlassCard
        }
        Text(
            insight.summaryText,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(insight.actionText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenBudgetSettings, modifier = Modifier.fillMaxWidth()) {
            Text("去设置分类预算")
        }
    }
}

@Composable
private fun SpendingAiInsightCard(
    insight: SpendingAiInsight,
    onOpenBudgetSettings: () -> Unit
) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("本地 AI 消费画像", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "可信度 ${insight.confidenceLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(insight.habitProfile, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            insight.budgetAdvice,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (insight.suggestedBudgetCents != null) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenBudgetSettings, modifier = Modifier.fillMaxWidth()) {
                Text("参考预算 ${MoneyFormat.fromCents(insight.suggestedBudgetCents)}")
            }
        }
    }
}

private data class SpendingFocusInsight(
    val summaryText: String,
    val actionText: String
)

private fun buildSpendingFocusInsight(
    mode: OverviewTabMode,
    shares: List<CategoryShare>
): SpendingFocusInsight? {
    val top = shares.maxByOrNull { it.amountCents }?.takeIf { it.amountCents > 0L } ?: return null
    val periodLabel = when (mode) {
        OverviewTabMode.Weekly -> "近期"
        OverviewTabMode.Monthly -> "本月"
        OverviewTabMode.Yearly -> "今年"
    }
    val ratioPercent = (top.ratio * 100).toInt()
    val actionText = when {
        top.ratio >= 0.45f -> "占比偏集中，建议先给这个分类设一个小上限，连续 3 天观察变化。"
        top.ratio >= 0.28f -> "这是当前最大支出来源，建议优先检查是否有可延后或可替代消费。"
        else -> "支出结构较分散，建议先关注高频小额消费，避免无感累积。"
    }
    return SpendingFocusInsight(
        summaryText = "$periodLabel「${top.name}」支出 ${MoneyFormat.fromCents(top.amountCents)}，占比 $ratioPercent%。",
        actionText = actionText
    )
}

@Composable
private fun InsightEntryCard(title: String, subtitle: String, onClick: () -> Unit) {
    GlassCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .semantics { role = Role.Button }
                .clickable { onClick() }
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InsightBarTrendCard(title: String, points: List<TrendPoint>) {
    GlassCard {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        if (points.isEmpty()) {
            Text("暂无趋势数据")
            return@GlassCard
        }
        val max = points.maxOf { it.expenseCents }.coerceAtLeast(1L)
        val chartSummary = points.takeLast(12).joinToString("，") { "${it.label}支出${MoneyFormat.fromCents(it.expenseCents)}" }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "$title：$chartSummary" },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            points.takeLast(12).forEach { p ->
                val ratio = (p.expenseCents.toFloat() / max.toFloat()).coerceIn(0f, 1f)
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)), contentAlignment = Alignment.BottomCenter) {
                        Box(modifier = Modifier.fillMaxWidth().height((42f * ratio).dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)))
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(p.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun InsightPieCard(title: String, shares: List<CategoryShare>) {
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
    )
    GlassCard {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        if (shares.isEmpty()) {
            Text("暂无分类数据")
            return@GlassCard
        }
        val topShareSummary = shares.take(5).joinToString("，") {
            "${it.name}${(it.ratio * 100).roundToInt()}%"
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .semantics { contentDescription = "$title：$topShareSummary" }
        ) {
            val diameter = size.minDimension * 0.75f
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            var start = -90f
            shares.take(5).forEachIndexed { idx, item ->
                val sweep = item.ratio.coerceIn(0f, 1f) * 360f
                drawArc(
                    color = palette[idx % palette.size],
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = topLeft,
                    size = Size(diameter, diameter)
                )
                start += sweep
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        shares.take(5).forEachIndexed { idx, item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(8.dp).height(8.dp).background(palette[idx % palette.size], RoundedCornerShape(2.dp)))
                Spacer(modifier = Modifier.width(6.dp))
                Text("${item.name} ${(item.ratio * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun InsightMonthCalendarCard(
    month: YearMonth,
    selectedDate: LocalDate,
    dailySummary: Map<LocalDate, DayAmountSummary>,
    onSelectDate: (LocalDate) -> Unit
) {
    val cells = remember(month) {
        val first = month.atDay(1)
        val offset = first.dayOfWeek.value - 1
        val total = month.lengthOfMonth()
        val count = ((offset + total + 6) / 7) * 7
        buildList<LocalDate?> {
            repeat(offset) { add(null) }
            for (d in 1..total) add(month.atDay(d))
            repeat(count - size) { add(null) }
        }
    }
    val weekNames = listOf("一", "二", "三", "四", "五", "六", "日")
    GlassCard {
        Text("选择日期", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            weekNames.forEach { w -> Text(w, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(modifier = Modifier.height(6.dp))
        for (i in cells.indices step 7) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (j in 0..6) {
                    val date = cells[i + j]
                    val expenseCents = if (date == null) 0L else (dailySummary[date]?.expenseCents ?: 0L)
                    val hasTx = expenseCents > 0L
                    val selected = date == selectedDate
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    date == null -> Color.Transparent
                                    selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    hasTx -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    else -> Color.Transparent
                                }
                            )
                            .semantics {
                                if (date != null) {
                                    role = Role.Button
                                    contentDescription = "${date.monthValue}月${date.dayOfMonth}日，支出${MoneyFormat.fromCents(expenseCents)}"
                                }
                            }
                            .clickable(enabled = date != null) { if (date != null) onSelectDate(date) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(date?.dayOfMonth?.toString().orEmpty(), color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        if (hasTx) {
                            Text(
                                text = MoneyFormat.fromCents(expenseCents),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightTransactionRow(
    tx: TransactionEntity,
    categories: List<com.example.coin_nest.data.model.CategoryItem>,
    onUpdateTransaction: (Long, String, String, String) -> Unit,
    onDelete: () -> Unit
) {
    TransactionRow(
        tx = tx,
        categories = categories,
        allowCategoryEdit = true,
        onUpdateTransaction = onUpdateTransaction,
        onDelete = onDelete
    )
}
