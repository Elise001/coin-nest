package com.example.coin_nest.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun ProfileAvatar(
    profile: ProfilePrefs,
    size: Int,
    modifier: Modifier = Modifier,
    activeReward: RewardDefinition? = null
) {
    val context = LocalContext.current
    val imageBitmap = remember(profile.avatarImageUri) {
        profile.avatarImageUri
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?.let { uri ->
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }
    }
    val avatarColor = avatarPresetColor(profile.avatarPresetId)
    val frameColor = avatarFrameColor(activeReward)
    Box(
        modifier = modifier
            .width(size.dp)
            .height(size.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(avatarColor)
            .border(
                width = if (activeReward?.kind == RewardKind.AvatarFrame) 3.dp else 1.dp,
                color = frameColor,
                shape = RoundedCornerShape(999.dp)
            )
            .semantics { contentDescription = "个人头像" },
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(size.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            LineAvatarArt(profile.avatarPresetId, modifier = Modifier.width(size.dp).height(size.dp))
        }
    }
}

@Composable
internal fun ProfileRewardControlHero(
    profile: ProfilePrefs,
    rewardProgress: RewardProgress,
    autoBookHealthy: Boolean,
    budgetText: String,
    rules: Int,
    onOpenProfile: () -> Unit,
    onOpenRewards: () -> Unit
) {
    GlassCard {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(profileHeroBrush(rewardProgress.currentReward))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenProfile() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfileAvatar(profile = profile, size = 52, activeReward = rewardProgress.currentReward)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            profile.nickname,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            "账户控制台 · ${rewardProgress.activeDays} 天记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f)
                        )
                    }
                    Text("›", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileRewardHeroMetric("自动记账", if (autoBookHealthy) "可用" else "待修复", Modifier.weight(1f))
                    ProfileRewardHeroMetric("预算", budgetText, Modifier.weight(1f))
                    ProfileRewardHeroMetric("规则", "${rules}条", Modifier.weight(1f))
                }
                LinearProgressIndicator(
                    progress = { rewardProgress.progressRatio },
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(999.dp)),
                    color = MaterialTheme.colorScheme.onPrimary,
                    trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f))
                        .clickable { onOpenRewards() }
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                ) {
                    val next = rewardProgress.nextReward
                    val current = rewardProgress.currentReward
                    val statusText = when {
                        next == null -> "成长奖励已全部解锁，继续自然记录。"
                        current != null -> "当前：${current.title} · 下个奖励：${next.title}"
                        else -> "下个奖励：${next.title}"
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
internal fun RewardUnlockNoticeCard(
    reward: RewardDefinition,
    onOpenRewards: () -> Unit,
    onDismiss: () -> Unit
) {
    GlassCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RewardBadge(reward = reward, locked = false)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("刚解锁：${reward.title}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(reward.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text("知道了")
            }
            PrimaryActionButton(text = "看看奖励", onClick = onOpenRewards, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun RewardCenterCard(
    progress: RewardProgress,
    selectedRewardId: String?,
    onSelectReward: (String?) -> Unit
) {
    GlassCard {
        Text("奖励中心", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "累计有效记账 ${progress.activeDays} 天。奖励只改变体验，不制造压力。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        rewardCatalog.forEach { reward ->
            val unlocked = progress.activeDays >= reward.requiredDays
            val selected = selectedRewardId == reward.id || (selectedRewardId == null && progress.currentReward?.id == reward.id)
            RewardRow(
                reward = reward,
                unlocked = unlocked,
                selected = selected,
                onSelect = { onSelectReward(if (unlocked) reward.id else selectedRewardId) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun AvatarPresetPicker(
    profile: ProfilePrefs,
    selectedPresetId: String,
    onSelectPreset: (String) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        avatarPresets.forEach { preset ->
            val selected = preset.id == selectedPresetId && profile.avatarImageUri == null
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(avatarPresetColor(preset.id))
                    .border(
                        2.dp,
                        if (selected) MaterialTheme.colorScheme.secondary else Color.Transparent,
                        RoundedCornerShape(999.dp)
                    )
                    .clickable { onSelectPreset(preset.id) },
                contentAlignment = Alignment.Center
            ) {
                LineAvatarArt(preset.id, modifier = Modifier.width(42.dp).height(42.dp))
            }
        }
    }
}

@Composable
private fun RewardRow(
    reward: RewardDefinition,
    unlocked: Boolean,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
                RoundedCornerShape(12.dp)
            )
            .clickable(enabled = unlocked) { onSelect() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RewardBadge(reward = reward, locked = !unlocked)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${reward.requiredDays} 天 · ${reward.title}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                reward.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            when {
                selected -> "使用中"
                unlocked -> "可用"
                else -> "待解锁"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun RewardBadge(reward: RewardDefinition, locked: Boolean) {
    val alpha = if (locked) 0.38f else 1f
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(44.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(14.dp))
            .background(rewardBrush(reward))
            .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            reward.kind.label.take(1),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall
        )
    }
}

@Composable
private fun ProfileRewardHeroMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.13f))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun profileHeroBrush(reward: RewardDefinition?): Brush {
    val colors = when (reward?.kind) {
        RewardKind.ConsoleSkin -> listOf(Color(0xFF155E75), Color(0xFF2563EB))
        RewardKind.AiVoice -> listOf(Color(0xFF5B21B6), Color(0xFF0F766E))
        RewardKind.ReportCover -> listOf(Color(0xFF0F172A), Color(0xFF155E75))
        RewardKind.ChartAccent -> listOf(Color(0xFF0E7490), Color(0xFF1D4ED8))
        RewardKind.Title -> listOf(Color(0xFF92400E), Color(0xFF0F766E))
        RewardKind.AvatarFrame -> listOf(Color(0xFF0D7377), Color(0xFF1D4ED8))
        null -> listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)
    }
    return Brush.linearGradient(colors)
}

@Composable
private fun rewardBrush(reward: RewardDefinition): Brush {
    val colors = when (reward.kind) {
        RewardKind.AvatarFrame -> listOf(Color(0xFF0D7377), Color(0xFF84DCC6))
        RewardKind.ConsoleSkin -> listOf(Color(0xFF2563EB), Color(0xFFA7F3D0))
        RewardKind.AiVoice -> listOf(Color(0xFF7C3AED), Color(0xFFF0ABFC))
        RewardKind.ReportCover -> listOf(Color(0xFF0F172A), Color(0xFFFFD166))
        RewardKind.ChartAccent -> listOf(Color(0xFF155E75), Color(0xFF38BDF8))
        RewardKind.Title -> listOf(Color(0xFFB7791F), Color(0xFFFFD166))
    }
    return Brush.linearGradient(colors)
}
