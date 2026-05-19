package com.example.coin_nest.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.coin_nest.data.db.TransactionEntity
import com.example.coin_nest.data.model.CategoryItem
import com.example.coin_nest.util.MoneyFormat
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

@Composable
internal fun InsightMetricStrip(income: Long, expense: Long, balance: Long) {
    GlassCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricPill("收入", MoneyFormat.fromCents(income), Modifier.weight(1f))
            MetricPill("支出", MoneyFormat.fromCents(expense), Modifier.weight(1f))
            MetricPill("结余", MoneyFormat.fromCents(balance), Modifier.weight(1f))
        }
    }
}

@Composable
internal fun InsightBarTrendCard(title: String, points: List<TrendPoint>) {
    GlassCard {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        if (points.isEmpty()) {
            Text("暂无趋势数据")
            return@GlassCard
        }
        val max = points.maxOf { it.expenseCents }.coerceAtLeast(1L)
        val chartSummary = points.takeLast(12).joinToString("，") { "${it.label}支出${MoneyFormat.fromCents(it.expenseCents)}" }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "$title：$chartSummary" },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            points.takeLast(12).forEach { p ->
                val ratio = (p.expenseCents.toFloat() / max.toFloat()).coerceIn(0f, 1f)
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)), contentAlignment = Alignment.BottomCenter) {
                        Box(modifier = Modifier.fillMaxWidth().height((42f * ratio).dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)))
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(p.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun InsightPieCard(title: String, shares: List<CategoryShare>) {
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
    )
    GlassCard {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        if (shares.isEmpty()) {
            Text("暂无分类数据")
            return@GlassCard
        }
        val topShareSummary = shares.take(5).joinToString("，") {
            "${it.name}${(it.ratio * 100).roundToInt()}%"
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .semantics { contentDescription = "$title：$topShareSummary" }
        ) {
            val diameter = size.minDimension * 0.75f
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            var start = -90f
            shares.take(5).forEachIndexed { idx, item ->
                val sweep = item.ratio.coerceIn(0f, 1f) * 360f
                drawArc(
                    color = palette[idx % palette.size],
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = topLeft,
                    size = Size(diameter, diameter)
                )
                start += sweep
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        shares.take(5).forEachIndexed { idx, item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(8.dp).height(8.dp).background(palette[idx % palette.size], RoundedCornerShape(2.dp)))
                Spacer(modifier = Modifier.width(6.dp))
                Text("${item.name} ${(item.ratio * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
internal fun InsightMonthCalendarCard(
    month: YearMonth,
    selectedDate: LocalDate,
    dailySummary: Map<LocalDate, DayAmountSummary>,
    onSelectDate: (LocalDate) -> Unit
) {
    val cells = remember(month) {
        val first = month.atDay(1)
        val offset = first.dayOfWeek.value - 1
        val total = month.lengthOfMonth()
        val count = ((offset + total + 6) / 7) * 7
        buildList<LocalDate?> {
            repeat(offset) { add(null) }
            for (d in 1..total) add(month.atDay(d))
            repeat(count - size) { add(null) }
        }
    }
    val weekNames = listOf("一", "二", "三", "四", "五", "六", "日")
    GlassCard {
        Text("选择日期", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            weekNames.forEach { w -> Text(w, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(modifier = Modifier.height(6.dp))
        for (i in cells.indices step 7) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (j in 0..6) {
                    val date = cells[i + j]
                    val expenseCents = if (date == null) 0L else (dailySummary[date]?.expenseCents ?: 0L)
                    val hasTx = expenseCents > 0L
                    val selected = date == selectedDate
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    date == null -> Color.Transparent
                                    selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    hasTx -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    else -> Color.Transparent
                                }
                            )
                            .semantics {
                                if (date != null) {
                                    role = Role.Button
                                    contentDescription = "${date.monthValue}月${date.dayOfMonth}日，支出${MoneyFormat.fromCents(expenseCents)}"
                                }
                            }
                            .clickable(enabled = date != null) { if (date != null) onSelectDate(date) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(date?.dayOfMonth?.toString().orEmpty(), color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        if (hasTx) {
                            Text(
                                text = MoneyFormat.fromCents(expenseCents),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun InsightTransactionRow(
    tx: TransactionEntity,
    categories: List<CategoryItem>,
    onUpdateTransaction: (Long, String, String, String) -> Unit,
    onDelete: () -> Unit
) {
    TransactionRow(
        tx = tx,
        categories = categories,
        allowCategoryEdit = true,
        onUpdateTransaction = onUpdateTransaction,
        onDelete = onDelete
    )
}
