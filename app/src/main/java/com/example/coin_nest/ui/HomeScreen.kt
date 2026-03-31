package com.example.coin_nest.ui

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.coin_nest.data.db.TransactionEntity
import com.example.coin_nest.util.MoneyFormat
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class MainTab(val title: String) {
    Overview("概览"),
    Record("记账"),
    Settings("设置")
}

private enum class OverviewTabMode(val title: String) {
    Daily("日"),
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

private val zone: ZoneId = ZoneId.systemDefault()
private val rowTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

@Composable
fun HomeScreen(
    state: HomeUiState,
    onAddTransaction: (String, Boolean, String, String, String) -> Unit,
    onConfirmPendingAuto: (Long) -> Unit,
    onIgnorePendingAuto: (Long) -> Unit,
    onAddCategory: (String, String) -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onSetMonthBudget: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMainTab by rememberSaveable { mutableIntStateOf(0) }
    val mainTabs = remember { MainTab.entries }

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
                MainTab.Overview -> OverviewScreen(state, onSelectMonth)
                MainTab.Record -> RecordTab(state, onAddTransaction, onConfirmPendingAuto, onIgnorePendingAuto)
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

@Composable
private fun OverviewScreen(state: HomeUiState, onSelectMonth: (YearMonth) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(OverviewTabMode.Daily) }
    var showMonthCalendar by rememberSaveable { mutableStateOf(false) }
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now()) }
    val selectedMonth = state.selectedMonth

    val todayTransactions = state.todayTransactions
    val monthTransactions = state.monthTransactions
    val yearTransactions = state.yearTransactions

    val monthDailyTrend = remember(mode, showMonthCalendar, monthTransactions) {
        if (mode != OverviewTabMode.Monthly || showMonthCalendar) emptyList()
        else buildMonthTrend(monthTransactions, selectedMonth)
    }
    val monthCategoryShare = remember(mode, showMonthCalendar, monthTransactions) {
        if (mode != OverviewTabMode.Monthly || showMonthCalendar) emptyList()
        else buildCategoryShare(monthTransactions)
    }
    val yearMonthlyTrend = remember(mode, yearTransactions) {
        if (mode != OverviewTabMode.Yearly) emptyList() else buildYearTrend(yearTransactions)
    }
    val yearCategoryShare = remember(mode, yearTransactions) {
        if (mode != OverviewTabMode.Yearly) emptyList() else buildCategoryShare(yearTransactions)
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
                else items(todayTransactions, key = { it.id }, contentType = { "tx_row" }) { tx -> TransactionRow(tx) }
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
                    else items(selectedDayTransactions, key = { it.id }, contentType = { "tx_row" }) { tx -> TransactionRow(tx) }
                } else {
                    item {
                        MonthlyComparisonCard(
                            month = selectedMonth,
                            currentExpenseCents = state.selectedMonthSummary.expenseCents,
                            previousExpenseCents = state.previousMonthSummary.expenseCents
                        )
                    }
                    item { TrendChartCard("月内支出趋势", monthDailyTrend) }
                    item { CategoryShareCard("月支出分类", monthCategoryShare) }
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
                        onClick = { mode = OverviewTabMode.Monthly; showMonthCalendar = false }
                    )
                }
                item { TrendChartCard("年内支出趋势", yearMonthlyTrend) }
                item { CategoryShareCard("年支出分类", yearCategoryShare) }
            }
        }
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
                            onConfirm = { onConfirmPendingAuto(tx.id) },
                            onIgnore = { onIgnorePendingAuto(tx.id) }
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
private fun SummaryCard(
    title: String,
    income: Long,
    expense: Long,
    balance: Long,
    highlight: Boolean = false,
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
            Text(title, fontWeight = FontWeight.SemiBold)
            HorizontalDivider(color = Color(0xFF7B573A).copy(alpha = 0.25f))
            Text("收入：${MoneyFormat.fromCents(income)}")
            Text("支出：${MoneyFormat.fromCents(expense)}")
            Text("结余：${MoneyFormat.fromCents(balance)}", fontWeight = FontWeight.SemiBold)
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
private fun TransactionRow(tx: TransactionEntity) {
    val prefix = if (tx.type == "INCOME") "+" else "-"
    val amountColor = if (tx.type == "INCOME") Color(0xFF2E7D32) else Color(0xFFB23A30)
    val timeText = remember(tx.occurredAtEpochMs) { Instant.ofEpochMilli(tx.occurredAtEpochMs).atZone(zone).format(rowTimeFormatter) }
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF2DE)), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = "$prefix${MoneyFormat.fromCents(tx.amountCents)}  ${tx.parentCategory}/${tx.childCategory}",
                fontWeight = FontWeight.SemiBold,
                color = amountColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text("来源：${tx.source}  时间：$timeText")
            if (tx.note.isNotBlank()) {
                Text("备注：${tx.note}", maxLines = 1, overflow = TextOverflow.Ellipsis)
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

private fun buildMonthTrend(monthTx: List<TransactionEntity>, month: YearMonth): List<TrendPoint> {
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

private fun buildYearTrend(yearTx: List<TransactionEntity>): List<TrendPoint> {
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

private fun formatTrendRange(first: String, last: String): String {
    return "$first - $last"
}


