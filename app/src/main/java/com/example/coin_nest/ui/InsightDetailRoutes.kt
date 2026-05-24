package com.example.coin_nest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.coin_nest.data.db.TransactionEntity
import java.time.LocalDate
import java.time.YearMonth

@Composable
internal fun InsightWeekDetailRoute(
    state: HomeUiState,
    weekTx: List<TransactionEntity>,
    selectedWeekStart: LocalDate,
    selectedWeekDate: LocalDate,
    selectedWeekDayTx: List<TransactionEntity>,
    onSelectedWeekStartChange: (LocalDate) -> Unit,
    onSelectedWeekDateChange: (LocalDate) -> Unit,
    onUpdateTransactionDetails: (Long, String, String, String) -> Unit,
    onDelete: (TransactionEntity) -> Unit,
    onLoadMoreYearTransactions: () -> Unit
) {
    val weekTrend = remember(weekTx, selectedWeekStart) { buildWeekLineTrend(weekTx, selectedWeekStart) }
    val weekShare = remember(weekTx) { buildCategoryShare(weekTx) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            GlassCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { onSelectedWeekStartChange(selectedWeekStart.minusWeeks(1)) },
                        modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                    ) { Text("上周") }
                    Text(
                        "${selectedWeekStart.monthValue}/${selectedWeekStart.dayOfMonth} - ${selectedWeekStart.plusDays(6).monthValue}/${selectedWeekStart.plusDays(6).dayOfMonth}",
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(
                        onClick = { onSelectedWeekStartChange(selectedWeekStart.plusWeeks(1)) },
                        modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                    ) { Text("下周") }
                }
            }
        }
        item { InsightBarTrendCard(title = "周支出柱状图", points = weekTrend.take(7)) }
        item { InsightPieCard(title = "周分类饼图", shares = weekShare) }
        item {
            GlassCard {
                Text("周内日期", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0..6).forEach { idx ->
                        val day = selectedWeekStart.plusDays(idx.toLong())
                        val selected = day == selectedWeekDate
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface)
                                .border(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .clickable { onSelectedWeekDateChange(day) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${day.dayOfMonth}", color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (selectedWeekDayTx.isEmpty()) {
            item { GlassCard { Text("${selectedWeekDate.monthValue}/${selectedWeekDate.dayOfMonth} 暂无流水") } }
            if (state.yearHasMore) {
                item {
                    OutlinedButton(
                        onClick = onLoadMoreYearTransactions,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("加载更多历史流水") }
                }
            }
        } else {
            items(selectedWeekDayTx, key = { it.id }) { tx ->
                InsightTransactionRow(
                    tx = tx,
                    categories = state.categories,
                    onUpdateTransaction = onUpdateTransactionDetails,
                    onDelete = { onDelete(tx) }
                )
            }
        }
    }
}

@Composable
internal fun InsightMonthDetailRoute(
    state: HomeUiState,
    selectedMonth: YearMonth,
    monthByDate: Map<LocalDate, List<TransactionEntity>>,
    selectedMonthDate: LocalDate,
    selectedMonthDayTx: List<TransactionEntity>,
    onSelectMonth: (YearMonth) -> Unit,
    onSelectedMonthDateChange: (LocalDate) -> Unit,
    onUpdateTransactionDetails: (Long, String, String, String) -> Unit,
    onDelete: (TransactionEntity) -> Unit,
    onLoadMoreMonthTransactions: () -> Unit
) {
    val monthSummaryByDay = remember(monthByDate) { calculateDaySummary(monthByDate) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            GlassCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { onSelectMonth(selectedMonth.minusMonths(1)) },
                        modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                    ) { Text("上月") }
                    Text("${selectedMonth.year}年${selectedMonth.monthValue}月", fontWeight = FontWeight.SemiBold)
                    OutlinedButton(
                        onClick = { onSelectMonth(selectedMonth.plusMonths(1)) },
                        modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                    ) { Text("下月") }
                }
            }
        }
        item { InsightBarTrendCard(title = "月支出柱状图", points = state.monthTrendPoints) }
        item { InsightPieCard(title = "月分类饼图", shares = state.monthCategoryShare) }
        item {
            InsightMonthCalendarCard(
                month = selectedMonth,
                selectedDate = selectedMonthDate,
                dailySummary = monthSummaryByDay,
                onSelectDate = onSelectedMonthDateChange
            )
        }
        if (selectedMonthDayTx.isEmpty()) {
            item { GlassCard { Text("${selectedMonthDate.monthValue}/${selectedMonthDate.dayOfMonth} 暂无流水") } }
            if (state.monthHasMore) {
                item {
                    OutlinedButton(
                        onClick = onLoadMoreMonthTransactions,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("加载更多本月流水") }
                }
            }
        } else {
            items(selectedMonthDayTx, key = { it.id }) { tx ->
                InsightTransactionRow(
                    tx = tx,
                    categories = state.categories,
                    onUpdateTransaction = onUpdateTransactionDetails,
                    onDelete = { onDelete(tx) }
                )
            }
            if (state.monthHasMore) {
                item {
                    OutlinedButton(
                        onClick = onLoadMoreMonthTransactions,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("加载更多本月流水") }
                }
            }
        }
    }
}

@Composable
internal fun InsightYearDetailRoute(
    state: HomeUiState,
    selectedMonth: YearMonth,
    onSelectMonth: (YearMonth) -> Unit,
    onLoadMoreYearTransactions: () -> Unit
) {
    val yearSummary = state.selectedYearSummary
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            GlassCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { onSelectMonth(YearMonth.of(selectedMonth.year - 1, selectedMonth.monthValue)) },
                        modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                    ) { Text("上一年") }
                    Text("${selectedMonth.year} 年分析", fontWeight = FontWeight.SemiBold)
                    OutlinedButton(
                        onClick = { onSelectMonth(YearMonth.of(selectedMonth.year + 1, selectedMonth.monthValue)) },
                        modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                    ) { Text("下一年") }
                }
            }
        }
        item {
            InsightMetricStrip(
                income = yearSummary.incomeCents,
                expense = yearSummary.expenseCents,
                balance = yearSummary.balanceCents
            )
        }
        item { InsightBarTrendCard(title = "年内月度支出柱状图", points = state.yearTrendPoints) }
        item { InsightPieCard(title = "年度分类饼图", shares = state.yearCategoryShare) }
        if (state.yearHasMore) {
            item {
                OutlinedButton(
                    onClick = onLoadMoreYearTransactions,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("加载更多历史流水（用于周视图）") }
            }
        }
    }
}

@Composable
internal fun InsightAnomalyRoute(
    anomalies: List<AnomalyInsight>,
    spendingAiInsight: SpendingAiInsight
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (anomalies.isEmpty()) {
            item {
                GlassCard {
                    Text("本月暂无明显异常，继续保持当前记账节奏。")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        spendingAiInsight.anomalyExplanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            item {
                GlassCard(tone = GlassCardTone.Warning) {
                    Text("本地异常解释", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        spendingAiInsight.anomalyExplanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(anomalies, key = { it.id }) { anomaly ->
                GlassCard(tone = GlassCardTone.Warning) {
                    Text(anomaly.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(anomaly.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    anomaly.suggestions.take(3).forEach { s ->
                        Text("• $s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
