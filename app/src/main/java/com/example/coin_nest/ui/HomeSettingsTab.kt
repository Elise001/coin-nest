package com.example.coin_nest.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.fillMaxSize
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
internal fun SettingsTab(
    state: HomeUiState,
    onAddCategory: (String, String) -> Unit,
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
    openBudgetAtToken: Int = 0,
    onBudgetJumpHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val settingsNav = rememberNavController()
    var profilePrefs by remember { mutableStateOf(ProfilePreferenceStore.load(context)) }
    var rewardUnlockNotice by remember { mutableStateOf<RewardDefinition?>(null) }
    var showClearSmartRuleConfirm by rememberSaveable { mutableStateOf(false) }
    var lastHandledBudgetToken by rememberSaveable { mutableIntStateOf(0) }
    val rewardProgress = remember(state.activeBookkeepingDays, profilePrefs.selectedRewardId) {
        buildRewardProgress(
            activeDays = state.activeBookkeepingDays,
            selectedRewardId = profilePrefs.selectedRewardId
        )
    }
    val autoBookHealthy = remember { getAutoBookHealthStatus(context).healthy }

    LaunchedEffect(openBudgetAtToken) {
        if (openBudgetAtToken > lastHandledBudgetToken) {
            settingsNav.navigate("budget") { launchSingleTop = true }
            lastHandledBudgetToken = openBudgetAtToken
            onBudgetJumpHandled()
        }
    }
    LaunchedEffect(state.activeBookkeepingDays) {
        val latest = rewardCatalog.lastOrNull { state.activeBookkeepingDays >= it.requiredDays }
        if (latest != null && latest.requiredDays > profilePrefs.lastRewardShownDay) {
            profilePrefs = ProfilePreferenceStore.markRewardShown(context, latest.requiredDays)
            rewardUnlockNotice = latest
        }
    }

    NavHost(
        navController = settingsNav,
        startDestination = "overview",
        modifier = Modifier.fillMaxSize()
    ) {
        composable("overview") {
            SettingsOverviewRoute(
                state = state,
                profile = profilePrefs,
                rewardProgress = rewardProgress,
                rewardUnlockNotice = rewardUnlockNotice,
                autoBookHealthy = autoBookHealthy,
                onOpenRoute = { route -> settingsNav.navigate(route) },
                onOpenProfile = { settingsNav.navigate("profile_detail") },
                onOpenRewards = { settingsNav.navigate("rewards") },
                onDismissRewardNotice = { rewardUnlockNotice = null }
            )
        }
        composable("autobook") {
            SettingsAutoBookRoute()
        }
        composable("budget") {
            SettingsBudgetRoute(
                state = state,
                onAddCategory = onAddCategory,
                onSetMonthBudget = onSetMonthBudget,
                onSetCategoryBudget = onSetCategoryBudget
            )
        }
        composable("data") {
            SettingsDataRoute(
                onExportBackup = onExportBackup,
                onImportBackup = onImportBackup
            )
        }
        composable("debug_logs") {
            SettingsDebugLogsRoute()
        }
        composable("profile_detail") {
            SettingsProfileDetailRoute(
                profile = profilePrefs,
                rewardProgress = rewardProgress,
                onProfileSaved = { profilePrefs = it },
                onBack = { settingsNav.popBackStack() }
            )
        }
        composable("rewards") {
            SettingsRewardsRoute(
                profile = profilePrefs,
                rewardProgress = rewardProgress,
                onProfileChanged = { profilePrefs = it }
            )
        }
        composable("learning_detail") {
            SettingsLearningDetailRoute(
                state = state,
                showClearSmartRuleConfirm = showClearSmartRuleConfirm,
                onShowClearSmartRuleConfirm = { showClearSmartRuleConfirm = true },
                onDismissClearSmartRuleConfirm = { showClearSmartRuleConfirm = false },
                onClearSmartRules = onClearSmartRules
            )
        }
    }
}
