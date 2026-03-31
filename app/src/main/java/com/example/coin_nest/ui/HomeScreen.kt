package com.example.coin_nest.ui

import android.content.Intent
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.provider.MediaStore
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.coin_nest.data.db.TransactionEntity
import com.example.coin_nest.util.MoneyFormat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import android.provider.OpenableColumns

private enum class MainTab(val title: String) {
    Overview("概览"),
    Record("记账"),
    Import("导入"),
    Settings("设置")
}

private enum class OverviewTabMode(val title: String) {
    Daily("日"),
    Weekly("周"),
    Monthly("月"),
    Yearly("年")
}

private data class DayAmountSummary(
    val incomeCents: Long = 0L,
    val expenseCents: Long = 0L
) {
    val balanceCents: Long get() = incomeCents - expenseCents
}

private data class TrendPoint(val label: String, val expenseCents: Long)
private data class AxisMarker(val label: String, val value: Long)

private data class CategoryShare(
    val name: String,
    val amountCents: Long,
    val ratio: Float
)

private data class RecordTemplate(
    val label: String,
    val amountYuan: String,
    val isIncome: Boolean,
    val parent: String,
    val child: String,
    val note: String
)

private data class ReportSnapshot(
    val title: String,
    val totalExpenseCents: Long,
    val topCategoryName: String,
    val topCategoryExpenseCents: Long,
    val maxExpenseCents: Long,
    val maxExpenseLabel: String,
    val changeSummary: String
)

private data class ParsedImportItem(
    val occurredAtEpochMs: Long,
    val title: String,
    val amountYuan: String,
    val isIncome: Boolean,
    val source: String
)

private val zone: ZoneId = ZoneId.systemDefault()
private val rowTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

@Composable
fun HomeScreen(
    state: HomeUiState,
    initialMainTabIndex: Int = 0,
    onAddTransaction: (String, Boolean, String, String, String) -> Unit,
    onConfirmPendingAuto: (Long) -> Unit,
    onIgnorePendingAuto: (Long) -> Unit,
    onUpdateTransactionCategory: (Long, String, String) -> Unit,
    onDeleteTransaction: (Long) -> Unit,
    onExportBackupJson: (onResult: (String) -> Unit, onError: (String) -> Unit) -> Unit,
    onImportBackupJson: (json: String, replaceExisting: Boolean, onResult: (Int, Int) -> Unit, onError: (String) -> Unit) -> Unit,
    onImportTransactions: (drafts: List<ImportTransactionDraft>, onResult: (Int) -> Unit, onError: (String) -> Unit) -> Unit,
    onAddCategory: (String, String) -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onSetMonthBudget: (String) -> Unit,
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
                MainTab.Overview -> OverviewScreen(state, onSelectMonth, onUpdateTransactionCategory, onDeleteTransaction)
                MainTab.Record -> RecordTab(state, onAddTransaction, onConfirmPendingAuto, onIgnorePendingAuto)
                MainTab.Import -> ImportTab(state, onExportBackupJson, onImportBackupJson, onImportTransactions)
                MainTab.Settings -> SettingsTab(state, onAddCategory, onSetMonthBudget)
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
    var selectedWeekStart by rememberSaveable { mutableStateOf(LocalDate.now().minusDays((LocalDate.now().dayOfWeek.value - 1).toLong())) }
    val selectedMonth = state.selectedMonth

    val todayTransactions = state.todayTransactions
    val monthTransactions = state.monthTransactions
    val yearTransactions = state.yearTransactions
    val groupedCategories = remember(state.categories) { state.categories.groupBy { it.parent } }

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

    val monthByDate = remember(showMonthCalendar, monthTransactions) {
        if (!showMonthCalendar) emptyMap()
        else monthTransactions.groupBy { Instant.ofEpochMilli(it.occurredAtEpochMs).atZone(zone).toLocalDate() }
    }
    val monthDaySummary = remember(monthByDate) { calculateDaySummary(monthByDate) }
    val selectedDayTransactions = remember(monthByDate, selectedDate) {
        monthByDate[selectedDate].orEmpty().sortedByDescending { it.occurredAtEpochMs }
    }

    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { OverviewModeSwitch(mode = mode, onModeChange = { mode = it; if (it != OverviewTabMode.Monthly) showMonthCalendar = false }) }

        when (mode) {
            OverviewTabMode.Daily -> {
                item { SummaryCard("当日", state.daily.incomeCents, state.daily.expenseCents, state.daily.balanceCents, highlight = true) }
                if (state.monthBudgetCents != null && state.monthBudgetCents > 0) {
                    item { BudgetProgressCard(expense = state.monthly.expenseCents, budget = state.monthBudgetCents) }
                }
                item { Text("今日流水", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                if (todayTransactions.isEmpty()) item { GlassCard { Text("今天暂无流水") } }
                else items(todayTransactions, key = { it.id }, contentType = { "tx_row" }) { tx ->
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
    onAddTransaction: (String, Boolean, String, String, String) -> Unit,
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
    val canSubmit = remember(amount, parentCategory, childCategory) {
        val parsed = amount.toBigDecimalOrNull()
        parsed != null && parsed > BigDecimal.ZERO && parentCategory.isNotBlank() && childCategory.isNotBlank()
    }

    LaunchedEffect(parentOptions) { if (parentCategory !in parentOptions) parentCategory = parentOptions.firstOrNull().orEmpty() }
    LaunchedEffect(childOptions) { if (childCategory !in childOptions) childCategory = childOptions.firstOrNull().orEmpty() }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.pendingAutoTransactions.isNotEmpty()) {
            item {
                GlassCard {
                    Text("待确认自动记账", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("通知错过也没关系，可在这里确认或取消。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isIncome) "收入" else "支出")
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(checked = isIncome, onCheckedChange = { isIncome = it })
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("模板记账", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                shownTemplates.chunked(2).forEach { rowItems ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEach { tpl ->
                            OutlinedButton(
                                onClick = {
                                    amount = tpl.amountYuan
                                    isIncome = tpl.isIncome
                                    parentCategory = tpl.parent
                                    childCategory = tpl.child
                                    note = tpl.note
                                    amountError = null
                                    categoryError = false
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text(tpl.label) }
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
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
                    onAddTransaction(amount, isIncome, parentCategory, childCategory, note)
                    amount = ""; note = ""
                    Toast.makeText(context, "保存成功", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth(), enabled = canSubmit) { Text("保存记录") }
            }
        }
    }
}

@Composable
private fun SettingsTab(
    state: HomeUiState,
    onAddCategory: (String, String) -> Unit,
    onSetMonthBudget: (String) -> Unit
) {
    val context = LocalContext.current
    var budget by rememberSaveable { mutableStateOf("") }
    var budgetError by rememberSaveable { mutableStateOf<String?>(null) }
    var newParent by rememberSaveable { mutableStateOf("") }
    var newChild by rememberSaveable { mutableStateOf("") }
    val canUpdateBudget = remember(budget) {
        val parsed = budget.toBigDecimalOrNull()
        parsed != null && parsed > BigDecimal.ZERO
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            GlassCard {
                Text("自动记账", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("通过监听微信/支付宝通知自动提取消费金额，并在通知栏直接确认/取消。")
                Spacer(modifier = Modifier.height(10.dp))
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) { Text("打开通知监听权限") }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    try {
                        val pm = context.getSystemService(PowerManager::class.java)
                        if (pm != null && !pm.isIgnoringBatteryOptimizations(context.packageName)) {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${context.packageName}") }
                            context.startActivity(intent)
                        } else Toast.makeText(context, "已经忽略电池优化", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                        Toast.makeText(context, "打开失败", Toast.LENGTH_SHORT).show()
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("允许后台保活（可选）") }
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
                Text("新增二级分类", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = newParent, onValueChange = { newParent = it }, modifier = Modifier.fillMaxWidth(), label = { Text("一级分类") }, singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = newChild, onValueChange = { newChild = it }, modifier = Modifier.fillMaxWidth(), label = { Text("二级分类") }, singleLine = true)
                Spacer(modifier = Modifier.height(10.dp))
                Button(onClick = { onAddCategory(newParent, newChild); newParent = ""; newChild = "" }, modifier = Modifier.fillMaxWidth()) { Text("添加分类") }
            }
        }
    }
}

@Composable
private fun ImportTab(
    state: HomeUiState,
    onExportBackupJson: (onResult: (String) -> Unit, onError: (String) -> Unit) -> Unit,
    onImportBackupJson: (json: String, replaceExisting: Boolean, onResult: (Int, Int) -> Unit, onError: (String) -> Unit) -> Unit,
    onImportTransactions: (drafts: List<ImportTransactionDraft>, onResult: (Int) -> Unit, onError: (String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var recognizeLoading by rememberSaveable { mutableStateOf(false) }
    var recognizeText by rememberSaveable { mutableStateOf("") }
    var imageParsedItems by remember { mutableStateOf<List<ParsedImportItem>>(emptyList()) }
    var fileParsedItems by remember { mutableStateOf<List<ParsedImportItem>>(emptyList()) }
    var importReplaceMode by rememberSaveable { mutableStateOf(false) }
    var statementLoading by rememberSaveable { mutableStateOf(false) }
    var pendingExportJson by remember { mutableStateOf<String?>(null) }

    val exportFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = pendingExportJson
        if (uri == null || json == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
        }.onSuccess {
            Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
        }
        pendingExportJson = null
    }

    val importFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                InputStreamReader(stream).readText()
            }.orEmpty()
        }.onSuccess { json ->
            if (json.isBlank()) {
                Toast.makeText(context, "导入文件为空", Toast.LENGTH_SHORT).show()
                return@onSuccess
            }
            onImportBackupJson(
                json,
                importReplaceMode,
                { txCount, catCount ->
                    Toast.makeText(context, "导入完成：流水$txCount 条，分类$catCount 条", Toast.LENGTH_LONG).show()
                },
                { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            )
        }.onFailure {
            Toast.makeText(context, "读取导入文件失败", Toast.LENGTH_SHORT).show()
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        recognizeLoading = true
        recognizeBillText(
            context = context,
            uri = uri,
            onSuccess = { text ->
                recognizeLoading = false
                recognizeText = text.trim()
                imageParsedItems = parseImageBillItems(text)
                if (imageParsedItems.isEmpty()) {
                    Toast.makeText(context, "未识别到有效流水，请更换截图重试", Toast.LENGTH_SHORT).show()
                }
            },
            onError = {
                recognizeLoading = false
                Toast.makeText(context, "识别失败，请更换清晰截图", Toast.LENGTH_SHORT).show()
            }
        )
    }

    val statementPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        statementLoading = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { parseStatementFile(context, uri) } }
                .onSuccess { items -> fileParsedItems = items }
                .onFailure { fileParsedItems = emptyList() }
            statementLoading = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            GlassCard {
                Text("图片账单导入", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("支持从截图中提取多条流水：时间-项目-金额（总支出不会导入）。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (recognizeLoading) "识别中..." else "选择账单图片识别") }
                if (recognizeText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("识别条数：${imageParsedItems.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    imageParsedItems.take(6).forEach { item ->
                        Text("${formatEpoch(item.occurredAtEpochMs)}  ${item.title}  ${if (item.isIncome) "+" else "-"}${item.amountYuan}")
                    }
                    if (imageParsedItems.size > 6) {
                        Text("还有 ${imageParsedItems.size - 6} 条...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (imageParsedItems.isEmpty()) {
                                Toast.makeText(context, "无可导入流水", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val drafts = imageParsedItems.map {
                                ImportTransactionDraft(
                                    amountYuan = it.amountYuan,
                                    isIncome = it.isIncome,
                                    parentCategory = "待分类",
                                    childCategory = "自动识别",
                                    note = it.title.take(120),
                                    occurredAtEpochMs = it.occurredAtEpochMs,
                                    source = it.source
                                )
                            }
                            onImportTransactions(
                                drafts,
                                { count ->
                                    Toast.makeText(context, "已导入 $count 条流水", Toast.LENGTH_SHORT).show()
                                    imageParsedItems = emptyList()
                                    recognizeText = ""
                                },
                                { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !recognizeLoading && imageParsedItems.isNotEmpty()
                    ) { Text("导入图片识别流水") }
                }
            }
        }

        item {
            GlassCard {
                Text("Excel/CSV 账单导入", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("支持微信账单 Excel、支付宝交易 CSV。将按每条明细导入（跳过汇总行）。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { statementPickerLauncher.launch(arrayOf("text/csv", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (statementLoading) "解析中..." else "选择账单文件解析") }
                if (fileParsedItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("解析条数：${fileParsedItems.size}")
                    fileParsedItems.take(6).forEach { item ->
                        Text("${formatEpoch(item.occurredAtEpochMs)}  ${item.title}  ${if (item.isIncome) "+" else "-"}${item.amountYuan}")
                    }
                    if (fileParsedItems.size > 6) {
                        Text("还有 ${fileParsedItems.size - 6} 条...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val drafts = fileParsedItems.map {
                                ImportTransactionDraft(
                                    amountYuan = it.amountYuan,
                                    isIncome = it.isIncome,
                                    parentCategory = "待分类",
                                    childCategory = "自动识别",
                                    note = it.title.take(120),
                                    occurredAtEpochMs = it.occurredAtEpochMs,
                                    source = it.source
                                )
                            }
                            onImportTransactions(
                                drafts,
                                { count ->
                                    Toast.makeText(context, "已导入 $count 条流水", Toast.LENGTH_SHORT).show()
                                    fileParsedItems = emptyList()
                                },
                                { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("导入文件流水") }
                }
            }
        }

        item {
            GlassCard {
                Text("数据导入导出", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("当前流水 ${state.yearTransactions.size} 条（当年视图），建议定期导出备份。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        onExportBackupJson(
                            { json ->
                                pendingExportJson = json
                                exportFileLauncher.launch("coin_nest_backup_${System.currentTimeMillis()}.json")
                            },
                            { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("导出 JSON 备份") }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (importReplaceMode) "导入模式：覆盖现有数据" else "导入模式：合并数据")
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(checked = importReplaceMode, onCheckedChange = { importReplaceMode = it })
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { importFileLauncher.launch(arrayOf("application/json", "text/plain")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("选择 JSON 导入") }
            }
        }
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
            Text("待确认 ${MoneyFormat.fromCents(tx.amountCents)}", fontWeight = FontWeight.SemiBold)
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

private fun buildWeekLineTrend(weekTx: List<TransactionEntity>, weekStart: LocalDate): List<TrendPoint> {
    val expenseByDay = weekTx.asSequence()
        .filter { it.type == "EXPENSE" }
        .groupBy { Instant.ofEpochMilli(it.occurredAtEpochMs).atZone(zone).toLocalDate() }
        .mapValues { it.value.sumOf { tx -> tx.amountCents } }
    return (0..6).map { offset ->
        val day = weekStart.plusDays(offset.toLong())
        TrendPoint(label = "${day.monthValue}/${day.dayOfMonth}", expenseCents = expenseByDay[day] ?: 0L)
    }
}

private fun buildMonthLineTrend(monthTx: List<TransactionEntity>, month: YearMonth): List<TrendPoint> {
    if (monthTx.isEmpty()) return emptyList()
    val expenseByDay = monthTx.asSequence().filter { it.type == "EXPENSE" }.groupBy { Instant.ofEpochMilli(it.occurredAtEpochMs).atZone(zone).toLocalDate().dayOfMonth }.mapValues { it.value.sumOf { tx -> tx.amountCents } }
    val allDays = (1..month.lengthOfMonth()).toList()
    val maxBars = 15
    val step = kotlin.math.ceil(allDays.size / maxBars.toDouble()).toInt().coerceAtLeast(1)
    return allDays
        .chunked(step)
        .map { chunk ->
            val label = chunk.first().toString()
            val sum = chunk.sumOf { day -> expenseByDay[day] ?: 0L }
            TrendPoint(label = label, expenseCents = sum)
        }
}

private fun buildYearLineTrend(yearTx: List<TransactionEntity>): List<TrendPoint> {
    if (yearTx.isEmpty()) return emptyList()
    val expenseByMonth = yearTx.asSequence().filter { it.type == "EXPENSE" }.groupBy { Instant.ofEpochMilli(it.occurredAtEpochMs).atZone(zone).toLocalDate().monthValue }.mapValues { it.value.sumOf { tx -> tx.amountCents } }
    return (1..12).map { month -> TrendPoint(label = "${month}月", expenseCents = expenseByMonth[month] ?: 0L) }
}

private fun buildCategoryShare(txs: List<TransactionEntity>): List<CategoryShare> {
    val expenseTx = txs.filter { it.type == "EXPENSE" }
    if (expenseTx.isEmpty()) return emptyList()
    val total = expenseTx.sumOf { it.amountCents }.coerceAtLeast(1L)
    return expenseTx.groupBy { it.parentCategory }.map { (name, list) ->
        val amount = list.sumOf { it.amountCents }
        CategoryShare(name = name, amountCents = amount, ratio = amount.toFloat() / total.toFloat())
    }.sortedByDescending { it.amountCents }
}

private fun calculateDaySummary(map: Map<LocalDate, List<TransactionEntity>>): Map<LocalDate, DayAmountSummary> {
    return map.mapValues { (_, txs) ->
        var income = 0L
        var expense = 0L
        txs.forEach { tx -> if (tx.type == "INCOME") income += tx.amountCents else expense += tx.amountCents }
        DayAmountSummary(incomeCents = income, expenseCents = expense)
    }
}

private fun calculateSummary(txs: List<TransactionEntity>): DayAmountSummary {
    var income = 0L
    var expense = 0L
    txs.forEach { tx -> if (tx.type == "INCOME") income += tx.amountCents else expense += tx.amountCents }
    return DayAmountSummary(incomeCents = income, expenseCents = expense)
}

private fun shouldAllowCategoryEdit(tx: TransactionEntity): Boolean {
    val isAutoSource = tx.source.equals("ALIPAY", true) || tx.source.equals("WECHAT", true)
    val isPendingCategory = tx.parentCategory == "待分类" || tx.childCategory == "自动识别"
    return isAutoSource || isPendingCategory
}

private fun buildXAxisMarkers(points: List<TrendPoint>): List<AxisMarker> {
    if (points.isEmpty()) return emptyList()
    if (points.size <= 7) return points.map { AxisMarker(it.label, it.expenseCents) }

    val indexes = when {
        points.size <= 12 -> {
            val base = (0..points.lastIndex step 2).toMutableList()
            if (base.last() != points.lastIndex) base.add(points.lastIndex)
            base
        }
        else -> {
            listOf(0, points.size / 5, points.size * 2 / 5, points.size * 3 / 5, points.size * 4 / 5, points.lastIndex)
        }
    }.distinct()
    return indexes.map { idx -> AxisMarker(points[idx].label, points[idx].expenseCents) }
}

private fun reportSnapshotLines(snapshot: ReportSnapshot): List<String> {
    return listOf(
        "Top 分类：${snapshot.topCategoryName}（${MoneyFormat.fromCents(snapshot.topCategoryExpenseCents)}）",
        "最大单笔：${MoneyFormat.fromCents(snapshot.maxExpenseCents)} · ${snapshot.maxExpenseLabel}",
        snapshot.changeSummary
    )
}

private fun buildReportSnapshot(
    title: String,
    txs: List<TransactionEntity>,
    previousExpenseCents: Long
): ReportSnapshot {
    val expenseTx = txs.filter { it.type == "EXPENSE" }
    val totalExpense = expenseTx.sumOf { it.amountCents }
    val topEntry = expenseTx.groupBy { it.parentCategory }
        .mapValues { (_, list) -> list.sumOf { it.amountCents } }
        .maxByOrNull { it.value }
    val maxTx = expenseTx.maxByOrNull { it.amountCents }
    val delta = totalExpense - previousExpenseCents
    val ratio = if (previousExpenseCents > 0L) kotlin.math.abs(delta).toFloat() / previousExpenseCents.toFloat() * 100f else 0f
    val changeSummary = when {
        previousExpenseCents <= 0L && totalExpense <= 0L -> "与上一周期相比：暂无有效支出数据。"
        previousExpenseCents <= 0L -> "与上一周期相比：新增支出 ${MoneyFormat.fromCents(totalExpense)}。"
        delta > 0L -> "与上一周期相比：上升 ${ratio.toInt()}%（+${MoneyFormat.fromCents(delta)}）"
        delta < 0L -> "与上一周期相比：下降 ${ratio.toInt()}%（${MoneyFormat.fromCents(delta)}）"
        else -> "与上一周期相比：基本持平。"
    }
    val maxLabel = maxTx?.let {
        val d = Instant.ofEpochMilli(it.occurredAtEpochMs).atZone(zone).toLocalDate()
        "${it.parentCategory}/${it.childCategory} ${d.monthValue}/${d.dayOfMonth}"
    } ?: "无"
    return ReportSnapshot(
        title = title,
        totalExpenseCents = totalExpense,
        topCategoryName = topEntry?.key ?: "无",
        topCategoryExpenseCents = topEntry?.value ?: 0L,
        maxExpenseCents = maxTx?.amountCents ?: 0L,
        maxExpenseLabel = maxLabel,
        changeSummary = changeSummary
    )
}

private fun shareReportImage(context: android.content.Context, snapshot: ReportSnapshot) {
    val width = 1080
    val height = 1350
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(AndroidColor.parseColor("#FFF7EA"))

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#7A3B18")
        textSize = 64f
        isFakeBoldText = true
    }
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#4B3424")
        textSize = 42f
    }
    val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#8B6B51")
        textSize = 34f
    }
    val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#FFE6C4")
    }

    canvas.drawRoundRect(48f, 48f, width - 48f, height - 48f, 30f, 30f, blockPaint)
    canvas.drawText("Coin Nest ${snapshot.title}", 96f, 170f, titlePaint)
    canvas.drawText("总支出：${MoneyFormat.fromCents(snapshot.totalExpenseCents)}", 96f, 300f, bodyPaint)
    canvas.drawText("Top 分类：${snapshot.topCategoryName}", 96f, 390f, bodyPaint)
    canvas.drawText("分类金额：${MoneyFormat.fromCents(snapshot.topCategoryExpenseCents)}", 96f, 470f, bodyPaint)
    canvas.drawText("最大单笔：${MoneyFormat.fromCents(snapshot.maxExpenseCents)}", 96f, 560f, bodyPaint)
    canvas.drawText(snapshot.maxExpenseLabel, 96f, 640f, hintPaint)
    canvas.drawText(snapshot.changeSummary, 96f, 760f, bodyPaint)
    canvas.drawText("记录越轻松，洞察越清晰", 96f, 1180f, hintPaint)

    val dir = File(context.cacheDir, "shares")
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, "coin_nest_report_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(sendIntent, "分享报告"))
}

private fun formatTrendRange(first: String, last: String): String {
    return "$first - $last"
}

private fun formatSourceLabel(source: String): String {
    return when (source.uppercase()) {
        "MANUAL" -> "手动记账"
        "ALIPAY" -> "支付宝自动识别"
        "WECHAT" -> "微信自动识别"
        "CREDIT_CARD" -> "信用卡账单识别"
        "UNKNOWN" -> "图片识别"
        else -> source
    }
}

private fun recognizeBillText(
    context: android.content.Context,
    uri: Uri,
    onSuccess: (String) -> Unit,
    onError: (Throwable) -> Unit
) {
    runCatching {
        val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { result -> onSuccess(result.text) }
            .addOnFailureListener { onError(it) }
    }.onFailure(onError)
}

private fun inferSourceFromBillText(text: String): String {
    val t = text.lowercase()
    return when {
        t.contains("支付宝") || t.contains("alipay") -> "ALIPAY"
        t.contains("微信") || t.contains("wechat") -> "WECHAT"
        t.contains("信用卡") || t.contains("card") || t.contains("visa") || t.contains("mastercard") -> "CREDIT_CARD"
        else -> "UNKNOWN"
    }
}

private fun parseImageBillItems(text: String): List<ParsedImportItem> {
    val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
    if (lines.isEmpty()) return emptyList()

    val dateHeaderRegex = Regex("(\\d{1,2})月(\\d{1,2})日")
    val fullDateRegex = Regex("(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})\\s*(\\d{1,2}:\\d{2})?")
    val timeRegex = Regex("(\\d{1,2}:\\d{2})")
    val amountRegex = Regex("([+\\-−]?\\s*[¥￥]?\\s*\\d+(?:[\\.,]\\d{1,2})?)")
    val yearNow = LocalDate.now().year
    var currentDate = LocalDate.now()
    val source = inferSourceFromBillText(text)

    val items = mutableListOf<ParsedImportItem>()
    for (i in lines.indices) {
        val line = lines[i]
        dateHeaderRegex.find(line)?.let { m ->
            currentDate = LocalDate.of(yearNow, m.groupValues[1].toInt(), m.groupValues[2].toInt())
        }
        fullDateRegex.find(line)?.let { m ->
            val y = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            val d = m.groupValues[3].toInt()
            currentDate = LocalDate.of(y, mo, d)
        }

        if (line.contains("总支出") || line.contains("总收入") || line.contains("收支统计")) continue
        if (line.contains("支出¥") || line.contains("收入¥")) continue
        if (line.contains("可用") || line.contains("剩余可用额度")) continue

        val amountMatch = amountRegex.findAll(line).toList().lastOrNull() ?: continue
        val rawAmount = amountMatch.groupValues[1]
        val amount = parseAmount(rawAmount) ?: continue
        if (amount <= 0.0 || amount > 500_000.0) continue

        val isIncome = rawAmount.contains("+") || line.contains("退款") || line.contains("收入") || line.contains("收款")
        val amountYuan = BigDecimal(amount.toString()).stripTrailingZeros().toPlainString()
        val titleFromLine = line.replace(rawAmount, "").replace("¥", "").replace("￥", "").trim().trim('-', '−', ':')
        val title = when {
            titleFromLine.length >= 2 && !titleFromLine.contains("月") -> titleFromLine
            i > 0 && lines[i - 1].length >= 2 && !lines[i - 1].contains("尾号") -> lines[i - 1]
            else -> "图片导入"
        }.take(60)

        val time = timeRegex.find(line)?.groupValues?.get(1)
            ?: lines.getOrNull(i + 1)?.let { next -> timeRegex.find(next)?.groupValues?.get(1) }
            ?: "12:00"
        val occurredAt = localDateAndTimeToEpoch(currentDate, time)

        items += ParsedImportItem(
            occurredAtEpochMs = occurredAt,
            title = title,
            amountYuan = amountYuan,
            isIncome = isIncome,
            source = if (source == "UNKNOWN") "IMAGE_IMPORT" else source
        )
    }
    return items.distinctBy { "${it.occurredAtEpochMs}_${it.title}_${it.amountYuan}" }
}

private fun parseStatementFile(context: android.content.Context, uri: Uri): List<ParsedImportItem> {
    val displayName = queryDisplayName(context, uri).lowercase()
    return when {
        displayName.endsWith(".csv") -> {
            val text = context.contentResolver.openInputStream(uri)?.use { InputStreamReader(it).readText() }.orEmpty()
            parseStatementRows(parseCsvToRows(text), "CSV_IMPORT")
        }
        displayName.endsWith(".xlsx") -> {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val workbook = XSSFWorkbook(stream)
                workbook.use { wb ->
                    val sheet = wb.getSheetAt(0)
                    val rows = mutableListOf<List<String>>()
                    for (r in 0..sheet.lastRowNum) {
                        val row = sheet.getRow(r) ?: continue
                        val maxCell = row.lastCellNum.toInt().coerceAtLeast(0)
                        val cols = mutableListOf<String>()
                        for (c in 0 until maxCell) {
                            cols += row.getCell(c)?.toString()?.trim().orEmpty()
                        }
                        rows += cols
                    }
                    parseStatementRows(rows, "EXCEL_IMPORT")
                }
            }.orEmpty()
        }
        else -> {
            val text = context.contentResolver.openInputStream(uri)?.use { InputStreamReader(it).readText() }.orEmpty()
            parseStatementRows(parseCsvToRows(text), "FILE_IMPORT")
        }
    }
}

private fun parseStatementRows(rows: List<List<String>>, fallbackSource: String): List<ParsedImportItem> {
    if (rows.isEmpty()) return emptyList()
    val headerIndex = rows.indexOfFirst { row ->
        val merged = row.joinToString("|")
        merged.contains("交易时间") || merged.contains("交易创建时间") || merged.contains("收/支") || merged.contains("金额")
    }
    if (headerIndex < 0 || headerIndex >= rows.lastIndex) return emptyList()

    val header = rows[headerIndex].map { it.trim() }
    val items = mutableListOf<ParsedImportItem>()
    for (i in (headerIndex + 1) until rows.size) {
        val row = rows[i]
        if (row.isEmpty()) continue
        val map = mutableMapOf<String, String>()
        header.forEachIndexed { idx, key -> map[key] = row.getOrNull(idx)?.trim().orEmpty() }

        val timeText = firstNotBlank(
            map["交易时间"],
            map["交易创建时间"],
            map["付款时间"],
            map["完成时间"],
            map["时间"]
        )
        val title = firstNotBlank(
            map["商品"],
            map["商品名称"],
            map["交易对方"],
            map["交易对方名称"],
            map["交易描述"],
            map["备注"]
        ).ifBlank { "文件导入" }

        if (title.contains("总支出") || title.contains("总收入")) continue

        val amountText = firstNotBlank(
            map["金额(元)"],
            map["金额（元）"],
            map["金额"],
            map["收/支金额"]
        )
        val amount = parseAmount(amountText) ?: continue
        if (amount <= 0.0) continue

        val inOut = firstNotBlank(map["收/支"], map["资金流向"], map["类型"], map["交易类型"]).lowercase()
        val isIncome = amountText.contains("+") || inOut.contains("收入") || inOut.contains("入账") || inOut.contains("退款")

        val status = firstNotBlank(map["当前状态"], map["交易状态"], map["状态"]).lowercase()
        if (status.isNotBlank() && !(status.contains("成功") || status.contains("已完成") || status.contains("入账中"))) {
            continue
        }

        val sourceHint = firstNotBlank(map["交易来源"], map["支付方式"])
        val source = when {
            sourceHint.contains("微信") || title.contains("微信") -> "WECHAT"
            sourceHint.contains("支付宝") || title.contains("支付宝") -> "ALIPAY"
            title.contains("信用卡") -> "CREDIT_CARD"
            else -> fallbackSource
        }
        val occurredAt = parseDateTimeToEpoch(timeText) ?: System.currentTimeMillis()
        items += ParsedImportItem(
            occurredAtEpochMs = occurredAt,
            title = title.take(80),
            amountYuan = BigDecimal(amount.toString()).stripTrailingZeros().toPlainString(),
            isIncome = isIncome,
            source = source
        )
    }
    return items
}

private fun parseCsvToRows(text: String): List<List<String>> {
    return text.lines()
        .map { it.trim('\uFEFF') }
        .filter { it.isNotBlank() }
        .map { splitCsvLine(it) }
}

private fun splitCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    val sb = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val ch = line[i]
        when {
            ch == '"' -> {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    sb.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            }
            ch == ',' && !inQuotes -> {
                result += sb.toString().trim()
                sb.clear()
            }
            else -> sb.append(ch)
        }
        i++
    }
    result += sb.toString().trim()
    return result
}

private fun parseDateTimeToEpoch(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    val s = raw.trim()
    val dtPatterns = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy/M/d HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy/M/d HH:mm"),
        DateTimeFormatter.ofPattern("yyyy.M.d HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy.M.d HH:mm")
    )
    dtPatterns.forEach { fmt ->
        runCatching {
            val dt = LocalDateTime.parse(s, fmt)
            return dt.atZone(zone).toInstant().toEpochMilli()
        }
    }
    return null
}

private fun parseAmount(raw: String?): Double? {
    if (raw.isNullOrBlank()) return null
    val cleaned = raw.replace("¥", "").replace("￥", "").replace(",", ".").replace(" ", "").trim()
    val regex = Regex("[+\\-−]?\\d+(?:\\.\\d{1,2})?")
    val value = regex.find(cleaned)?.value ?: return null
    return value.replace("−", "-").toDoubleOrNull()?.let { kotlin.math.abs(it) }
}

private fun localDateAndTimeToEpoch(date: LocalDate, timeHHmm: String): Long {
    val time = runCatching { LocalTime.parse(timeHHmm, DateTimeFormatter.ofPattern("H:mm")) }.getOrElse { LocalTime.NOON }
    return LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()
}

private fun firstNotBlank(vararg values: String?): String {
    return values.firstOrNull { !it.isNullOrBlank() }?.orEmpty() ?: ""
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String {
    val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            return it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)).orEmpty()
        }
    }
    return ""
}

private fun formatEpoch(epochMs: Long): String {
    return Instant.ofEpochMilli(epochMs).atZone(zone).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
}


