package com.example.coin_nest.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.coin_nest.ui.theme.Amber700
import com.example.coin_nest.ui.theme.Coral500
import com.example.coin_nest.ui.theme.Mint500
import java.time.YearMonth
internal val SuccessColor = Mint500
internal val DangerColor = Coral500
internal val WarningColor = Amber700

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    modifier: Modifier = Modifier,
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
    ) -> Unit
) {
    var selectedMainTab by rememberSaveable { mutableIntStateOf(initialMainTabIndex.coerceIn(0, MainTab.entries.size - 1)) }
    var insightOpenMonthDetailToken by rememberSaveable { mutableIntStateOf(0) }
    var insightOpenSearchToken by rememberSaveable { mutableIntStateOf(0) }
    var insightOpenSearchType by rememberSaveable { mutableIntStateOf(LocalSearchType.All.ordinal) }
    var insightOpenSearchScope by rememberSaveable { mutableIntStateOf(LocalSearchScope.Year.ordinal) }
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
                            insightOpenSearchScope = LocalSearchScope.Month.ordinal
                            insightOpenSearchToken++
                        },
                        onOpenExpenseSearch = {
                            selectedMainTab = MainTab.Insight.ordinal
                            insightOpenSearchType = LocalSearchType.Expense.ordinal
                            insightOpenSearchScope = LocalSearchScope.Month.ordinal
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
                        openSearchScope = LocalSearchScope.entries.getOrElse(insightOpenSearchScope) { LocalSearchScope.Year },
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
