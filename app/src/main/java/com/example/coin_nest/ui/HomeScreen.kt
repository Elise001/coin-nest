package com.example.coin_nest.ui

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.coin_nest.data.db.TransactionEntity
import com.example.coin_nest.util.MoneyFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private enum class HomeTab(val title: String) {
    Overview("\u6982\u89c8"),
    Record("\u8bb0\u8d26"),
    Settings("\u8bbe\u7f6e")
}

private enum class OverviewMode {
    Daily,
    Monthly,
    Yearly
}

private data class DayAmountSummary(
    val incomeCents: Long = 0L,
    val expenseCents: Long = 0L
) {
    val balanceCents: Long get() = incomeCents - expenseCents
}

private val zone: ZoneId = ZoneId.systemDefault()

@Composable
fun HomeScreen(
    state: HomeUiState,
    onAddTransaction: (String, Boolean, String, String, String) -> Unit,
    onAddCategory: (String, String) -> Unit,
    onSetMonthBudget: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = remember { HomeTab.entries }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF09111F), Color(0xFF121D2E), Color(0xFF0E1624))
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Coin Nest",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)
            )
            Text(
                text = "\u4e2a\u4eba\u8bb0\u8d26\u4e0e\u9884\u7b97",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(tab.title) }
                    )
                }
            }
            when (tabs[selectedTab]) {
                HomeTab.Overview -> OverviewTab(state)
                HomeTab.Record -> RecordTab(state, onAddTransaction)
                HomeTab.Settings -> SettingsTab(state, onAddCategory, onSetMonthBudget)
            }
        }
    }
}

@Composable
private fun OverviewTab(state: HomeUiState) {
    var mode by rememberSaveable { mutableStateOf(OverviewMode.Daily) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var focusMonth by remember { mutableStateOf(YearMonth.now()) }
    val currentYear = LocalDate.now().year

    val monthTransactions = remember(state.transactions, focusMonth) {
        state.transactions.filter { tx ->
            val date = Instant.ofEpochMilli(tx.occurredAtEpochMs).atZone(zone).toLocalDate()
            date.year == focusMonth.year && date.month == focusMonth.month
        }
    }
    val monthByDate = remember(monthTransactions) {
        monthTransactions.groupBy { tx ->
            Instant.ofEpochMilli(tx.occurredAtEpochMs).atZone(zone).toLocalDate()
        }
    }
    val monthDailySummary = remember(monthByDate) { calculateDaySummary(monthByDate) }
    val yearMonthlySummary = remember(state.transactions, currentYear) { calculateYearMonthSummary(state.transactions, currentYear) }
    val months = remember(currentYear) { (1..12).map { YearMonth.of(currentYear, it) } }

    LaunchedEffect(focusMonth) {
        if (selectedDate.year != focusMonth.year || selectedDate.month != focusMonth.month) {
            selectedDate = LocalDate.of(focusMonth.year, focusMonth.month, 1)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SummaryCard(
                title = "\u672c\u6708",
                income = state.monthly.incomeCents,
                expense = state.monthly.expenseCents,
                balance = state.monthly.balanceCents,
                highlight = true,
                onClick = {
                    focusMonth = YearMonth.now()
                    mode = OverviewMode.Monthly
                }
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) {
                    SummaryCard(
                        title = "\u4eca\u65e5",
                        income = state.daily.incomeCents,
                        expense = state.daily.expenseCents,
                        balance = state.daily.balanceCents,
                        onClick = { mode = OverviewMode.Daily }
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    SummaryCard(
                        title = "\u672c\u5e74",
                        income = state.yearly.incomeCents,
                        expense = state.yearly.expenseCents,
                        balance = state.yearly.balanceCents,
                        onClick = { mode = OverviewMode.Yearly }
                    )
                }
            }
        }
        if (state.monthBudgetCents != null && state.monthBudgetCents > 0) {
            item { BudgetProgressCard(expense = state.monthly.expenseCents, budget = state.monthBudgetCents) }
        }
        when (mode) {
            OverviewMode.Daily -> {
                item {
                    Text(
                        text = "\u6700\u8fd1\u6d41\u6c34",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                val latest = state.transactions.take(20)
                if (latest.isEmpty()) {
                    item { GlassCard { Text("\u6682\u65e0\u8bb0\u5f55") } }
                } else {
                    items(latest, key = { it.id }) { tx -> TransactionRow(tx) }
                }
            }
            OverviewMode.Monthly -> {
                item {
                    HeaderWithBack(
                        title = "${focusMonth.year}\u5e74${focusMonth.monthValue}\u6708\u65e5\u5386",
                        onBack = { mode = OverviewMode.Daily }
                    )
                }
                item {
                    MonthCalendarCard(
                        month = focusMonth,
                        selectedDate = selectedDate,
                        dailySummary = monthDailySummary,
                        onSelectDate = { selectedDate = it }
                    )
                }
                item {
                    val formatter = DateTimeFormatter.ofPattern("MM-dd")
                    Text(
                        text = "${selectedDate.format(formatter)} \u6536\u652f\u660e\u7ec6",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                val selectedList = monthByDate[selectedDate].orEmpty().sortedByDescending { it.occurredAtEpochMs }
                if (selectedList.isEmpty()) {
                    item { GlassCard { Text("\u5f53\u5929\u6682\u65e0\u6d41\u6c34") } }
                } else {
                    items(selectedList, key = { it.id }) { tx -> TransactionRow(tx) }
                }
            }
            OverviewMode.Yearly -> {
                item {
                    HeaderWithBack(
                        title = "${currentYear}\u5e74\u6708\u5ea6\u660e\u7ec6",
                        onBack = { mode = OverviewMode.Daily }
                    )
                }
                items(months, key = { it.toString() }) { ym ->
                    val summary = yearMonthlySummary[ym] ?: DayAmountSummary()
                    GlassCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${ym.monthValue}\u6708", fontWeight = FontWeight.SemiBold)
                            Text("\u6536\u5165 ${MoneyFormat.fromCents(summary.incomeCents)}")
                            Text("\u652f\u51fa ${MoneyFormat.fromCents(summary.expenseCents)}")
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("\u7ed3\u4f59 ${MoneyFormat.fromCents(summary.balanceCents)}")
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "\u70b9\u51fb\u67e5\u770b\u8be5\u6708\u65e5\u5386",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.clickable {
                                focusMonth = ym
                                mode = OverviewMode.Monthly
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordTab(
    state: HomeUiState,
    onAddTransaction: (String, Boolean, String, String, String) -> Unit
) {
    val context = LocalContext.current
    var amount by remember { mutableStateOf("") }
    var isIncome by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var parentExpanded by remember { mutableStateOf(false) }
    var childExpanded by remember { mutableStateOf(false) }
    var parentCategory by rememberSaveable { mutableStateOf("") }
    var childCategory by rememberSaveable { mutableStateOf("") }

    val grouped = remember(state.categories) { state.categories.groupBy { it.parent } }
    val parentOptions = remember(grouped, isIncome) {
        val keys = grouped.keys.toList().sorted()
        if (isIncome) keys.filter { it == "\u6536\u5165" }.ifEmpty { keys }
        else keys.filter { it != "\u6536\u5165" }.ifEmpty { keys }
    }
    val childOptions = remember(parentCategory, grouped) {
        grouped[parentCategory].orEmpty().map { it.child }.distinct().sorted()
    }

    LaunchedEffect(parentOptions) {
        if (parentCategory !in parentOptions) parentCategory = parentOptions.firstOrNull().orEmpty()
    }
    LaunchedEffect(childOptions) {
        if (childCategory !in childOptions) childCategory = childOptions.firstOrNull().orEmpty()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            GlassCard {
                Text("\u5feb\u901f\u8bb0\u8d26", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isIncome) "\u6536\u5165" else "\u652f\u51fa")
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(checked = isIncome, onCheckedChange = { isIncome = it })
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("\u91d1\u989d\uff08\u5143\uff09") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = parentExpanded,
                    onExpandedChange = { parentExpanded = !parentExpanded }
                ) {
                    OutlinedTextField(
                        value = parentCategory,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
                        label = { Text("\u4e00\u7ea7\u5206\u7c7b") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = parentExpanded) }
                    )
                    ExposedDropdownMenu(expanded = parentExpanded, onDismissRequest = { parentExpanded = false }) {
                        parentOptions.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    parentCategory = item
                                    childCategory = grouped[item]?.firstOrNull()?.child.orEmpty()
                                    parentExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = childExpanded,
                    onExpandedChange = { childExpanded = !childExpanded }
                ) {
                    OutlinedTextField(
                        value = childCategory,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
                        label = { Text("\u4e8c\u7ea7\u5206\u7c7b") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = childExpanded) }
                    )
                    ExposedDropdownMenu(expanded = childExpanded, onDismissRequest = { childExpanded = false }) {
                        childOptions.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    childCategory = item
                                    childExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("\u5907\u6ce8\uff08\u53ef\u9009\uff09") },
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        val parsedAmount = amount.toDoubleOrNull()
                        if (parsedAmount == null || parsedAmount <= 0.0) {
                            Toast.makeText(context, "\u8bf7\u8f93\u5165\u6b63\u786e\u91d1\u989d", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (parentCategory.isBlank() || childCategory.isBlank()) {
                            Toast.makeText(context, "\u8bf7\u9009\u62e9\u5206\u7c7b", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        onAddTransaction(amount, isIncome, parentCategory, childCategory, note)
                        amount = ""
                        note = ""
                        Toast.makeText(context, "\u4fdd\u5b58\u6210\u529f", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("\u4fdd\u5b58\u8bb0\u5f55")
                }
            }
        }
        item {
            GlassCard {
                Text("\u5df2\u914d\u7f6e\u5206\u7c7b", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (state.categories.isEmpty()) "\u6682\u65e0\u5206\u7c7b"
                    else state.categories.joinToString("\u3001") { "${it.parent}/${it.child}" },
                    style = MaterialTheme.typography.bodyMedium
                )
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
    var budget by remember { mutableStateOf("") }
    var newParent by remember { mutableStateOf("") }
    var newChild by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            GlassCard {
                Text("\u81ea\u52a8\u8bb0\u8d26", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "\u901a\u8fc7\u76d1\u542c\u5fae\u4fe1/\u652f\u4ed8\u5b9d\u901a\u77e5\u81ea\u52a8\u63d0\u53d6\u6d88\u8d39\u91d1\u989d\u3002",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("\u6253\u5f00\u901a\u77e5\u76d1\u542c\u6743\u9650")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        try {
                            val pm = context.getSystemService(PowerManager::class.java)
                            if (pm != null && !pm.isIgnoringBatteryOptimizations(context.packageName)) {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } else {
                                Toast.makeText(context, "\u5df2\u7ecf\u5ffd\u7565\u7535\u6c60\u4f18\u5316", Toast.LENGTH_SHORT).show()
                            }
                        } catch (_: Exception) {
                            Toast.makeText(context, "\u6253\u5f00\u5931\u8d25", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("\u5141\u8bb8\u540e\u53f0\u4fdd\u6d3b\uff08\u53ef\u9009\uff09")
                }
            }
        }
        item {
            GlassCard {
                Text("\u9884\u7b97\u8bbe\u7f6e", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "\u5f53\u524d\u9884\u7b97\uff1a${state.monthBudgetCents?.let { MoneyFormat.fromCents(it) } ?: "\u672a\u8bbe\u7f6e"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = budget,
                    onValueChange = { budget = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("\u672c\u6708\u9884\u7b97\uff08\u5143\uff09") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { onSetMonthBudget(budget) }, modifier = Modifier.fillMaxWidth()) {
                    Text("\u66f4\u65b0\u9884\u7b97")
                }
            }
        }
        item {
            GlassCard {
                Text("\u65b0\u589e\u4e8c\u7ea7\u5206\u7c7b", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newParent,
                    onValueChange = { newParent = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("\u4e00\u7ea7\u5206\u7c7b") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newChild,
                    onValueChange = { newChild = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("\u4e8c\u7ea7\u5206\u7c7b") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        onAddCategory(newParent, newChild)
                        newParent = ""
                        newChild = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("\u6dfb\u52a0\u5206\u7c7b")
                }
            }
        }
    }
}

@Composable
private fun HeaderWithBack(title: String, onBack: () -> Unit) {
    GlassCard {
        Text(
            text = "\u8fd4\u56de",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable { onBack() }
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SummaryCard(
    title: String,
    income: Long,
    expense: Long,
    balance: Long,
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val bg = if (highlight) Brush.linearGradient(listOf(Color(0xFF143B57), Color(0xFF1E5A62)))
    else Brush.linearGradient(listOf(Color(0xFF1C2537), Color(0xFF252F43)))
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    ) {
        Column(
            modifier = Modifier.background(bg).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
            Text("\u6536\u5165\uff1a${MoneyFormat.fromCents(income)}")
            Text("\u652f\u51fa\uff1a${MoneyFormat.fromCents(expense)}")
            Text("\u7ed3\u4f59\uff1a${MoneyFormat.fromCents(balance)}", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BudgetProgressCard(expense: Long, budget: Long) {
    val ratio = if (budget <= 0) 0f else (expense.toFloat() / budget.toFloat()).coerceAtLeast(0f)
    val percent = (ratio * 100).toInt()
    val status = when {
        ratio >= 1f -> "\u5df2\u8d85\u989d"
        ratio >= 0.8f -> "\u63a5\u8fd1\u4e0a\u9650"
        else -> "\u6b63\u5e38"
    }
    GlassCard {
        Text("\u9884\u7b97\u8fdb\u5ea6", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        Text("\u672c\u6708\u652f\u51fa\uff1a${MoneyFormat.fromCents(expense)} / ${MoneyFormat.fromCents(budget)}")
        Text("\u4f7f\u7528\u7387\uff1a$percent%\uff08$status\uff09")
    }
}

@Composable
private fun MonthCalendarCard(
    month: YearMonth,
    selectedDate: LocalDate,
    dailySummary: Map<LocalDate, DayAmountSummary>,
    onSelectDate: (LocalDate) -> Unit
) {
    val firstDay = month.atDay(1)
    val offset = firstDay.dayOfWeek.value - 1
    val totalDays = month.lengthOfMonth()
    val cellCount = ((offset + totalDays + 6) / 7) * 7
    val cells = buildList<LocalDate?> {
        repeat(offset) { add(null) }
        for (day in 1..totalDays) add(month.atDay(day))
        repeat(cellCount - size) { add(null) }
    }
    val weekNames = listOf("\u4e00", "\u4e8c", "\u4e09", "\u56db", "\u4e94", "\u516d", "\u65e5")

    GlassCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${month.year}\u5e74${month.monthValue}\u6708", fontWeight = FontWeight.SemiBold)
            val monthIncome = dailySummary.values.sumOf { it.incomeCents }
            val monthExpense = dailySummary.values.sumOf { it.expenseCents }
            Text("\u6536 ${MoneyFormat.fromCents(monthIncome)} \u652f ${MoneyFormat.fromCents(monthExpense)}")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            weekNames.forEach { name ->
                Text(text = name, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        for (weekIndex in 0 until cellCount step 7) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (i in 0..6) {
                    val date = cells[weekIndex + i]
                    val summary = date?.let { dailySummary[it] } ?: DayAmountSummary()
                    CalendarCell(
                        date = date,
                        selected = date == selectedDate,
                        summary = summary,
                        modifier = Modifier.weight(1f),
                        onClick = { if (date != null) onSelectDate(date) }
                    )
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
        selected -> Color(0xFF2FA7A1).copy(alpha = 0.35f)
        hasRecord -> Color(0xFF2FA7A1).copy(alpha = 0.16f)
        else -> Color.Transparent
    }
    Column(
        modifier = modifier
            .height(58.dp)
            .padding(2.dp)
            .background(cellBg, RoundedCornerShape(8.dp))
            .clickable(enabled = date != null, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = date?.dayOfMonth?.toString().orEmpty(), style = MaterialTheme.typography.bodySmall)
        if (date != null && hasRecord) {
            Text(
                text = "\u6536${(summary.incomeCents / 100)} \u652f${(summary.expenseCents / 100)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Spacer(modifier = Modifier.height(1.dp))
        }
    }
}

@Composable
private fun TransactionRow(tx: TransactionEntity) {
    val formatter = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    val prefix = if (tx.type == "INCOME") "+" else "-"
    GlassCard {
        Text(
            text = "$prefix${MoneyFormat.fromCents(tx.amountCents)}  ${tx.parentCategory}/${tx.childCategory}",
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text("\u6765\u6e90\uff1a${tx.source}  \u65f6\u95f4\uff1a${formatter.format(Date(tx.occurredAtEpochMs))}")
        if (tx.note.isNotBlank()) Text("\u5907\u6ce8\uff1a${tx.note}")
    }
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B3547).copy(alpha = 0.85f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            content = content
        )
    }
}

private fun calculateDaySummary(map: Map<LocalDate, List<TransactionEntity>>): Map<LocalDate, DayAmountSummary> {
    return map.mapValues { (_, txs) ->
        var income = 0L
        var expense = 0L
        txs.forEach { tx ->
            if (tx.type == "INCOME") income += tx.amountCents else expense += tx.amountCents
        }
        DayAmountSummary(incomeCents = income, expenseCents = expense)
    }
}

private fun calculateYearMonthSummary(
    all: List<TransactionEntity>,
    year: Int
): Map<YearMonth, DayAmountSummary> {
    val grouped = all.groupBy { tx ->
        val d = Instant.ofEpochMilli(tx.occurredAtEpochMs).atZone(zone).toLocalDate()
        YearMonth.of(d.year, d.month)
    }
    return grouped
        .filterKeys { it.year == year }
        .mapValues { (_, txs) ->
            var income = 0L
            var expense = 0L
            txs.forEach { tx ->
                if (tx.type == "INCOME") income += tx.amountCents else expense += tx.amountCents
            }
            DayAmountSummary(incomeCents = income, expenseCents = expense)
        }
}
