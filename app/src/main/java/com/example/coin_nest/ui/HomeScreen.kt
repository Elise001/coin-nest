package com.example.coin_nest.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.coin_nest.ui.theme.Amber700
import com.example.coin_nest.ui.theme.Coral400
import com.example.coin_nest.ui.theme.Coral500
import com.example.coin_nest.ui.theme.Ink
import com.example.coin_nest.ui.theme.Mint500
import com.example.coin_nest.ui.theme.Sky200
import com.example.coin_nest.ui.theme.Sky500
import com.example.coin_nest.ui.theme.Sky700
import com.example.coin_nest.util.MoneyFormat
import java.time.LocalDate
import java.time.YearMonth
internal val SuccessColor = Mint500
internal val DangerColor = Coral500
internal val WarningColor = Amber700

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    initialMainTabIndex: Int = 0,
    onAddTransaction: (String, Boolean, String, String, String, Long) -> Unit,
    onConfirmPendingAuto: (Long) -> Unit,
    onIgnorePendingAuto: (Long) -> Unit,
    onUpdateTransactionDetails: (Long, String, String, String) -> Unit,
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
    var insightOpenMonthDetailToken by rememberSaveable { mutableIntStateOf(0) }
    var insightOpenSearchToken by rememberSaveable { mutableIntStateOf(0) }
    var insightOpenSearchType by rememberSaveable { mutableIntStateOf(LocalSearchType.All.ordinal) }
    var settingsOpenBudgetToken by rememberSaveable { mutableIntStateOf(0) }
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
        AppHeader(
            selectedTab = mainTabs[selectedMainTab],
            selectedMonth = state.selectedMonth,
            balance = state.selectedMonthSummary.balanceCents
        )

        Box(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = selectedMainTab,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(animationSpec = tween(220)) { it / 6 * direction } + fadeIn(tween(180)))
                        .togetherWith(slideOutHorizontally(animationSpec = tween(180)) { -it / 8 * direction } + fadeOut(tween(120)))
                        .using(SizeTransform(clip = false))
                },
                label = "main_tab_transition"
            ) { tabIndex ->
                when (mainTabs[tabIndex]) {
                    MainTab.Home -> HomeDashboardTab(
                        state = state,
                        onOpenRecord = { selectedMainTab = MainTab.Record.ordinal },
                        onOpenInsight = { selectedMainTab = MainTab.Insight.ordinal },
                        onUpdateTransaction = onUpdateTransactionDetails,
                        onOpenInsightMonthCalendar = {
                            selectedMainTab = MainTab.Insight.ordinal
                            insightOpenMonthDetailToken++
                        },
                        onOpenIncomeSearch = {
                            selectedMainTab = MainTab.Insight.ordinal
                            insightOpenSearchType = LocalSearchType.Income.ordinal
                            insightOpenSearchToken++
                        },
                        onOpenExpenseSearch = {
                            selectedMainTab = MainTab.Insight.ordinal
                            insightOpenSearchType = LocalSearchType.Expense.ordinal
                            insightOpenSearchToken++
                        },
                        onOpenBudgetSettings = {
                            selectedMainTab = MainTab.Profile.ordinal
                            settingsOpenBudgetToken++
                        }
                    )
                    MainTab.Record -> RecordTab(state, onAddTransaction, onConfirmPendingAuto, onIgnorePendingAuto)
                    MainTab.Insight -> InsightTab(
                        state = state,
                        onSelectMonth = onSelectMonth,
                        onUpdateTransactionDetails = onUpdateTransactionDetails,
                        onDeleteTransaction = onDeleteTransaction,
                        onLoadMoreMonthTransactions = onLoadMoreMonthTransactions,
                        onLoadMoreYearTransactions = onLoadMoreYearTransactions,
                        openMonthDetailAtTodayToken = insightOpenMonthDetailToken,
                        onMonthDetailJumpHandled = { insightOpenMonthDetailToken = 0 },
                        openSearchAtToken = insightOpenSearchToken,
                        openSearchType = LocalSearchType.entries.getOrElse(insightOpenSearchType) { LocalSearchType.All },
                        onSearchJumpHandled = { insightOpenSearchToken = 0 },
                        onOpenBudgetSettings = {
                            selectedMainTab = MainTab.Profile.ordinal
                            settingsOpenBudgetToken++
                        }
                    )
                    MainTab.Profile -> SettingsTab(
                        state = state,
                        onAddCategory = onAddCategory,
                        onSetMonthBudget = onSetMonthBudget,
                        onSetCategoryBudget = onSetCategoryBudget,
                        onExportBackup = onExportBackup,
                        onClearSmartRules = onClearSmartRules,
                        onImportBackup = onImportBackup,
                        openBudgetAtToken = settingsOpenBudgetToken,
                        onBudgetJumpHandled = { settingsOpenBudgetToken = 0 }
                    )
                }
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
    val tabSelectedColor = Sky700
    val tabUnselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tabActiveBgColor = Sky200
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp)
                        .padding(horizontal = 4.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) tabActiveBgColor else MaterialTheme.colorScheme.surface)
                        .semantics {
                            role = Role.Tab
                            this.selected = selected
                        }
                        .clickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current
                        ) { onSelect(index) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .width(22.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) Sky500 else Color.Transparent)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = tabIcon(tab),
                            contentDescription = tab.title,
                            modifier = Modifier.size(22.dp),
                            tint = if (selected) tabSelectedColor else tabUnselectedColor
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Box(
                            modifier = Modifier.width(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab.title,
                                color = if (selected) tabSelectedColor else tabUnselectedColor,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun tabIcon(tab: MainTab): ImageVector = when (tab) {
    MainTab.Home -> Icons.Filled.Home
    MainTab.Record -> Icons.Filled.Edit
    MainTab.Insight -> Icons.Filled.BarChart
    MainTab.Profile -> Icons.Filled.Person
}

@Composable
private fun HomeDashboardTab(
    state: HomeUiState,
    onOpenRecord: () -> Unit,
    onOpenInsight: () -> Unit,
    onUpdateTransaction: (Long, String, String, String) -> Unit,
    onOpenInsightMonthCalendar: () -> Unit,
    onOpenIncomeSearch: () -> Unit,
    onOpenExpenseSearch: () -> Unit,
    onOpenBudgetSettings: () -> Unit
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
    val keyAnomaly = remember(anomalies) { anomalies.firstOrNull() }
    val topCategory = remember(state.monthCategoryShare) { state.monthCategoryShare.maxByOrNull { it.amountCents } }
    val todayPreviewTransactions = remember(state.todayTransactions) {
        state.todayTransactions
            .sortedByDescending { it.occurredAtEpochMs }
            .take(3)
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item {
            MoneyHeroCard(
                month = state.selectedMonth,
                income = state.selectedMonthSummary.incomeCents,
                expense = state.selectedMonthSummary.expenseCents,
                balance = state.selectedMonthSummary.balanceCents,
                budget = state.monthBudgetCents,
                pendingCount = state.pendingAutoTransactions.size,
                onRecord = onOpenRecord,
                onOpenCalendar = onOpenInsightMonthCalendar,
                onOpenIncome = onOpenIncomeSearch,
                onOpenExpense = onOpenExpenseSearch,
                onOpenBudgetSettings = onOpenBudgetSettings
            )
        }
        item {
            SignalCard(
                icon = if (keyAnomaly == null) Icons.Filled.Savings else Icons.Filled.Warning,
                title = keyAnomaly?.title ?: "节奏正常",
                detail = keyAnomaly?.detail ?: "本月暂无明显异常，保持自动记账和每天看一眼即可。",
                warning = keyAnomaly != null,
                onClick = onOpenInsight
            )
        }
        item {
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Receipt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("今日流水", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = onOpenInsight, modifier = Modifier.defaultMinSize(minHeight = 44.dp)) { Text("更多") }
                }
                Spacer(modifier = Modifier.height(6.dp))
                if (todayPreviewTransactions.isEmpty()) {
                    Text(
                        "今天还没有记录，点上方“记一笔”补上即可。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    todayPreviewTransactions.forEachIndexed { index, tx ->
                        TransactionRow(
                            tx = tx,
                            categories = state.categories,
                            allowCategoryEdit = true,
                            onUpdateTransaction = onUpdateTransaction
                        )
                        if (index != todayPreviewTransactions.lastIndex) Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
        item {
            TopCategoryCard(topCategory = topCategory, onOpenInsight = onOpenInsight)
        }
    }
}

@Composable
private fun AppHeader(
    selectedTab: MainTab,
    selectedMonth: YearMonth,
    balance: Long
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
    ) {
        Text(
            text = when (selectedTab) {
                MainTab.Home -> "Coin Nest"
                MainTab.Record -> "记一笔"
                MainTab.Insight -> "钱去哪了"
                MainTab.Profile -> "我的财务设置"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (selectedTab) {
                    MainTab.Home -> "先看余额，再决定要不要花"
                    MainTab.Record -> "少填一点，系统多想一点"
                    MainTab.Insight -> "只看重点，不看噪音"
                    MainTab.Profile -> "权限、预算、数据都在这里"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${selectedMonth.monthValue}月 ${MoneyFormat.fromCents(balance)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun MoneyHeroCard(
    month: YearMonth,
    income: Long,
    expense: Long,
    balance: Long,
    budget: Long?,
    pendingCount: Int,
    onRecord: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenIncome: () -> Unit,
    onOpenExpense: () -> Unit,
    onOpenBudgetSettings: () -> Unit
) {
    val budgetRatio = if (budget != null && budget > 0L) {
        expense.toFloat() / budget.toFloat()
    } else {
        0f
    }.coerceIn(0f, 1.2f)
    val leftBudget = budget?.let { (it - expense).coerceAtLeast(0L) }
    val monthProjection = remember(expense, budget, month) {
        budget?.let { buildMonthBudgetProjection(expense = expense, budget = it, month = month, today = LocalDate.now()) }
    }
    val heroTextColor = Ink
    val heroSubtleColor = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Sky200),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.72f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccountBalanceWallet,
                            contentDescription = null,
                            tint = Sky700,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(9.dp))
                    Column {
                        Text(
                            text = "${month.monthValue}月钱包",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = heroTextColor
                        )
                        Text(
                            text = if (pendingCount > 0) "待确认 $pendingCount 条" else "自动记账已同步",
                            style = MaterialTheme.typography.bodySmall,
                            color = heroSubtleColor
                        )
                    }
                }
                TextButton(onClick = onOpenCalendar, modifier = Modifier.defaultMinSize(minHeight = 44.dp)) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = Sky700)
                }
            }

            Column {
                Text(
                    text = "本月结余",
                    style = MaterialTheme.typography.bodySmall,
                    color = heroSubtleColor
                )
                Text(
                    text = MoneyFormat.fromCents(balance),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = heroTextColor,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyHeroMetric("收入", MoneyFormat.fromCents(income), SuccessColor, Modifier.weight(1f), onClick = onOpenIncome)
                MoneyHeroMetric("支出", MoneyFormat.fromCents(expense), DangerColor, Modifier.weight(1f), onClick = onOpenExpense)
                MoneyHeroMetric("净值", MoneyFormat.fromCents(balance), heroTextColor, Modifier.weight(1f))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.72f))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = budget?.let { "预算剩余 ${MoneyFormat.fromCents(leftBudget ?: 0L)}" } ?: "还没设本月预算",
                            style = MaterialTheme.typography.bodySmall,
                            color = heroTextColor
                        )
                        Text(
                            text = budget?.let { "${(budgetRatio * 100).toInt()}%" } ?: "去我的页设置",
                            modifier = if (budget == null) Modifier.clickable { onOpenBudgetSettings() } else Modifier,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (budget == null) Sky700 else heroSubtleColor,
                            fontWeight = if (budget == null) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.24f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (budget == null) 0.18f else budgetRatio.coerceIn(0.04f, 1f))
                                .height(7.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (budgetRatio >= 0.9f) Coral400 else Sky500)
                        )
                    }
                    if (monthProjection != null) {
                        Text(
                            text = "月底预测：${MoneyFormat.fromCents(monthProjection.projectedExpenseCents)}（${monthProjection.statusText}）",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = monthProjection.statusColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = monthProjection.actionText,
                            style = MaterialTheme.typography.bodySmall,
                            color = heroSubtleColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRecord,
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Sky700,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("记一笔", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onOpenCalendar,
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Sky500.copy(alpha = 0.42f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Sky700
                    )
                ) {
                    Icon(Icons.Filled.Insights, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("看日历")
                }
            }
        }
    }
}

@Composable
private fun MoneyHeroMetric(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.76f))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SignalCard(
    icon: ImageVector,
    title: String,
    detail: String,
    warning: Boolean,
    onClick: () -> Unit
) {
    GlassCard(
        tone = if (warning) GlassCardTone.Warning else GlassCardTone.Neutral,
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background((if (warning) WarningColor else SuccessColor).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (warning) WarningColor else SuccessColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TopCategoryCard(
    topCategory: CategoryShare?,
    onOpenInsight: () -> Unit
) {
    GlassCard(modifier = Modifier.clickable { onOpenInsight() }) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("本月最花钱", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                if (topCategory == null) {
                    Text("先记几笔，系统会自动找出重点分类。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        "${topCategory.name} · ${MoneyFormat.fromCents(topCategory.amountCents)} · ${(topCategory.ratio * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text("去洞察", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

private data class MonthBudgetProjection(
    val projectedExpenseCents: Long,
    val statusText: String,
    val actionText: String,
    val statusColor: Color
)

private fun buildMonthBudgetProjection(
    expense: Long,
    budget: Long,
    month: YearMonth,
    today: LocalDate
): MonthBudgetProjection? {
    val currentMonth = YearMonth.now()
    if (month != currentMonth || budget <= 0L || today.dayOfMonth <= 0) return null
    val projected = (expense.toDouble() / today.dayOfMonth.toDouble() * month.lengthOfMonth()).toLong()
    val overrun = projected - budget
    val elapsedPercent = (today.dayOfMonth.toFloat() / month.lengthOfMonth().toFloat() * 100f).toInt()
    val usedPercent = (expense.toFloat() / budget.toFloat() * 100f).toInt()
    val paceSummary = "本月已过 $elapsedPercent%，预算已用 $usedPercent%。"
    return when {
        overrun > 0L -> MonthBudgetProjection(
            projectedExpenseCents = projected,
            statusText = "预计超 ${MoneyFormat.fromCents(overrun)}",
            actionText = "$paceSummary 建议先压低可选消费，检查餐饮、购物、出行等高频分类。",
            statusColor = DangerColor
        )
        projected >= budget * 0.9 -> MonthBudgetProjection(
            projectedExpenseCents = projected,
            statusText = "接近预算",
            actionText = "$paceSummary 接下来几天按日预算执行，避免月底被动压缩。",
            statusColor = WarningColor
        )
        else -> MonthBudgetProjection(
            projectedExpenseCents = projected,
            statusText = "节奏安全",
            actionText = "$paceSummary 当前消费节奏可控，保持自动记账和每周复盘即可。",
            statusColor = SuccessColor
        )
    }
}

