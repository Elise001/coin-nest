package com.example.coin_nest.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.coin_nest.util.MoneyFormat
import kotlin.math.roundToInt

internal data class InsightSummary(val detail: String, val nudge: String)

@Composable
internal fun InsightCommandCard(
    detail: String,
    nudge: String,
    focusInsight: SpendingFocusInsight?,
    income: Long,
    expense: Long,
    balance: Long,
    anomalyCount: Int
) {
    GlassCard {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "本月先看",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (focusInsight != null) "重点：${focusInsight.shortText}" else "先让自动记账积累样本",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.86f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (anomalyCount > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (anomalyCount > 0) "$anomalyCount 条异常" else "无异常",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (anomalyCount > 0) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = nudge,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f)
                )
                if (focusInsight != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.10f))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = focusInsight.actionText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricOnDark("收入", MoneyFormat.fromCents(income), Modifier.weight(1f))
                    MetricOnDark("支出", MoneyFormat.fromCents(expense), Modifier.weight(1f))
                    MetricOnDark("结余", MoneyFormat.fromCents(balance), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun InsightSnapshotCard(
    mode: OverviewTabMode,
    onModeChange: (OverviewTabMode) -> Unit,
    points: List<TrendPoint>,
    shares: List<CategoryShare>
) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("趋势与占比", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            CompactModeSelector(mode = mode, onModeChange = onModeChange)
        }
        Spacer(modifier = Modifier.height(10.dp))
        MiniTrendBars(points = points)
        Spacer(modifier = Modifier.height(10.dp))
        if (shares.isEmpty()) {
            Text(
                "暂无分类占比，先保持自动记账，形成一周以上样本后再看结构。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            shares.take(3).forEachIndexed { index, item ->
                TopShareRow(rank = index + 1, item = item)
                if (index < shares.take(3).lastIndex) Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun CompactModeSelector(mode: OverviewTabMode, onModeChange: (OverviewTabMode) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        OverviewTabMode.entries.forEach { item ->
            val selected = item == mode
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .semantics {
                        role = Role.Tab
                        contentDescription = "切换到${item.title}趋势"
                    }
                    .clickable { onModeChange(item) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MiniTrendBars(points: List<TrendPoint>) {
    if (points.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)),
            contentAlignment = Alignment.Center
        ) {
            Text("暂无趋势", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val recentPoints = points.takeLast(12)
    val max = recentPoints.maxOf { it.expenseCents }.coerceAtLeast(1L)
    val chartSummary = recentPoints.joinToString("，") { "${it.label}支出${MoneyFormat.fromCents(it.expenseCents)}" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .semantics { contentDescription = "支出趋势：$chartSummary" },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        recentPoints.forEach { point ->
            val ratio = (point.expenseCents.toFloat() / max.toFloat()).coerceIn(0.08f, 1f)
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((42f * ratio).dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.72f))
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(point.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TopShareRow(rank: Int, item: CategoryShare) {
    val ratioText = "${(item.ratio * 100).roundToInt()}%"
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text("$rank", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Text(ratioText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(item.ratio.coerceIn(0f, 1f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.72f))
                )
            }
        }
    }
}

@Composable
private fun MetricOnDark(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.13f))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f), fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(3.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

internal fun buildInsightSummary(
    currentExpenseCents: Long,
    previousExpenseCents: Long,
    budgetCents: Long?
): InsightSummary {
    val nudge = when {
        budgetCents != null && budgetCents > 0L -> {
            val ratio = currentExpenseCents.toDouble() / budgetCents.toDouble()
            when {
                ratio >= 1.0 -> "预算超额，建议本周收缩非必要消费。"
                ratio >= 0.8 -> "预算已使用 ${(ratio * 100).roundToInt()}%，请关注剩余天数。"
                else -> "预算使用率 ${(ratio * 100).roundToInt()}%，节奏正常。"
            }
        }
        else -> "尚未设置预算，建议先设本月上限。"
    }
    val detail = if (previousExpenseCents > 0L) {
        val delta = currentExpenseCents - previousExpenseCents
        if (delta >= 0L) "较上月多支出 ${MoneyFormat.fromCents(delta)}" else "较上月少支出 ${MoneyFormat.fromCents(-delta)}"
    } else {
        "暂无可对比上月数据"
    }
    return InsightSummary(detail = detail, nudge = nudge)
}

@Composable
internal fun InsightActionListCard(
    mode: OverviewTabMode,
    anomalyCount: Int,
    onOpenDetail: () -> Unit,
    onOpenAnomaly: () -> Unit,
    onOpenSearch: () -> Unit
) {
    val detailTitle = when (mode) {
        OverviewTabMode.Weekly -> "周流水"
        OverviewTabMode.Monthly -> "月日历与流水"
        OverviewTabMode.Yearly -> "年流水分析"
    }
    val detailSubtitle = when (mode) {
        OverviewTabMode.Weekly -> "周趋势、周内日期、当天明细"
        OverviewTabMode.Monthly -> "月趋势、分类占比、按天查流水"
        OverviewTabMode.Yearly -> "年度趋势、年度占比、历史流水"
    }
    GlassCard {
        Text("继续看", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        InsightActionRow(title = detailTitle, subtitle = detailSubtitle, onClick = onOpenDetail)
        Spacer(modifier = Modifier.height(8.dp))
        InsightActionRow(
            title = "异常与建议",
            subtitle = if (anomalyCount > 0) "发现 $anomalyCount 条风险，优先处理" else "暂无异常，继续观察",
            onClick = onOpenAnomaly
        )
        Spacer(modifier = Modifier.height(8.dp))
        InsightActionRow(title = "本地找账", subtitle = "按金额、分类、来源、备注定位流水", onClick = onOpenSearch)
    }
}

@Composable
private fun InsightActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
            .semantics { role = Role.Button }
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun SpendingAiInsightCard(
    insight: SpendingAiInsight,
    onOpenBudgetSettings: () -> Unit
) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("本地 AI 判断", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "可信度 ${insight.confidenceLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(insight.anomalyExplanation, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(insight.habitProfile, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            insight.budgetAdvice,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (insight.suggestedBudgetCents != null) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenBudgetSettings, modifier = Modifier.fillMaxWidth()) {
                Text("参考预算 ${MoneyFormat.fromCents(insight.suggestedBudgetCents)}")
            }
        }
    }
}

internal data class SpendingFocusInsight(
    val shortText: String,
    val actionText: String
)

internal fun buildSpendingFocusInsight(shares: List<CategoryShare>): SpendingFocusInsight? {
    val top = shares.maxByOrNull { it.amountCents }?.takeIf { it.amountCents > 0L } ?: return null
    val ratioPercent = (top.ratio * 100).toInt()
    val actionText = when {
        top.ratio >= 0.45f -> "占比偏集中，建议先给这个分类设一个小上限，连续 3 天观察变化。"
        top.ratio >= 0.28f -> "这是当前最大支出来源，建议优先检查是否有可延后或可替代消费。"
        else -> "支出结构较分散，建议先关注高频小额消费，避免无感累积。"
    }
    return SpendingFocusInsight(
        shortText = "${top.name} ${MoneyFormat.fromCents(top.amountCents)} · $ratioPercent%",
        actionText = actionText
    )
}

