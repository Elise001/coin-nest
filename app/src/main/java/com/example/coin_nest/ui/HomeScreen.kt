package com.example.coin_nest.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.example.coin_nest.data.db.TransactionEntity
import com.example.coin_nest.util.MoneyFormat
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

@Composable
fun HomeScreen(
    state: HomeUiState,
    initialMainTabIndex: Int = 0,
    onAddTransaction: (String, Boolean, String, String, String, Long) -> Unit,
    onConfirmPendingAuto: (Long) -> Unit,
    onIgnorePendingAuto: (Long) -> Unit,
    onUpdateTransactionCategory: (Long, String, String) -> Unit,
    onDeleteTransaction: (Long) -> Unit,
    onAddCategory: (String, String) -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onSetMonthBudget: (String) -> Unit,
    onSetCategoryBudget: (String, String, String) -> Unit,
    onExportBackup: (onResult: (String) -> Unit, onError: (String) -> Unit) -> Unit,
    onImportBackup: (
        json: String,
        replaceExisting: Boolean,
        onResult: (Int, Int) -> Unit,
        onError: (String) -> Unit
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMainTab by rememberSaveable { mutableIntStateOf(initialMainTabIndex.coerceIn(0, MainTab.entries.size - 1)) }
    val mainTabs = remember { MainTab.entries }
    LaunchedEffect(initialMainTabIndex) {
        selectedMainTab = initialMainTabIndex.coerceIn(0, mainTabs.size - 1)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFFF7EA))
    ) {
        Text(
            text = "Coin Nest",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
        )
        Text(
            text = "个人记账与预算",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        TopMainTabs(
            tabs = mainTabs,
            selectedIndex = selectedMainTab,
            onSelect = { selectedMainTab = it }
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(modifier = Modifier.weight(1f)) {
            when (mainTabs[selectedMainTab]) {
                MainTab.Overview -> OverviewScreen(
                    state = state,
                    onSelectMonth = onSelectMonth,
                    onUpdateTransactionCategory = onUpdateTransactionCategory,
                    onDeleteTransaction = onDeleteTransaction
                )
                MainTab.Record -> RecordTab(state, onAddTransaction, onConfirmPendingAuto, onIgnorePendingAuto)
                MainTab.Settings -> SettingsTab(
                    state = state,
                    onAddCategory = onAddCategory,
                    onSetMonthBudget = onSetMonthBudget,
                    onSetCategoryBudget = onSetCategoryBudget,
                    onExportBackup = onExportBackup,
                    onImportBackup = onImportBackup
                )
            }
        }
    }
}

@Composable
private fun TopMainTabs(
    tabs: List<MainTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFE8CA))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) Color(0xFFD77D33) else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.title,
                    color = if (selected) Color.White else Color(0xFF6F4D34),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverviewScreen(
    state: HomeUiState,
    onSelectMonth: (YearMonth) -> Unit,
    onUpdateTransactionCategory: (Long, String, String) -> Unit,
    onDeleteTransaction: (Long) -> Unit
) {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf(OverviewTabMode.Daily) }
    var showMonthCalendar by rememberSaveable { mutableStateOf(false) }
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var editingCategoryTx by remember { mutableStateOf<TransactionEntity?>(null) }
    var deleteTx by remember { mutableStateOf<TransactionEntity?>(null) }
    var editParentExpanded by rememberSaveable { mutableStateOf(false) }
    var editChildExpanded by rememberSaveable { mutableStateOf(false) }
    var editParent by rememberSaveable { mutableStateOf("") }
    var editChild by rememberSaveable { mutableStateOf("") }
    var selectedAnomaly by remember { mutableStateOf<AnomalyInsight?>(null) }
    var selectedWeekStart by rememberSaveable { mutableStateOf(LocalDate.now().minusDays((LocalDate.now().dayOfWeek.value - 1).toLong())) }
    val selectedMonth = state.selectedMonth
    val today = remember { LocalDate.now() }

    val monthTransactions = state.monthTransactions
    val yearTransactions = state.yearTransactions
    val groupedCategories = remember(state.categories) { state.categories.groupBy { it.parent } }
    val txByDate = remember(yearTransactions) {
        yearTransactions.groupBy { Instant.ofEpochMilli(it.occurredAtEpochMs).atZone(zone).toLocalDate() }
    }
    val selectedDailyTransactions = remember(txByDate, selectedDate) {
        txByDate[selectedDate].orEmpty().sortedByDescending { it.occurredAtEpochMs }
    }
    val selectedDaySummary = remember(selectedDailyTransactions) { calculateSummary(selectedDailyTransactions) }

    val weekTransactions = remember(yearTransactions, selectedWeekStart) {
        val endExclusive = selectedWeekStart.plusDays(7)
        yearTransactions.filter {
            val d = Instant.ofEpochMilli(it.occurredAtEpochMs).atZone(zone).toLocalDate()
            d >= selectedWeekStart && d < endExclusive
        }
    }
    val weekSummary = remember(weekTransactions) { calculateSummary(weekTransactions) }
    val weekTrend = remember(weekTransactions, selectedWeekStart) { buildWeekLineTrend(weekTransactions, selectedWeekStart) }
    val weekCategoryShare = remember(weekTransactions) { buildCategoryShare(weekTransactions) }
    val weekReport = remember(weekTransactions, selectedWeekStart, yearTransactions) {
        val prevStart = selectedWeekStart.minusWeeks(1)
        val prevEnd = prevStart.plusDays(7)
        val prevWeekExpense = yearTransactions.asSequence()
            .filter { it.type == "EXPENSE" }
            .filter {
                val d = Instant.ofEpochMilli(it.occurredAtEpochMs).atZone(zone).toLocalDate()
                d >= prevStart && d < prevEnd
            }
            .sumOf { it.amountCents }
        buildReportSnapshot("周报", weekTransactions, prevWeekExpense)
    }

    val monthDailyTrend = remember(mode, showMonthCalendar, monthTransactions) {
        if (mode != OverviewTabMode.Monthly || showMonthCalendar) emptyList() else buildMonthLineTrend(monthTransactions, selectedMonth)
    }
    val monthCategoryShare = remember(mode, showMonthCalendar, monthTransactions) {
        if (mode != OverviewTabMode.Monthly || showMonthCalendar) emptyList()
        else buildCategoryShare(monthTransactions)
    }
    val yearMonthlyTrend = remember(mode, yearTransactions) {
        if (mode != OverviewTabMode.Yearly) emptyList() else buildYearLineTrend(yearTransactions)
    }
    val yearCategoryShare = remember(mode, yearTransactions) {
        if (mode != OverviewTabMode.Yearly) emptyList() else buildCategoryShare(yearTransactions)
    }
    val monthReport = remember(monthTransactions, state.previousMonthSummary.expenseCents) {
        buildReportSnapshot("${selectedMonth.monthValue}月报", monthTransactions, state.previousMonthSummary.expenseCents)
    }
    val yearReport = remember(yearTransactions, state.previousYearSummary.expenseCents) {
        buildReportSnapshot("${selectedMonth.year}年报", yearTransactions, state.previousYearSummary.expenseCents)
    }
    val focusInsights = remember(
        state.selectedMonthSummary.expenseCents,
        state.previousMonthSummary.expenseCents,
        state.monthBudgetCents
    ) {
        buildList {
            val currentExpense = state.selectedMonthSummary.expenseCents
            val previousExpense = state.previousMonthSummary.expenseCents
            if (previousExpense > 0L) {
                val delta = currentExpense - previousExpense
                val percent = (kotlin.math.abs(delta).toDouble() / previousExpense.toDouble() * 100.0).roundToInt()
                if (delta > 0L) add("较上月支出上升 $percent%，建议优先查看高频分类。")
                if (delta < 0L) add("较上月支出下降 $percent%，当前节奏值得保持。")
            }
            val budget = state.monthBudgetCents
            if (budget != null && budget > 0) {
                val ratio = currentExpense.toDouble() / budget.toDouble()
                when {
                    ratio >= 1.0 -> add("本月预算已超额，今天建议暂停非必要支出。")
                    ratio >= 0.8 -> add("预算已使用 ${(ratio * 100).roundToInt()}%，请关注接下来一周消费。")
                    else -> add("预算使用率 ${(ratio * 100).roundToInt()}%，仍在安全范围。")
                }
            } else {
                add("尚未设置本月预算，建议先设定一个可执行上限。")
            }
            if (isEmpty()) add("本期暂无明显风险，保持自动记账即可。")
        }.take(3)
    }
    val anomalies = remember(
        monthTransactions,
        state.previousMonthSummary.expenseCents,
        state.monthBudgetCents,
        state.selectedMonthCategoryBudgets
    ) {
        buildMonthlyAnomalies(
            monthTx = monthTransactions,
            previousMonthExpenseCents = state.previousMonthSummary.expenseCents,
            monthBudgetCents = state.monthBudgetCents,
            categoryBudgets = state.selectedMonthCategoryBudgets
        )
    }

    val monthByDate = remember(showMonthCalendar, monthTransactions) {
        if (!showMonthCalendar) emptyMap()
        else monthTransactions.groupBy { Instant.ofEpochMilli(it.occurredAtEpochMs).atZone(zone).toLocalDate() }
    }
    val monthDaySummary = remember(monthByDate) { calculateDaySummary(monthByDate) }
    val selectedDayTransactions = remember(monthByDate, selectedDate) {
        monthByDate[selectedDate].orEmpty().sortedByDescending { it.occurredAtEpochMs }
    }
    LaunchedEffect(selectedDate, today) {
        if (selectedDate.isAfter(today)) selectedDate = today
    }

    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OverviewModeSwitch(
                mode = mode,
                onModeChange = {
                    mode = it
                    if (it != OverviewTabMode.Monthly) showMonthCalendar = false
                }
            )
        }
        item { FocusInsightCard(lines = focusInsights) }
        if (anomalies.isNotEmpty()) {
            item {
                AnomalyRadarCard(
                    anomalies = anomalies,
                    onOpenDetail = { selectedAnomaly = it }
                )
            }
        }

        when (mode) {
            OverviewTabMode.Daily -> {
                item {
                    DaySwitcher(
                        selectedDate = selectedDate,
                        today = today,
                        onPrev = { selectedDate = selectedDate.minusDays(1) },
                        onNext = {
                            val next = selectedDate.plusDays(1)
                            if (!next.isAfter(today)) selectedDate = next
                        }
                    )
                }
                item {
                    SummaryCard(
                        if (selectedDate == today) "当日" else "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日",
                        selectedDaySummary.incomeCents,
                        selectedDaySummary.expenseCents,
                        selectedDaySummary.balanceCents,
                        highlight = true
                    )
                }
                if (state.monthBudgetCents != null && state.monthBudgetCents > 0) {
                    item { BudgetProgressCard(expense = state.monthly.expenseCents, budget = state.monthBudgetCents) }
                }
                item {
                    Text(
                        if (selectedDate == today) "当日流水" else "${selectedDate.format(DateTimeFormatter.ofPattern("MM-dd"))} 流水",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (selectedDailyTransactions.isEmpty()) item { GlassCard { Text("当天暂无流水") } }
                else items(selectedDailyTransactions, key = { it.id }, contentType = { "tx_row" }) { tx ->
                    TransactionRow(
                        tx = tx,
                        allowCategoryEdit = shouldAllowCategoryEdit(tx),
                        onEditCategory = {
                            editingCategoryTx = tx
                            editParent = tx.parentCategory
                            editChild = tx.childCategory
                        },
                        onDelete = { deleteTx = tx }
                    )
                }
            }

            OverviewTabMode.Weekly -> {
                item {
                    WeekSwitcher(
                        weekStart = selectedWeekStart,
                        onPrev = { selectedWeekStart = selectedWeekStart.minusWeeks(1) },
                        onNext = { selectedWeekStart = selectedWeekStart.plusWeeks(1) }
                    )
                }
                item {
                    SummaryCard(
                        title = "本周",
                        income = weekSummary.incomeCents,
                        expense = weekSummary.expenseCents,
                        balance = weekSummary.balanceCents,
                        highlight = true,
                        extraLines = reportSnapshotLines(weekReport),
                        actionText = "导出分享图",
                        onAction = { shareReportImage(context, weekReport) }
                    )
                }
                item { LineChartCard("周消费趋势", weekTrend) }
                item { PieChartCard("周支出结构", weekCategoryShare) }
            }

            OverviewTabMode.Monthly -> {
                item {
                    MonthSwitcher(
                        month = selectedMonth,
                        onPrev = {
                            onSelectMonth(selectedMonth.minusMonths(1))
                            showMonthCalendar = false
                        },
                        onNext = {
                            onSelectMonth(selectedMonth.plusMonths(1))
                            showMonthCalendar = false
                        }
                    )
                }
                item {
                    SummaryCard(
                        title = "${selectedMonth.year}年${selectedMonth.monthValue}月",
                        income = state.selectedMonthSummary.incomeCents,
                        expense = state.selectedMonthSummary.expenseCents,
                        balance = state.selectedMonthSummary.balanceCents,
                        highlight = true,
                        extraLines = reportSnapshotLines(monthReport),
                        actionText = "导出分享图",
                        onAction = { shareReportImage(context, monthReport) },
                        onClick = {
                            showMonthCalendar = true
                            selectedDate = LocalDate.now().takeIf { YearMonth.from(it) == selectedMonth } ?: selectedMonth.atDay(1)
                        }
                    )
                }
                if (showMonthCalendar) {
                    item { GlassCard { Text("返回月概览", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { showMonthCalendar = false }) } }
                    item { MonthCalendarCard(month = selectedMonth, selectedDate = selectedDate, dailySummary = monthDaySummary, onSelectDate = { selectedDate = it }) }
                    item { Text("${selectedDate.format(DateTimeFormatter.ofPattern("MM-dd"))} 流水", fontWeight = FontWeight.SemiBold) }
                    if (selectedDayTransactions.isEmpty()) item { GlassCard { Text("当天暂无流水") } }
                    else items(selectedDayTransactions, key = { it.id }, contentType = { "tx_row" }) { tx ->
                        TransactionRow(
                            tx = tx,
                            allowCategoryEdit = shouldAllowCategoryEdit(tx),
                            onEditCategory = {
                                editingCategoryTx = tx
                                editParent = tx.parentCategory
                                editChild = tx.childCategory
                            },
                            onDelete = { deleteTx = tx }
                        )
                    }
                } else {
                    item {
                        MonthlyComparisonCard(
                            month = selectedMonth,
                            currentExpenseCents = state.selectedMonthSummary.expenseCents,
                            previousExpenseCents = state.previousMonthSummary.expenseCents
                        )
                    }
                    item { LineChartCard("月消费趋势", monthDailyTrend) }
                    item { PieChartCard("月支出结构", monthCategoryShare) }
                }
            }

            OverviewTabMode.Yearly -> {
                item {
                    SummaryCard(
                        title = "当年",
                        income = state.yearly.incomeCents,
                        expense = state.yearly.expenseCents,
                        balance = state.yearly.balanceCents,
                        highlight = true,
                        extraLines = reportSnapshotLines(yearReport),
                        actionText = "导出分享图",
                        onAction = { shareReportImage(context, yearReport) },
                        onClick = { mode = OverviewTabMode.Monthly; showMonthCalendar = false }
                    )
                }
                item { LineChartCard("年消费趋势", yearMonthlyTrend) }
                item { PieChartCard("年支出结构", yearCategoryShare) }
            }
        }
    }
    if (editingCategoryTx != null) {
        AlertDialog(
            onDismissRequest = { editingCategoryTx = null },
            title = { Text("调整分类") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(expanded = editParentExpanded, onExpandedChange = { editParentExpanded = !editParentExpanded }) {
                        OutlinedTextField(
                            value = editParent,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
                            label = { Text("一级分类") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = editParentExpanded) }
                        )
                        ExposedDropdownMenu(expanded = editParentExpanded, onDismissRequest = { editParentExpanded = false }) {
                            groupedCategories.keys.sorted().forEach { parent ->
                                DropdownMenuItem(
                                    text = { Text(parent) },
                                    onClick = {
                                        editParent = parent
                                        editChild = groupedCategories[parent]?.firstOrNull()?.child.orEmpty()
                                        editParentExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    ExposedDropdownMenuBox(expanded = editChildExpanded, onExpandedChange = { editChildExpanded = !editChildExpanded }) {
                        OutlinedTextField(
                            value = editChild,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
                            label = { Text("二级分类") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = editChildExpanded) }
                        )
                        ExposedDropdownMenu(expanded = editChildExpanded, onDismissRequest = { editChildExpanded = false }) {
                            groupedCategories[editParent].orEmpty().forEach { c ->
                                DropdownMenuItem(text = { Text(c.child) }, onClick = { editChild = c.child; editChildExpanded = false })
                            }
                        }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { editingCategoryTx = null }) { Text("取消") } },
            confirmButton = {
                TextButton(onClick = {
                    val tx = editingCategoryTx ?: return@TextButton
                    if (editParent.isNotBlank() && editChild.isNotBlank()) {
                        onUpdateTransactionCategory(tx.id, editParent, editChild)
                    }
                    editingCategoryTx = null
                }) { Text("更新") }
            }
        )
    }
    if (deleteTx != null) {
        AlertDialog(
            onDismissRequest = { deleteTx = null },
            title = { Text("删除流水") },
            text = { Text("删除后不可恢复，确认删除这条记录吗？") },
            dismissButton = { TextButton(onClick = { deleteTx = null }) { Text("取消") } },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteTransaction(deleteTx!!.id)
                    deleteTx = null
                }) { Text("删除", color = Color(0xFFB23A30)) }
            }
        )
    }
    if (selectedAnomaly != null) {
        AnomalyDetailDialog(
            anomaly = selectedAnomaly!!,
            onDismiss = { selectedAnomaly = null }
        )
    }
}

@Composable
private fun OverviewModeSwitch(mode: OverviewTabMode, onModeChange: (OverviewTabMode) -> Unit) {
    val options = remember { OverviewTabMode.entries }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFE4C6)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { item ->
            val selected = item == mode
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (selected) Color(0xFFB9652E) else Color.Transparent).clickable { onModeChange(item) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = item.title, color = if (selected) Color.White else Color(0xFF6D4B2F), fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun MonthSwitcher(
    month: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onPrev) { Text("上月") }
            Text("${month.year}年${month.monthValue}月", fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = onNext) { Text("下月") }
        }
    }
}

@Composable
private fun WeekSwitcher(
    weekStart: LocalDate,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val weekEnd = weekStart.plusDays(6)
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onPrev) { Text("上周") }
            Text("${weekStart.monthValue}/${weekStart.dayOfMonth} - ${weekEnd.monthValue}/${weekEnd.dayOfMonth}", fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = onNext) { Text("下周") }
        }
    }
}

@Composable
private fun LineChartCard(title: String, points: List<TrendPoint>) {
    val animProgress by animateFloatAsState(
        targetValue = if (points.isEmpty()) 0f else 1f,
        animationSpec = tween(durationMillis = 900),
        label = "line_chart_progress"
    )
    GlassCard {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        if (points.isEmpty()) {
            Text("暂无数据")
            return@GlassCard
        }
        val max = points.maxOf { it.expenseCents }.coerceAtLeast(1L)
        val min = 0L
        val markers = remember(points) { buildXAxisMarkers(points) }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .background(Color(0xFFFFE8CC), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 12.dp)
        ) {
            val stepX = if (points.size <= 1) 0f else size.width / (points.size - 1)
            val pointsOffset = points.mapIndexed { index, p ->
                val x = index * stepX
                val ratio = p.expenseCents.toFloat() / max.toFloat()
                val y = size.height - size.height * ratio * animProgress
                Offset(x, y)
            }
            val activeCount = ((points.size - 1) * animProgress).roundToInt().coerceIn(0, pointsOffset.lastIndex)
            for (i in 0 until activeCount) {
                drawLine(
                    color = Color(0xFFD77D33),
                    start = pointsOffset[i],
                    end = pointsOffset[i + 1],
                    strokeWidth = 4f
                )
            }
            pointsOffset.take(activeCount + 1).forEach { point ->
                drawCircle(color = Color(0xFFB9652E), radius = 5f, center = point)
            }
            val valueLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#6F4D34")
                textSize = 24f
                textAlign = Paint.Align.CENTER
            }
            points.take(activeCount + 1).forEachIndexed { idx, point ->
                if (point.expenseCents > 0L) {
                    val pos = pointsOffset[idx]
                    drawContext.canvas.nativeCanvas.drawText(
                        MoneyFormat.fromCents(point.expenseCents),
                        pos.x,
                        (pos.y - 10f).coerceAtLeast(20f),
                        valueLabelPaint
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            markers.forEach { mark ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(mark.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(MoneyFormat.fromCents(mark.value), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text("最小值 ${MoneyFormat.fromCents(min)} · 最大值 ${MoneyFormat.fromCents(max)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PieChartCard(title: String, shares: List<CategoryShare>) {
    val palette = listOf(Color(0xFFD77D33), Color(0xFFE8A35E), Color(0xFFB9652E), Color(0xFFF0BF86), Color(0xFF9F5A29))
    val animProgress by animateFloatAsState(
        targetValue = if (shares.isEmpty()) 0f else 1f,
        animationSpec = tween(durationMillis = 900),
        label = "pie_chart_progress"
    )
    GlassCard {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        if (shares.isEmpty()) {
            Text("暂无分类数据")
            return@GlassCard
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            val diameter = size.minDimension * 0.72f
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            var startAngle = -90f
            shares.take(5).forEachIndexed { index, item ->
                val sweep = (item.ratio.coerceIn(0f, 1f)) * 360f * animProgress
                drawArc(
                    color = palette[index % palette.size],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = topLeft,
                    size = Size(diameter, diameter)
                )
                startAngle += sweep
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        shares.take(5).forEachIndexed { index, item ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(10.dp).height(10.dp).background(palette[index % palette.size], RoundedCornerShape(2.dp)))
                Spacer(modifier = Modifier.width(6.dp))
                Text("${item.name} ${(item.ratio * 100).toInt()}%  ${MoneyFormat.fromCents(item.amountCents)}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun TrendChartCard(title: String, points: List<TrendPoint>) {
    GlassCard {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        if (points.isEmpty()) {
            Text("暂无数据")
            return@GlassCard
        }
        val max = points.maxOf { it.expenseCents }.coerceAtLeast(1L)
        Canvas(
            modifier = Modifier.fillMaxWidth().height(120.dp).background(Color(0xFFFFE8CC), RoundedCornerShape(10.dp)).padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            val barWidth = size.width / points.size
            points.forEachIndexed { index, point ->
                val ratio = point.expenseCents.toFloat() / max.toFloat()
                val h = size.height * ratio
                drawRect(
                    color = Color(0xFFE58E42),
                    topLeft = Offset(index * barWidth + barWidth * 0.2f, size.height - h),
                    size = Size(barWidth * 0.6f, h)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = formatTrendRange(points.first().label, points.last().label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CategoryShareCard(title: String, shares: List<CategoryShare>) {
    GlassCard {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        if (shares.isEmpty()) {
            Text("暂无分类数据")
            return@GlassCard
        }
        shares.take(5).forEach { item ->
            Text("${item.name}  ${MoneyFormat.fromCents(item.amountCents)}  ${(item.ratio * 100).toInt()}%")
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF5D5AE))) {
                Box(modifier = Modifier.fillMaxWidth(item.ratio.coerceIn(0f, 1f)).height(8.dp).background(Color(0xFFDB7E36)))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordTab(
    state: HomeUiState,
    onAddTransaction: (String, Boolean, String, String, String, Long) -> Unit,
    onConfirmPendingAuto: (Long) -> Unit,
    onIgnorePendingAuto: (Long) -> Unit
) {
    val context = LocalContext.current
    var amount by rememberSaveable { mutableStateOf("") }
    var isIncome by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    var parentExpanded by rememberSaveable { mutableStateOf(false) }
    var childExpanded by rememberSaveable { mutableStateOf(false) }
    var parentCategory by rememberSaveable { mutableStateOf("") }
    var childCategory by rememberSaveable { mutableStateOf("") }
    var selectedTemplateLabel by rememberSaveable { mutableStateOf("") }
    var selectedRecordDate by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var amountError by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryError by rememberSaveable { mutableStateOf(false) }
    val templates = remember {
        listOf(
            RecordTemplate("早餐", "15", false, "生活", "餐饮", "早餐"),
            RecordTemplate("午饭", "35", false, "生活", "餐饮", "午饭"),
            RecordTemplate("咖啡", "18", false, "生活", "餐饮", "咖啡"),
            RecordTemplate("地铁", "4", false, "工作", "通勤", "通勤"),
            RecordTemplate("工资", "5000", true, "收入", "工资", "工资入账")
        )
    }
    val shownTemplates = remember(isIncome, templates) { templates.filter { it.isIncome == isIncome } }

    val grouped = remember(state.categories) { state.categories.groupBy { it.parent } }
    val parentOptions = remember(grouped, isIncome) {
        val keys = grouped.keys.toList().sorted()
        if (isIncome) keys.filter { it == "收入" }.ifEmpty { keys } else keys.filter { it != "收入" }.ifEmpty { keys }
    }
    val childOptions = remember(parentCategory, grouped) { grouped[parentCategory].orEmpty().map { it.child }.distinct().sorted() }
    val recentCategoryPairs = remember(state.monthTransactions, isIncome) {
        val targetType = if (isIncome) "INCOME" else "EXPENSE"
        state.monthTransactions.asSequence()
            .filter { it.type == targetType }
            .sortedByDescending { it.occurredAtEpochMs }
            .map { it.parentCategory to it.childCategory }
            .distinct()
            .take(4)
            .toList()
    }
    val quickAmounts = remember(isIncome) {
        if (isIncome) listOf("500", "1000", "3000", "5000") else listOf("10", "20", "30", "50", "100")
    }
    val canSubmit = remember(amount, parentCategory, childCategory) {
        val parsed = amount.toBigDecimalOrNull()
        parsed != null && parsed > BigDecimal.ZERO && parentCategory.isNotBlank() && childCategory.isNotBlank()
    }
    val today = remember { LocalDate.now() }

    LaunchedEffect(parentOptions) { if (parentCategory !in parentOptions) parentCategory = parentOptions.firstOrNull().orEmpty() }
    LaunchedEffect(childOptions) { if (childCategory !in childOptions) childCategory = childOptions.firstOrNull().orEmpty() }
    LaunchedEffect(isIncome) { selectedTemplateLabel = "" }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.pendingAutoTransactions.isNotEmpty()) {
            item {
                GlassCard {
                    Text("待确认自动记账", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("通知错过也没关系，可在这里确认或取消。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                state.pendingAutoTransactions.forEach { tx ->
                                    onIgnorePendingAuto(tx.id)
                                    NotificationManagerCompat.from(context).cancel(tx.id.toInt())
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("全部取消") }
                        Button(
                            onClick = {
                                state.pendingAutoTransactions.forEach { tx ->
                                    onConfirmPendingAuto(tx.id)
                                    NotificationManagerCompat.from(context).cancel(tx.id.toInt())
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("全部确认") }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    state.pendingAutoTransactions.take(5).forEach { tx ->
                        PendingTransactionRow(
                            tx = tx,
                            onConfirm = {
                                onConfirmPendingAuto(tx.id)
                                NotificationManagerCompat.from(context).cancel(tx.id.toInt())
                            },
                            onIgnore = {
                                onIgnorePendingAuto(tx.id)
                                NotificationManagerCompat.from(context).cancel(tx.id.toInt())
                            }
                        )
                    }
                    if (state.pendingAutoTransactions.size > 5) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("还有 ${state.pendingAutoTransactions.size - 5} 条待处理", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            GlassCard {
                Text("快速记账", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isIncome) Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("支出") }
                    else OutlinedButton(onClick = { isIncome = false }, modifier = Modifier.weight(1f)) { Text("支出") }
                    if (isIncome) Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("收入") }
                    else OutlinedButton(onClick = { isIncome = true }, modifier = Modifier.weight(1f)) { Text("收入") }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("模板记账", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                shownTemplates.chunked(2).forEach { rowItems ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEach { tpl ->
                            val selected = selectedTemplateLabel == tpl.label
                            if (selected) {
                                Button(
                                    onClick = {
                                        amount = tpl.amountYuan
                                        isIncome = tpl.isIncome
                                        parentCategory = tpl.parent
                                        childCategory = tpl.child
                                        note = tpl.note
                                        selectedTemplateLabel = tpl.label
                                        amountError = null
                                        categoryError = false
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text(tpl.label) }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        amount = tpl.amountYuan
                                        isIncome = tpl.isIncome
                                        parentCategory = tpl.parent
                                        childCategory = tpl.child
                                        note = tpl.note
                                        selectedTemplateLabel = tpl.label
                                        amountError = null
                                        categoryError = false
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text(tpl.label) }
                            }
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Text("快捷金额", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                quickAmounts.chunked(3).forEach { rowItems ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEach { quick ->
                            OutlinedButton(
                                onClick = { amount = quick; amountError = null },
                                modifier = Modifier.weight(1f)
                            ) { Text("¥$quick") }
                        }
                        repeat(3 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                if (recentCategoryPairs.isNotEmpty()) {
                    Text("最近分类", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    recentCategoryPairs.chunked(2).forEach { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { pair ->
                                OutlinedButton(
                                    onClick = {
                                        parentCategory = pair.first
                                        childCategory = pair.second
                                        categoryError = false
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("${pair.first}/${pair.second}", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                            if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it
                        if (amountError != null) amountError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("金额（元）") },
                    singleLine = true,
                    isError = amountError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = { amountError?.let { msg -> Text(msg) } }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(expanded = parentExpanded, onExpandedChange = { parentExpanded = !parentExpanded }) {
                    OutlinedTextField(
                        value = parentCategory,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
                        label = { Text("一级分类") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = parentExpanded) },
                        isError = categoryError && parentCategory.isBlank()
                    )
                    ExposedDropdownMenu(expanded = parentExpanded, onDismissRequest = { parentExpanded = false }) {
                        parentOptions.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    parentCategory = item
                                    childCategory = grouped[item]?.firstOrNull()?.child.orEmpty()
                                    categoryError = false
                                    parentExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(expanded = childExpanded, onExpandedChange = { childExpanded = !childExpanded }) {
                    OutlinedTextField(
                        value = childCategory,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
                        label = { Text("二级分类") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = childExpanded) },
                        isError = categoryError && childCategory.isBlank()
                    )
                    ExposedDropdownMenu(expanded = childExpanded, onDismissRequest = { childExpanded = false }) {
                        childOptions.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    childCategory = item
                                    categoryError = false
                                    childExpanded = false
                                }
                            )
                        }
                    }
                }
                if (categoryError) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("请选择完整分类", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text("备注（可选）") }, maxLines = 2)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val picked = LocalDate.of(year, month + 1, dayOfMonth)
                                if (!picked.isAfter(today)) selectedRecordDate = picked
                            },
                            selectedRecordDate.year,
                            selectedRecordDate.monthValue - 1,
                            selectedRecordDate.dayOfMonth
                        ).apply {
                            datePicker.maxDate = System.currentTimeMillis()
                        }.show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("日期：${selectedRecordDate.format(dateOnlyFormatter)}")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = {
                    val parsedAmount = amount.toBigDecimalOrNull()
                    if (parsedAmount == null || parsedAmount <= BigDecimal.ZERO) {
                        amountError = "请输入正确金额"
                        return@Button
                    }
                    if (parentCategory.isBlank() || childCategory.isBlank()) {
                        categoryError = true
                        return@Button
                    }
                    amountError = null
                    categoryError = false
                    onAddTransaction(
                        amount,
                        isIncome,
                        parentCategory,
                        childCategory,
                        note,
                        selectedRecordDate.atTime(LocalTime.now()).atZone(zone).toInstant().toEpochMilli()
                    )
                    amount = ""; note = ""
                    selectedTemplateLabel = ""
                    Toast.makeText(context, "保存成功", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth(), enabled = canSubmit) { Text("保存记录") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTab(
    state: HomeUiState,
    onAddCategory: (String, String) -> Unit,
    onSetMonthBudget: (String) -> Unit,
    onSetCategoryBudget: (String, String, String) -> Unit,
    onExportBackup: (onResult: (String) -> Unit, onError: (String) -> Unit) -> Unit,
    onImportBackup: (
        json: String,
        replaceExisting: Boolean,
        onResult: (Int, Int) -> Unit,
        onError: (String) -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var budget by rememberSaveable { mutableStateOf("") }
    var budgetError by rememberSaveable { mutableStateOf<String?>(null) }
    var newParent by rememberSaveable { mutableStateOf("") }
    var newChild by rememberSaveable { mutableStateOf("") }
    var budgetParentExpanded by rememberSaveable { mutableStateOf(false) }
    var budgetChildExpanded by rememberSaveable { mutableStateOf(false) }
    var budgetParent by rememberSaveable { mutableStateOf("") }
    var budgetChild by rememberSaveable { mutableStateOf("") }
    var categoryBudgetAmount by rememberSaveable { mutableStateOf("") }
    var backupJsonInput by rememberSaveable { mutableStateOf("") }
    var replaceExisting by rememberSaveable { mutableStateOf(false) }
    var autoBookHealthRefreshTick by rememberSaveable { mutableIntStateOf(0) }
    val autoBookHealth = remember(autoBookHealthRefreshTick) { getAutoBookHealthStatus(context) }
    val canUpdateBudget = remember(budget) {
        val parsed = budget.toBigDecimalOrNull()
        parsed != null && parsed > BigDecimal.ZERO
    }
    val grouped = remember(state.categories) { state.categories.groupBy { it.parent } }
    val budgetParentOptions = remember(grouped) { grouped.keys.sorted() }
    val budgetChildOptions = remember(budgetParent, grouped) { grouped[budgetParent].orEmpty().map { it.child }.distinct().sorted() }
    val canSetCategoryBudget = remember(budgetParent, budgetChild, categoryBudgetAmount) {
        budgetParent.isNotBlank() && budgetChild.isNotBlank() &&
            ((categoryBudgetAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO) > BigDecimal.ZERO)
    }
    val categoryBudgetUsage = remember(state.monthTransactions, state.selectedMonthCategoryBudgets) {
        val expenseMap = state.monthTransactions.asSequence()
            .filter { it.type == "EXPENSE" }
            .groupBy { it.parentCategory to it.childCategory }
            .mapValues { (_, txs) -> txs.sumOf { it.amountCents } }
        state.selectedMonthCategoryBudgets.map { budgetItem ->
            val used = expenseMap[budgetItem.parentCategory to budgetItem.childCategory] ?: 0L
            budgetItem to used
        }.sortedByDescending { (_, used) -> used }
    }
    LaunchedEffect(budgetParentOptions) {
        if (budgetParent !in budgetParentOptions) budgetParent = budgetParentOptions.firstOrNull().orEmpty()
    }
    LaunchedEffect(budgetChildOptions) {
        if (budgetChild !in budgetChildOptions) budgetChild = budgetChildOptions.firstOrNull().orEmpty()
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            GlassCard {
                Text("自动记账", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("通过监听支付消息通知自动提取交易金额。请先做一次状态体检。")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (autoBookHealth.healthy) "自动记账状态：可用" else "自动记账状态：待修复",
                    color = if (autoBookHealth.healthy) Color(0xFF2E7D32) else Color(0xFFB23A30),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                autoBookHealth.hints.forEach { hint ->
                    Text("• $hint", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        when (checkAndOpenNotificationListenerPermission(context)) {
                            ListenerPermissionActionResult.ALREADY_ENABLED -> Toast.makeText(context, "通知监听权限已开启", Toast.LENGTH_SHORT).show()
                            ListenerPermissionActionResult.OPENED_SETTINGS -> Toast.makeText(context, "请开启 Coin Nest 通知监听权限", Toast.LENGTH_SHORT).show()
                        }
                        autoBookHealthRefreshTick++
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD8894A),
                        contentColor = Color.White
                    )
                ) { Text("打开通知监听权限") }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        when (checkAndOpenPaymentNotificationSettings(context, WECHAT_PACKAGE_NAME)) {
                            PaymentNotifyActionResult.ALREADY_ENABLED -> Toast.makeText(context, "微信通知已开启", Toast.LENGTH_SHORT).show()
                            PaymentNotifyActionResult.OPENED_SETTINGS -> Toast.makeText(context, "请在微信通知设置页确认已开启支付通知", Toast.LENGTH_SHORT).show()
                            PaymentNotifyActionResult.APP_NOT_INSTALLED -> Toast.makeText(context, "未检测到微信", Toast.LENGTH_SHORT).show()
                        }
                        autoBookHealthRefreshTick++
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD8894A),
                        contentColor = Color.White
                    )
                ) { Text("打开微信支付通知") }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        when (checkAndOpenPaymentNotificationSettings(context, ALIPAY_PACKAGE_NAME)) {
                            PaymentNotifyActionResult.ALREADY_ENABLED -> Toast.makeText(context, "支付宝通知已开启", Toast.LENGTH_SHORT).show()
                            PaymentNotifyActionResult.OPENED_SETTINGS -> Toast.makeText(context, "请在支付宝通知设置页确认已开启支付通知", Toast.LENGTH_SHORT).show()
                            PaymentNotifyActionResult.APP_NOT_INSTALLED -> Toast.makeText(context, "未检测到支付宝", Toast.LENGTH_SHORT).show()
                        }
                        autoBookHealthRefreshTick++
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD8894A),
                        contentColor = Color.White
                    )
                ) { Text("打开支付宝支付通知") }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        try {
                            val pm = context.getSystemService(PowerManager::class.java)
                            if (pm != null && !pm.isIgnoringBatteryOptimizations(context.packageName)) {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${context.packageName}") }
                                context.startActivity(intent)
                                Toast.makeText(context, "请允许忽略电池优化", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "后台保活已开启", Toast.LENGTH_SHORT).show()
                            }
                        } catch (_: Exception) {
                            Toast.makeText(context, "打开失败", Toast.LENGTH_SHORT).show()
                        }
                        autoBookHealthRefreshTick++
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD8894A),
                        contentColor = Color.White
                    )
                ) { Text("允许后台保活（可选）") }
            }
        }
        item {
            GlassCard {
                Text("预算设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("当前预算：${state.monthBudgetCents?.let { MoneyFormat.fromCents(it) } ?: "未设置"}")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = budget,
                    onValueChange = {
                        budget = it
                        if (budgetError != null) budgetError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("本月预算（元）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = budgetError != null,
                    supportingText = { budgetError?.let { msg -> Text(msg) } }
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        val parsed = budget.toBigDecimalOrNull()
                        if (parsed == null || parsed <= BigDecimal.ZERO) {
                            budgetError = "请输入正确预算"
                            return@Button
                        }
                        onSetMonthBudget(budget)
                        budget = ""
                        Toast.makeText(context, "预算已更新", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canUpdateBudget
                ) { Text("更新预算") }
            }
        }
        item {
            GlassCard {
                Text("分类预算", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(expanded = budgetParentExpanded, onExpandedChange = { budgetParentExpanded = !budgetParentExpanded }) {
                    OutlinedTextField(
                        value = budgetParent,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
                        label = { Text("一级分类") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = budgetParentExpanded) }
                    )
                    ExposedDropdownMenu(expanded = budgetParentExpanded, onDismissRequest = { budgetParentExpanded = false }) {
                        budgetParentOptions.forEach { parent ->
                            DropdownMenuItem(
                                text = { Text(parent) },
                                onClick = {
                                    budgetParent = parent
                                    budgetChild = grouped[parent].orEmpty().firstOrNull()?.child.orEmpty()
                                    budgetParentExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(expanded = budgetChildExpanded, onExpandedChange = { budgetChildExpanded = !budgetChildExpanded }) {
                    OutlinedTextField(
                        value = budgetChild,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
                        label = { Text("二级分类") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = budgetChildExpanded) }
                    )
                    ExposedDropdownMenu(expanded = budgetChildExpanded, onDismissRequest = { budgetChildExpanded = false }) {
                        budgetChildOptions.forEach { child ->
                            DropdownMenuItem(
                                text = { Text(child) },
                                onClick = {
                                    budgetChild = child
                                    budgetChildExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = categoryBudgetAmount,
                    onValueChange = { categoryBudgetAmount = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("该分类预算（元）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        onSetCategoryBudget(budgetParent, budgetChild, categoryBudgetAmount)
                        Toast.makeText(context, "分类预算已更新", Toast.LENGTH_SHORT).show()
                        categoryBudgetAmount = ""
                    },
                    enabled = canSetCategoryBudget,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("保存分类预算") }
                if (categoryBudgetUsage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("本月分类预算进度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    categoryBudgetUsage.take(6).forEach { (item, used) ->
                        val ratio = if (item.limitCents <= 0) 0f else (used.toFloat() / item.limitCents.toFloat()).coerceAtLeast(0f)
                        Text("${item.parentCategory}/${item.childCategory}  ${MoneyFormat.fromCents(used)} / ${MoneyFormat.fromCents(item.limitCents)}")
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { ratio.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
        item {
            GlassCard {
                Text("新增二级分类", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = newParent, onValueChange = { newParent = it }, modifier = Modifier.fillMaxWidth(), label = { Text("一级分类") }, singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = newChild, onValueChange = { newChild = it }, modifier = Modifier.fillMaxWidth(), label = { Text("二级分类") }, singleLine = true)
                Spacer(modifier = Modifier.height(10.dp))
                Button(onClick = { onAddCategory(newParent, newChild); newParent = ""; newChild = "" }, modifier = Modifier.fillMaxWidth()) { Text("添加分类") }
            }
        }
        item {
            GlassCard {
                Text("数据安全中心（本地）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Text("导出后会复制到剪贴板；导入时粘贴备份 JSON 即可。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        onExportBackup(
                            { json ->
                                clipboardManager.setText(AnnotatedString(json))
                                Toast.makeText(context, "备份 JSON 已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            },
                            { error -> Toast.makeText(context, "导出失败：$error", Toast.LENGTH_SHORT).show() }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("导出并复制备份") }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = backupJsonInput,
                    onValueChange = { backupJsonInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("粘贴备份 JSON") },
                    minLines = 4,
                    maxLines = 8
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = replaceExisting, onCheckedChange = { replaceExisting = it })
                    Text("导入前清空现有数据（谨慎）", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        onImportBackup(
                            backupJsonInput,
                            replaceExisting,
                            { txCount, catCount ->
                                Toast.makeText(context, "导入完成：$txCount 笔记录，$catCount 个分类", Toast.LENGTH_SHORT).show()
                                backupJsonInput = ""
                            },
                            { error -> Toast.makeText(context, "导入失败：$error", Toast.LENGTH_SHORT).show() }
                        )
                    },
                    enabled = backupJsonInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("导入本地备份") }
            }
        }
    }
}

@Composable
private fun FocusInsightCard(lines: List<String>) {
    GlassCard {
        Text("本期结论", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        lines.forEach { line ->
            Text("• $line", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

@Composable
private fun AnomalyRadarCard(
    anomalies: List<AnomalyInsight>,
    onOpenDetail: (AnomalyInsight) -> Unit
) {
    GlassCard {
        Text("异常消费雷达", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        anomalies.forEach { anomaly ->
            val tagColor = when (anomaly.level) {
                "HIGH" -> Color(0xFFB23A30)
                "MEDIUM" -> Color(0xFFD8894A)
                else -> Color(0xFF6F4D34)
            }
            Text(anomaly.title, fontWeight = FontWeight.Medium, color = tagColor)
            Text(anomaly.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "查看异常详情",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable { onOpenDetail(anomaly) }
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun AnomalyDetailDialog(
    anomaly: AnomalyInsight,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
        title = {
            Column {
                Text(anomaly.title, fontWeight = FontWeight.SemiBold)
                Text(
                    "风险等级：${levelLabel(anomaly.level)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("异常说明", fontWeight = FontWeight.Medium)
                Text(anomaly.detail, style = MaterialTheme.typography.bodySmall)
                Text("触发规则：${anomaly.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Text("建议动作", fontWeight = FontWeight.Medium)
                anomaly.suggestions.forEach { action ->
                    Text("• $action", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (anomaly.relatedTransactions.isNotEmpty()) {
                    Text("相关记录（最多5条）", fontWeight = FontWeight.Medium)
                    anomaly.relatedTransactions.take(5).forEach { tx ->
                        val sign = if (tx.type == "INCOME") "+" else "-"
                        val time = Instant.ofEpochMilli(tx.occurredAtEpochMs).atZone(zone).format(rowTimeFormatter)
                        Text(
                            "$sign${MoneyFormat.fromCents(tx.amountCents)}  ${tx.parentCategory}/${tx.childCategory}  $time",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    )
}

private fun levelLabel(level: String): String {
    return when (level) {
        "HIGH" -> "高"
        "MEDIUM" -> "中"
        else -> "低"
    }
}

@Composable
private fun SummaryCard(
    title: String,
    income: Long,
    expense: Long,
    balance: Long,
    highlight: Boolean = false,
    extraLines: List<String> = emptyList(),
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val bg = if (highlight) Color(0xFFFFC98A) else Color(0xFFFFE1BC)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = if (onClick != null) modifier.clickable { onClick() } else modifier
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold)
                if (!actionText.isNullOrBlank() && onAction != null) {
                    OutlinedButton(onClick = onAction) { Text(actionText) }
                }
            }
            HorizontalDivider(color = Color(0xFF7B573A).copy(alpha = 0.25f))
            Text("收入：${MoneyFormat.fromCents(income)}")
            Text("支出：${MoneyFormat.fromCents(expense)}")
            Text("结余：${MoneyFormat.fromCents(balance)}", fontWeight = FontWeight.SemiBold)
            if (extraLines.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                extraLines.forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun BudgetProgressCard(expense: Long, budget: Long) {
    val ratio = if (budget <= 0) 0f else (expense.toFloat() / budget.toFloat()).coerceAtLeast(0f)
    val percent = (ratio * 100).toInt()
    val status = when {
        ratio >= 1f -> "已超额"
        ratio >= 0.8f -> "接近上限"
        else -> "正常"
    }
    GlassCard {
        Text("预算进度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        Text("本月支出：${MoneyFormat.fromCents(expense)} / ${MoneyFormat.fromCents(budget)}")
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { ratio.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text("使用率：$percent%（$status）")
    }
}

@Composable
private fun MonthlyComparisonCard(
    month: YearMonth,
    currentExpenseCents: Long,
    previousExpenseCents: Long
) {
    val delta = currentExpenseCents - previousExpenseCents
    val base = previousExpenseCents.coerceAtLeast(1L)
    val percent = kotlin.math.abs(delta).toFloat() / base.toFloat() * 100f
    val title = "${month.monthValue}月变化提醒"
    val trendText = when {
        previousExpenseCents <= 0L && currentExpenseCents <= 0L -> "本月与上月都暂无支出记录。"
        previousExpenseCents <= 0L -> "上月几乎无支出，本月为 ${MoneyFormat.fromCents(currentExpenseCents)}。"
        delta > 0L -> "较上月上升 ${percent.toInt()}%（+${MoneyFormat.fromCents(delta)}）"
        delta < 0L -> "较上月下降 ${percent.toInt()}%（${MoneyFormat.fromCents(delta)}）"
        else -> "与上月基本持平。"
    }
    val actionText = when {
        previousExpenseCents > 0L && delta > 0L && percent >= 30f -> "建议：本周先锁定高频支出分类，设一个日上限，避免继续放大。"
        previousExpenseCents > 0L && delta > 0L -> "建议：接下来几天优先记录大额消费，确认增长来源。"
        delta < 0L -> "建议：保持当前节奏，继续用模板或自动记账减少手动成本。"
        else -> "建议：保持记录频率，月底再复盘趋势。"
    }

    GlassCard {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        Text("本月支出：${MoneyFormat.fromCents(currentExpenseCents)}")
        Text("上月支出：${MoneyFormat.fromCents(previousExpenseCents)}")
        Spacer(modifier = Modifier.height(6.dp))
        Text(trendText, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(actionText, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DaySwitcher(
    selectedDate: LocalDate,
    today: LocalDate,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val canNext = selectedDate.isBefore(today)
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onPrev) { Text("上一天") }
            Text("${selectedDate.monthValue}/${selectedDate.dayOfMonth}", fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = onNext, enabled = canNext) { Text("下一天") }
        }
    }
}

@Composable
private fun MonthCalendarCard(
    month: YearMonth,
    selectedDate: LocalDate,
    dailySummary: Map<LocalDate, DayAmountSummary>,
    onSelectDate: (LocalDate) -> Unit
) {
    val cells = remember(month) {
        val firstDay = month.atDay(1)
        val offset = firstDay.dayOfWeek.value - 1
        val totalDays = month.lengthOfMonth()
        val cellCount = ((offset + totalDays + 6) / 7) * 7
        buildList<LocalDate?> {
            repeat(offset) { add(null) }
            for (day in 1..totalDays) add(month.atDay(day))
            repeat(cellCount - size) { add(null) }
        }
    }
    val weekNames = listOf("一", "二", "三", "四", "五", "六", "日")

    GlassCard {
        Text("${month.year}年${month.monthValue}月", fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            weekNames.forEach { name -> Text(text = name, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(modifier = Modifier.height(6.dp))
        for (weekIndex in cells.indices step 7) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (i in 0..6) {
                    val date = cells[weekIndex + i]
                    val summary = date?.let { dailySummary[it] } ?: DayAmountSummary()
                    CalendarCell(date = date, selected = date == selectedDate, summary = summary, modifier = Modifier.weight(1f), onClick = { if (date != null) onSelectDate(date) })
                }
            }
        }
    }
}

@Composable
private fun CalendarCell(
    date: LocalDate?,
    selected: Boolean,
    summary: DayAmountSummary,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val hasRecord = summary.incomeCents > 0 || summary.expenseCents > 0
    val cellBg = when {
        date == null -> Color.Transparent
        selected -> Color(0xFFF2B57A)
        hasRecord -> Color(0xFFFFE5C5)
        else -> Color.Transparent
    }
    Column(
        modifier = modifier.height(54.dp).padding(2.dp).background(cellBg, RoundedCornerShape(8.dp)).clickable(enabled = date != null, onClick = onClick).padding(horizontal = 4.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = date?.dayOfMonth?.toString().orEmpty(), style = MaterialTheme.typography.bodySmall)
        if (date != null && hasRecord) {
            Text(text = "支${summary.expenseCents / 100}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Spacer(modifier = Modifier.height(1.dp))
        }
    }
}

@Composable
private fun PendingTransactionRow(
    tx: TransactionEntity,
    onConfirm: () -> Unit,
    onIgnore: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEAD0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            val prefix = if (tx.type == "INCOME") "+" else "-"
            val txLabel = if (tx.type == "INCOME") "收入" else "支出"
            Text("待确认$txLabel $prefix${MoneyFormat.fromCents(tx.amountCents)}", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text("${tx.source}  ${tx.note}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onIgnore, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text("确认入账") }
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun TransactionRow(
    tx: TransactionEntity,
    allowCategoryEdit: Boolean = false,
    onEditCategory: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val prefix = if (tx.type == "INCOME") "+" else "-"
    val amountColor = if (tx.type == "INCOME") Color(0xFF2E7D32) else Color(0xFFB23A30)
    val sourceLabel = formatSourceLabel(tx.source)
    val timeText = remember(tx.occurredAtEpochMs) { Instant.ofEpochMilli(tx.occurredAtEpochMs).atZone(zone).format(rowTimeFormatter) }
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF2DE)), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = "$prefix${MoneyFormat.fromCents(tx.amountCents)}  ${tx.parentCategory}/${tx.childCategory}",
                fontWeight = FontWeight.SemiBold,
                color = amountColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text("来源：$sourceLabel  时间：$timeText")
            if (tx.note.isNotBlank()) {
                Text("备注：${tx.note}", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (allowCategoryEdit || onDelete != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (allowCategoryEdit && onEditCategory != null) {
                        Text(
                            "调整分类",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onEditCategory() }.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (onDelete != null) {
                        Text(
                            "删除",
                            color = Color(0xFFB23A30),
                            modifier = Modifier.clickable { onDelete() }.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF2DE)), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), content = content)
    }
}
