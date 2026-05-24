package com.example.coin_nest.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsProfileDetailRoute(
    profile: ProfilePrefs,
    rewardProgress: RewardProgress,
    onProfileSaved: (ProfilePrefs) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var draftNickname by rememberSaveable(profile.nickname) { mutableStateOf(profile.nickname) }
    var draftAvatarPresetId by rememberSaveable(profile.avatarPresetId) { mutableStateOf(profile.avatarPresetId) }
    var draftAvatarImageUri by rememberSaveable(profile.avatarImageUri) { mutableStateOf(profile.avatarImageUri) }
    val avatarImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        draftAvatarImageUri = uri.toString()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            GlassCard {
                Text("个人资料", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(
                        profile = profile.copy(
                            avatarPresetId = draftAvatarPresetId,
                            avatarImageUri = draftAvatarImageUri
                        ),
                        size = 64,
                        activeReward = rewardProgress.currentReward
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("头像", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "使用本地预设或选择相册图片。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { avatarImageLauncher.launch(arrayOf("image/*")) },
                        modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                    ) {
                        Text("相册")
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                AvatarPresetPicker(
                    profile = profile,
                    selectedPresetId = draftAvatarPresetId,
                    onSelectPreset = {
                        draftAvatarPresetId = it
                        draftAvatarImageUri = null
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = draftNickname,
                    onValueChange = { draftNickname = it.take(16) },
                    label = { Text("昵称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                PrimaryActionButton(
                    text = "保存资料",
                    onClick = {
                        val saved = ProfilePreferenceStore.saveIdentity(
                            context = context,
                            nickname = draftNickname,
                            avatarPresetId = draftAvatarPresetId,
                            avatarImageUri = draftAvatarImageUri
                        )
                        onProfileSaved(saved)
                        Toast.makeText(context, "个人资料已保存", Toast.LENGTH_SHORT).show()
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
internal fun SettingsRewardsRoute(
    profile: ProfilePrefs,
    rewardProgress: RewardProgress,
    onProfileChanged: (ProfilePrefs) -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            RewardCenterCard(
                progress = rewardProgress,
                selectedRewardId = profile.selectedRewardId,
                onSelectReward = { rewardId ->
                    onProfileChanged(ProfilePreferenceStore.saveSelectedReward(context, rewardId))
                    Toast.makeText(context, "奖励外观已启用", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
internal fun SettingsLearningDetailRoute(
    state: HomeUiState,
    showClearSmartRuleConfirm: Boolean,
    onShowClearSmartRuleConfirm: () -> Unit,
    onDismissClearSmartRuleConfirm: () -> Unit,
    onClearSmartRules: () -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SmartLearningStatusCard(
                status = state.smartLearningStatus,
                onClearRules = onShowClearSmartRuleConfirm
            )
        }
    }

    if (showClearSmartRuleConfirm) {
        AlertDialog(
            onDismissRequest = onDismissClearSmartRuleConfirm,
            title = { Text("清空学习规则？") },
            text = { Text("此操作会删除本地智能分类学习结果，但不会删除已有流水。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearSmartRules()
                        onDismissClearSmartRuleConfirm()
                        Toast.makeText(context, "已清空智能分类学习规则", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("确认清空") }
            },
            dismissButton = {
                TextButton(onClick = onDismissClearSmartRuleConfirm) { Text("取消") }
            }
        )
    }
}
