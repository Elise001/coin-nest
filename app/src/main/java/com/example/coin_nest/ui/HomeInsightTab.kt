package com.example.coin_nest.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
    onUpdateTransactionCategory: (Long, String, String) -> Unit,
    onDeleteTransaction: (Long) -> Unit
) {
    onUpdateTransactionCategory
    val nav = rememberNavController()
    var mode by rememberSaveable { mutableStateOf(OverviewTabMode.Monthly) }
    var selectedWeekStart by rememberSaveable { mutableStateOf(LocalDate.now().minusDays((LocalDate.now().dayOfWeek.value - 1).toLong())) }
    var selectedWeekDate by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var selectedMonthDate by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var deleteTx by remember { mutableStateOf<TransactionEntity?>(null) }

    val selectedMonth = state.selectedMonth
    val monthTx = state.monthTransactions
    val yearTx = state.yearTransactions

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

    NavHost(navController = nav, startDestination = "overview", modifier = Modifier.fillMaxSize()) {
        composable("overview") {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { InsightModeSelector(mode = mode, onModeChange = { mode = it }) }
                item { InsightSummaryCard(title = summary.title, detail = summary.detail, nudge = summary.nudge) }
                item {
                    InsightMetricStrip(
                        income = state.selectedMonthSummary.incomeCents,
                        expense = state.selectedMonthSummary.expenseCents,
                        balance = state.selectedMonthSummary.balanceCents
                    )
                }
                item { AchievementMotivationCard(feedback = state.retentionFeedback) }
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
                            OutlinedButton(onClick = { selectedWeekStart = selectedWeekStart.minusWeeks(1) }) { Text("上周") }
                            Text("${selectedWeekStart.monthValue}/${selectedWeekStart.dayOfMonth} - ${selectedWeekStart.plusDays(6).monthValue}/${selectedWeekStart.plusDays(6).dayOfMonth}", fontWeight = FontWeight.SemiBold)
                            OutlinedButton(onClick = { selectedWeekStart = selectedWeekStart.plusWeeks(1) }) { Text("下周") }
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
                } else {
                    items(selectedWeekDayTx, key = { it.id }) { tx ->
                        InsightTransactionRow(tx = tx, onDelete = { deleteTx = tx })
                    }
                }
            }
        }

        composable("month_detail") {
            val monthTrend = remember(monthTx, selectedMonth) { buildMonthLineTrend(monthTx, selectedMonth) }
            val monthShare = remember(monthTx) { buildCategoryShare(monthTx) }
            val monthSummaryByDay = remember(monthByDate) { calculateDaySummary(monthByDate) }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    GlassCard {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { onSelectMonth(selectedMonth.minusMonths(1)) }) { Text("上月") }
                            Text("${selectedMonth.year}年${selectedMonth.monthValue}月", fontWeight = FontWeight.SemiBold)
                            OutlinedButton(onClick = { onSelectMonth(selectedMonth.plusMonths(1)) }) { Text("下月") }
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
                } else {
                    items(selectedMonthDayTx, key = { it.id }) { tx ->
                        InsightTransactionRow(tx = tx, onDelete = { deleteTx = tx })
                    }
                }
            }
        }

        composable("year_detail") {
            val yearTrend = remember(yearTx) { buildYearLineTrend(yearTx) }
            val yearShare = remember(yearTx) { buildCategoryShare(yearTx) }
            val yearSummary = remember(yearTx) { calculateSummary(yearTx) }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    GlassCard {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { onSelectMonth(YearMonth.of(selectedMonth.year - 1, selectedMonth.monthValue)) }) { Text("上一年") }
                            Text("${selectedMonth.year} 年分析", fontWeight = FontWeight.SemiBold)
                            OutlinedButton(onClick = { onSelectMonth(YearMonth.of(selectedMonth.year + 1, selectedMonth.monthValue)) }) { Text("下一年") }
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
            }
        }

        composable("anomaly_detail") {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (anomalies.isEmpty()) {
                    item { GlassCard { Text("本月暂无明显异常，继续保持当前记账节奏。") } }
                } else {
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
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OverviewTabMode.entries.forEach { item ->
            val selected = item == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                    .border(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    .clickable { onModeChange(item) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(item.title, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
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
private fun AchievementMotivationCard(feedback: RetentionFeedbackState) {
    val milestones = listOf(3, 7, 14, 30)
    val next = milestones.firstOrNull { feedback.currentStreakDays < it } ?: 30
    val progress = (feedback.currentStreakDays.toFloat() / next.toFloat()).coerceIn(0f, 1f)
    GlassCard {
        Text("成就与留存", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        Text("连续记账 ${feedback.currentStreakDays} 天，距离 ${next} 天还差 ${(next - feedback.currentStreakDays).coerceAtLeast(0)} 天", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))) {
            Box(modifier = Modifier.fillMaxWidth(progress).height(8.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text("本月活跃 ${feedback.activeDaysInSelectedMonth} 天 · 最长连记 ${feedback.longestStreakDays} 天", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InsightEntryCard(title: String, subtitle: String, onClick: () -> Unit) {
    GlassCard {
        Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(">", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
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
        Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
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
                    val hasTx = date != null && (dailySummary[date]?.expenseCents ?: 0L) > 0L
                    val selected = date == selectedDate
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
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
                            .clickable(enabled = date != null) { if (date != null) onSelectDate(date) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(date?.dayOfMonth?.toString().orEmpty(), color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightTransactionRow(tx: TransactionEntity, onDelete: () -> Unit) {
    val prefix = if (tx.type == "INCOME") "+" else "-"
    val amountColor = if (tx.type == "INCOME") SuccessColor else DangerColor
    val timeText = remember(tx.occurredAtEpochMs) { Instant.ofEpochMilli(tx.occurredAtEpochMs).atZone(zone).format(rowTimeFormatter) }
    GlassCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text("$prefix${MoneyFormat.fromCents(tx.amountCents)}", fontWeight = FontWeight.Bold, color = amountColor, fontFamily = FontFamily.Monospace)
                Text("${tx.parentCategory}/${tx.childCategory}", style = MaterialTheme.typography.bodySmall)
                Text(timeText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("删除", color = DangerColor, modifier = Modifier.clickable { onDelete() })
        }
    }
}
