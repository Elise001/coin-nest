package com.example.coin_nest.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.coin_nest.autobook.AutoBookAuditEvent

internal data class ProfileNavEntry(
    val title: String,
    val subtitle: String,
    val route: String
)

@Composable
internal fun SmartLearningStatusCard(
    status: SmartLearningStatus,
    onClearRules: () -> Unit
) {
    GlassCard {
        Text("智能分类学习状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        if (status.totalRules <= 0) {
            Text("当前暂无学习规则。先在流水里手动改几次分类，系统会自动学习。")
            return@GlassCard
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricPill(label = "已学习规则", value = "${status.totalRules}条", modifier = Modifier.weight(1f))
            MetricPill(label = "高置信规则", value = "${status.highConfidenceRules}条", modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "近7天学习热度",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        SmartLearningTrendBars(hits = status.recent7DayHits)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "高频关键词",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        status.topKeywords.forEach { item ->
            Text(
                "• ${item.keyword}（${item.hitCount}）→ ${item.categoryPath}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onClearRules, modifier = Modifier.fillMaxWidth()) {
            Text("清空学习规则")
        }
    }
}

@Composable
internal fun CompactSmartLearningCard(
    status: SmartLearningStatus,
    onOpenDetail: () -> Unit
) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("智能分类学习", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            TextButton(
                onClick = onOpenDetail,
                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
            ) {
                Text("查看详情")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "规则 ${status.totalRules} 条  ·  高置信 ${status.highConfidenceRules} 条  ·  热词 ${status.topKeywords.size} 个",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        CompactLearningTrend(hits = status.recent7DayHits)
    }
}

@Composable
internal fun ProfileControlHero(
    autoBookHealthy: Boolean,
    budgetText: String,
    activeDays: Int,
    rules: Int,
    onOpenProfile: () -> Unit
) {
    GlassCard {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable { onOpenProfile() }
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(52.dp)
                            .height(52.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("zh", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("账户控制台", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                        Text("本月已记账 $activeDays 天 · 点此查看账户", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f))
                    }
                    Text("›", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileHeroMetric("自动记账", if (autoBookHealthy) "可用" else "待修复", Modifier.weight(1f))
                    ProfileHeroMetric("预算", budgetText, Modifier.weight(1f))
                    ProfileHeroMetric("规则", "${rules}条", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun AutoBookStatusPill(statusText: String, healthy: Boolean) {
    val bg = if (healthy) SuccessColor.copy(alpha = 0.12f) else DangerColor.copy(alpha = 0.12f)
    val border = if (healthy) SuccessColor.copy(alpha = 0.35f) else DangerColor.copy(alpha = 0.35f)
    val color = if (healthy) SuccessColor else DangerColor
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(statusText, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun PermissionActionRow(
    done: Boolean,
    actionText: String,
    doneText: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "permission_press_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "permission_press_alpha"
    )
    val pressModifier = Modifier
        .fillMaxWidth()
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
    if (done) {
        OutlinedButton(
            onClick = onClick,
            modifier = pressModifier,
            interactionSource = interactionSource,
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(SuccessColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", color = SuccessColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(doneText, color = SuccessColor, fontWeight = FontWeight.SemiBold)
            }
        }
    } else {
        PrimaryActionButton(
            text = actionText,
            onClick = onClick,
            modifier = pressModifier,
            shape = RoundedCornerShape(24.dp),
            containerColor = WarningColor,
            interactionSource = interactionSource
        )
    }
}

@Composable
internal fun ProfileEntryCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.26f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun AutoBookAuditEventRow(event: AutoBookAuditEvent) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${auditPackageLabel(event.packageName)} · ${auditEventLabel(event.event)}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                formatEpoch(event.occurredAtEpochMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val reason = auditReasonLabel(event.reason.ifBlank { event.event })
        if (reason.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SmartLearningTrendBars(hits: List<Int>) {
    val safeHits = if (hits.size == 7) hits else List(7) { 0 }
    val maxHit = safeHits.maxOrNull()?.coerceAtLeast(1) ?: 1
    val dayLabels = listOf("D-6", "D-5", "D-4", "D-3", "D-2", "D-1", "今天")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        safeHits.forEachIndexed { index, hit ->
            val ratio = (hit.toFloat() / maxHit.toFloat()).coerceIn(0.15f, 1f)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = hit.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((32f * ratio).dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = dayLabels[index],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CompactLearningTrend(hits: List<Int>) {
    val safeHits = if (hits.size == 7) hits else List(7) { 0 }
    val maxHit = safeHits.maxOrNull()?.coerceAtLeast(1) ?: 1
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        safeHits.forEach { hit ->
            val ratio = (hit.toFloat() / maxHit.toFloat()).coerceIn(0f, 1f)
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (ratio > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((22f * ratio).dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(hit.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProfileHeroMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.13f))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f), fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(3.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
    }
}

private fun auditEventLabel(event: String): String {
    return when (event) {
        "notify_received" -> "收到通知"
        "insert_success", "accessibility_insert_success" -> "已入队"
        "insert_drop", "accessibility_insert_drop" -> "已过滤"
        "parse_failed", "accessibility_parse_failed" -> "解析失败"
        "accessibility_detected" -> "识别到页面"
        "accessibility_drop" -> "页面去重"
        "listener_connected" -> "监听已连接"
        "accessibility_connected" -> "无障碍已连接"
        else -> event
    }
}

private fun auditReasonLabel(reason: String): String {
    val upper = reason.uppercase()
    return when {
        upper.contains("AUTO_DUPLICATE_BY_WINDOW") -> "短时间重复，已忽略"
        upper.contains("SAME_SOURCE_DUPLICATE_BY_TXN_REF") -> "同交易号重复，已忽略"
        upper.contains("AI_RELATED_LINKED") || upper.contains("CROSS_SOURCE_LINKED") -> "跨渠道重复，已合并"
        upper.contains("NON") || reason.contains("非支付") -> reason.take(32)
        upper.contains("INSERTED") -> "进入待确认"
        upper.contains("PARSE") || reason.contains("未提取") || reason.contains("无法判断") -> reason.take(32)
        else -> reason.take(32)
    }
}

private fun auditPackageLabel(packageName: String): String {
    return when (packageName) {
        "com.eg.android.AlipayGphone" -> "支付宝"
        "com.tencent.mm" -> "微信"
        "cmb.pb", "com.chinamworld.main", "com.icbc" -> "银行卡"
        "com.unionpay" -> "云闪付"
        "" -> "本机"
        else -> packageName.take(16)
    }
}
