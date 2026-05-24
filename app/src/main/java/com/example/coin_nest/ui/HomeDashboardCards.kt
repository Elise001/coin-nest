package com.example.coin_nest.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.coin_nest.ui.theme.Coral400
import com.example.coin_nest.ui.theme.Ink
import com.example.coin_nest.ui.theme.Sky200
import com.example.coin_nest.ui.theme.Sky500
import com.example.coin_nest.ui.theme.Sky700
import com.example.coin_nest.util.MoneyFormat
import java.time.LocalDate
import java.time.YearMonth

@Composable
internal fun MoneyHeroCard(
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
                        Text("${month.monthValue}月钱包", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = heroTextColor)
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
                Text("本月结余", style = MaterialTheme.typography.bodySmall, color = heroSubtleColor)
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
                    colors = ButtonDefaults.buttonColors(containerColor = Sky700, contentColor = Color.White)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("记一笔", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onOpenCalendar,
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Sky500.copy(alpha = 0.42f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Sky700)
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
internal fun SignalCard(
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
internal fun TopCategoryCard(
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

