package com.example.coin_nest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
        Text("近7天学习热度", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        SmartLearningTrendBars(hits = status.recent7DayHits)
        Spacer(modifier = Modifier.height(8.dp))
        Text("高频关键词", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
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
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(hit.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text(dayLabels[index], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
