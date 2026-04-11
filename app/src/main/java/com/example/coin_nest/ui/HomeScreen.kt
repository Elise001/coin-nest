package com.example.coin_nest.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.coin_nest.data.db.TransactionEntity
import com.example.coin_nest.util.MoneyFormat
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

internal val SuccessColor = Color(0xFF2E7D32)
internal val DangerColor = Color(0xFFB23A30)
internal val WarningColor = Color(0xFFD8894A)

@Composable
fun HomeScreen(
    state: HomeUiState,
    initialMainTabIndex: Int = 0,
    onAddTransaction: (String, Boolean, String, String, String, Long) -> Unit,
    onConfirmPendingAuto: (Long) -> Unit,
    onIgnorePendingAuto: (Long) -> Unit,
    onUpdateTransactionCategory: (Long, String, String) -> Unit,
    onDeleteTransaction: (Long) -> Unit,
    onLoadMoreMonthTransactions: () -> Unit,
    onLoadMoreYearTransactions: () -> Unit,
    onAddCategory: (String, String) -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onSetMonthBudget: (String) -> Unit,
    onSetCategoryBudget: (String, String, String) -> Unit,
    onExportBackup: (onResult: (String) -> Unit, onError: (String) -> Unit) -> Unit,
    onClearSmartRules: () -> Unit,
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
    val pageBackground = MaterialTheme.colorScheme.background
    LaunchedEffect(initialMainTabIndex) {
        selectedMainTab = initialMainTabIndex.coerceIn(0, mainTabs.size - 1)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(pageBackground)
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
        Spacer(modifier = Modifier.height(6.dp))

        Box(modifier = Modifier.weight(1f)) {
            when (mainTabs[selectedMainTab]) {
                MainTab.Home -> HomeDashboardTab(
                    state = state,
                    onOpenInsight = { selectedMainTab = MainTab.Insight.ordinal }
                )
                MainTab.Record -> RecordTab(state, onAddTransaction, onConfirmPendingAuto, onIgnorePendingAuto)
                MainTab.Insight -> InsightTab(
                    state = state,
                    onSelectMonth = onSelectMonth,
                    onUpdateTransactionCategory = onUpdateTransactionCategory,
                    onDeleteTransaction = onDeleteTransaction,
                    onLoadMoreMonthTransactions = onLoadMoreMonthTransactions,
                    onLoadMoreYearTransactions = onLoadMoreYearTransactions
                )
                MainTab.Profile -> SettingsTab(
                    state = state,
                    onAddCategory = onAddCategory,
                    onSetMonthBudget = onSetMonthBudget,
                    onSetCategoryBudget = onSetCategoryBudget,
                    onExportBackup = onExportBackup,
                    onClearSmartRules = onClearSmartRules,
                    onImportBackup = onImportBackup
                )
            }
        }
        BottomMainTabs(
            tabs = mainTabs,
            selectedIndex = selectedMainTab,
            onSelect = { selectedMainTab = it }
        )
    }
}

@Composable
private fun BottomMainTabs(
    tabs: List<MainTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            val interactionSource = remember { MutableInteractionSource() }
            val pressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (pressed) 0.985f else 1f,
                animationSpec = tween(durationMillis = 120),
                label = "tab_press_scale"
            )
            val alpha by animateFloatAsState(
                targetValue = if (pressed) 0.92f else 1f,
                animationSpec = tween(durationMillis = 120),
                label = "tab_press_alpha"
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) { onSelect(index) }
                    .padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(if (selected) 18.dp else 8.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.95f) else Color.Transparent)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = tab.title,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.95f),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun HomeDashboardTab(
    state: HomeUiState,
    onOpenInsight: () -> Unit
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
    val keyAnomalies = remember(anomalies) { anomalies.take(3) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SummaryCard(
                title = "本期结论",
                income = state.selectedMonthSummary.incomeCents,
                expense = state.selectedMonthSummary.expenseCents,
                balance = state.selectedMonthSummary.balanceCents,
                highlight = true
            )
        }
        item {
            val budget = state.monthBudgetCents
            if (budget != null && budget > 0L) {
                BudgetProgressCard(
                    expense = state.selectedMonthSummary.expenseCents,
                    budget = budget,
                    month = state.selectedMonth
                )
            } else {
                GlassCard(tone = GlassCardTone.Warning) {
                    Text("预算风险", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("尚未设置预算，建议先在“我的”页设置本月预算。")
                }
            }
        }
        item {
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("今天流水", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "查看洞察 >",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onOpenInsight() }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (state.todayTransactions.isEmpty()) {
                    Text(
                        "今天还没有记录，记一笔会更清楚掌握消费节奏。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    state.todayTransactions
                        .sortedByDescending { it.occurredAtEpochMs }
                        .take(3)
                        .forEachIndexed { index, tx ->
                            TransactionRow(tx = tx)
                            if (index != 2) Spacer(modifier = Modifier.height(8.dp))
                        }
                }
            }
        }
        item {
            GlassCard {
                Text("异常提醒", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                if (keyAnomalies.isEmpty()) {
                    Text("本月暂无明显异常，保持当前消费节奏。")
                } else {
                    keyAnomalies.forEach { anomaly ->
                        Text("• ${anomaly.title}", fontWeight = FontWeight.Medium)
                        Text(
                            anomaly.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        "查看完整洞察",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable { onOpenInsight() }
                    )
                }
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
    val bg = if (highlight) MaterialTheme.colorScheme.secondary.copy(alpha = 0.33f) else MaterialTheme.colorScheme.surface
    val balanceColor = when {
        balance > 0L -> Color(0xFF2E7D32)
        balance < 0L -> Color(0xFFB23A30)
        else -> MaterialTheme.colorScheme.onSurface
    }
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            Text(
                "结余：${MoneyFormat.fromCents(balance)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = balanceColor,
                fontFamily = FontFamily.Monospace
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("收入：${MoneyFormat.fromCents(income)}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                Text("支出：${MoneyFormat.fromCents(expense)}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
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
private fun BudgetProgressCard(expense: Long, budget: Long, month: YearMonth) {
    val ratio = if (budget <= 0) 0f else (expense.toFloat() / budget.toFloat()).coerceAtLeast(0f)
    val percent = (ratio * 100).toInt()
    val (status, statusColor) = when {
        ratio >= 1f -> "已超额" to DangerColor
        ratio >= 0.8f -> "接近上限" to WarningColor
        else -> "正常" to SuccessColor
    }
    val currentMonth = YearMonth.now()
    val today = LocalDate.now()
    val remainingBudget = budget - expense
    val remainingDays = when {
        month.isBefore(currentMonth) -> 0
        month == currentMonth -> (month.lengthOfMonth() - today.dayOfMonth + 1).coerceAtLeast(0)
        else -> month.lengthOfMonth()
    }
    val dailySuggestionCents = if (remainingDays > 0) remainingBudget.coerceAtLeast(0L) / remainingDays else 0L
    GlassCard {
        Text("预算进度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        Text("本月支出：${MoneyFormat.fromCents(expense)} / ${MoneyFormat.fromCents(budget)}")
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { ratio.coerceIn(0f, 1f) },
            color = statusColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text("使用率：$percent%（$status）", color = statusColor, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = when {
                remainingBudget < 0L -> "已超预算 ${MoneyFormat.fromCents(-remainingBudget)}，今日建议非必要支出为 0。"
                remainingDays <= 0 -> "当月预算周期已结束。"
                month == currentMonth -> "今日建议可支出：${MoneyFormat.fromCents(dailySuggestionCents)}（按月剩余预算均摊）"
                else -> "日均建议可支出：${MoneyFormat.fromCents(dailySuggestionCents)}（按月预算均摊）"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChangeNudgeCard(
    currentExpenseCents: Long,
    previousExpenseCents: Long,
    budgetCents: Long?
) {
    val delta = currentExpenseCents - previousExpenseCents
    val ratioToBudget = if (budgetCents != null && budgetCents > 0L) currentExpenseCents.toFloat() / budgetCents.toFloat() else 0f
    val (title, detail, color) = when {
        budgetCents != null && budgetCents > 0L && ratioToBudget >= 1f ->
            Triple("预算超额提醒", "已超预算 ${(ratioToBudget * 100 - 100).toInt()}%，今天建议暂停非必要支出。", DangerColor)
        previousExpenseCents > 0L && delta > 0L ->
            Triple("支出上升提醒", "较上期多支出 ${MoneyFormat.fromCents(delta)}，建议先查看高频分类。", WarningColor)
        previousExpenseCents > 0L && delta < 0L ->
            Triple("节奏良好", "较上期减少 ${MoneyFormat.fromCents(kotlin.math.abs(delta))}，保持当前节奏。", SuccessColor)
        else -> Triple("保持记录", "暂无明显异常，继续维持记录频率。", MaterialTheme.colorScheme.primary)
    }
    GlassCard(tone = if (color == DangerColor || color == WarningColor) GlassCardTone.Warning else GlassCardTone.Neutral) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = color)
        Spacer(modifier = Modifier.height(6.dp))
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        selected -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
        hasRecord -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
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
internal fun PendingTransactionRow(
    tx: TransactionEntity,
    onConfirm: () -> Unit,
    onIgnore: () -> Unit
) {
    val smartTag = remember(tx.note) { parseSmartTag(tx.note) }
    val displayNote = remember(tx.note) { tx.note.replace(Regex("\\[SMART:[^\\]]+\\]"), "").trim() }
    Card(
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            val prefix = if (tx.type == "INCOME") "+" else "-"
            val txLabel = if (tx.type == "INCOME") "收入" else "支出"
            Text("待确认$txLabel $prefix${MoneyFormat.fromCents(tx.amountCents)}", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            val notePart = if (displayNote.isBlank()) "" else "  $displayNote"
            Text("${tx.source}$notePart", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            if (!smartTag.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "智能命中：$smartTag",
                    style = MaterialTheme.typography.bodySmall,
                    color = SuccessColor
                )
            }
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
    val amountColor = if (tx.type == "INCOME") SuccessColor else DangerColor
    val sourceLabel = formatSourceLabel(tx.source)
    val timeText = remember(tx.occurredAtEpochMs) { Instant.ofEpochMilli(tx.occurredAtEpochMs).atZone(zone).format(rowTimeFormatter) }
    val smartTag = remember(tx.note) { parseSmartTag(tx.note) }
    val displayNote = remember(tx.note) { tx.note.replace(Regex("\\[SMART:[^\\]]+\\]"), "").trim() }
    Card(
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$prefix${MoneyFormat.fromCents(tx.amountCents)}",
                    fontWeight = FontWeight.Bold,
                    color = amountColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "${tx.parentCategory}/${tx.childCategory}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "来源：$sourceLabel  时间：$timeText",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (displayNote.isNotBlank()) {
                Text(
                    "备注：$displayNote",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!smartTag.isNullOrBlank()) {
                Text(
                    "智能命中：$smartTag",
                    style = MaterialTheme.typography.bodySmall,
                    color = SuccessColor
                )
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
                            color = DangerColor,
                            modifier = Modifier.clickable { onDelete() }.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun parseSmartTag(note: String): String? {
    val match = Regex("\\[SMART:([^\\]]+)\\]").find(note) ?: return null
    return match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReadonlyDropdownField(
    value: String,
    label: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<String>,
    isError: Boolean = false,
    onOptionSelected: (String) -> Unit
) {
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { onExpandedChange(!expanded) }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            isError = isError,
            modifier = Modifier
                .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true)
                .fillMaxWidth(),
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(option)
                        onExpandedChange(false)
                    }
                )
            }
        }
    }
}

@Composable
internal fun SectionTitle(
    title: String,
    subtitle: String? = null
) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    if (!subtitle.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    containerColor: Color = MaterialTheme.colorScheme.primary,
    interactionSource: MutableInteractionSource? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Color.White
        )
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}



internal enum class GlassCardTone { Neutral, Warning }

@Composable
internal fun GlassCard(
    tone: GlassCardTone = GlassCardTone.Neutral,
    content: @Composable ColumnScope.() -> Unit
) {
    val container = when (tone) {
        GlassCardTone.Neutral -> MaterialTheme.colorScheme.surface
        GlassCardTone.Warning -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)
    }
    val border = when (tone) {
        GlassCardTone.Neutral -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        GlassCardTone.Warning -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, border),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), content = content)
    }
}







