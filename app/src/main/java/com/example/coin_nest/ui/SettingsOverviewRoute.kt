package com.example.coin_nest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsOverviewRoute(
    state: HomeUiState,
    profile: ProfilePrefs,
    rewardProgress: RewardProgress,
    rewardUnlockNotice: RewardDefinition?,
    autoBookHealthy: Boolean,
    onOpenRoute: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenRewards: () -> Unit,
    onDismissRewardNotice: () -> Unit
) {
    val profileEntries = listOf(
        ProfileNavEntry("自动记账与权限", "必要权限与稳定性优化", "autobook"),
        ProfileNavEntry("预算与分类", "月预算、分类预算、新增分类", "budget"),
        ProfileNavEntry("数据与备份", "导出、导入与恢复", "data"),
        ProfileNavEntry("识别日志", "开发调试用，记录识别内容与拦截原因", "debug_logs")
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ProfileRewardControlHero(
                profile = profile,
                rewardProgress = rewardProgress,
                autoBookHealthy = autoBookHealthy,
                budgetText = state.monthBudgetCents?.let { com.example.coin_nest.util.MoneyFormat.fromCents(it) } ?: "未设置",
                rules = state.smartLearningStatus.totalRules,
                onOpenProfile = onOpenProfile,
                onOpenRewards = onOpenRewards
            )
        }
        rewardUnlockNotice?.let { reward ->
            item {
                RewardUnlockNoticeCard(
                    reward = reward,
                    onOpenRewards = {
                        onDismissRewardNotice()
                        onOpenRewards()
                    },
                    onDismiss = onDismissRewardNotice
                )
            }
        }
        item {
            GlassCard {
                profileEntries.forEachIndexed { index, entry ->
                    ProfileEntryCard(
                        title = entry.title,
                        subtitle = entry.subtitle
                    ) {
                        onOpenRoute(entry.route)
                    }
                    if (index != profileEntries.lastIndex) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
        item {
            CompactSmartLearningCard(
                status = state.smartLearningStatus,
                onOpenDetail = { onOpenRoute("learning_detail") }
            )
        }
    }
}
