package com.example.coin_nest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.coin_nest.data.db.TransactionEntity
import com.example.coin_nest.util.MoneyFormat
import java.time.Instant
import java.util.Locale

internal enum class LocalSearchType(val title: String) {
    All("全部"),
    Expense("支出"),
    Income("收入")
}

@Composable
internal fun LocalSearchControlCard(
    query: String,
    onQueryChange: (String) -> Unit,
    type: LocalSearchType,
    onTypeChange: (LocalSearchType) -> Unit
) {
    GlassCard {
        Text("本地找账", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "搜索本年已加载流水，不联网、不上传。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("金额 / 分类 / 来源 / 备注") },
            placeholder = { Text("例如 13.70、餐饮、支付宝") }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LocalSearchType.entries.forEach { item ->
                val selected = item == type
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                        .border(
                            width = 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            },
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onTypeChange(item) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
internal fun LocalSearchSummaryCard(
    query: String,
    results: List<TransactionEntity>,
    hasMore: Boolean,
    onLoadMore: () -> Unit
) {
    val (incomeCents, expenseCents) = remember(results) {
        results.fold(0L to 0L) { (income, expense), tx ->
            if (tx.type == "INCOME") {
                income + tx.amountCents to expense
            } else {
                income to expense + tx.amountCents
            }
        }
    }
    GlassCard {
        Text(
            text = if (query.isBlank()) "当前展示本年已加载流水" else "找到 ${results.size} 条匹配流水",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricPill("收入", MoneyFormat.fromCents(incomeCents), Modifier.weight(1f))
            MetricPill("支出", MoneyFormat.fromCents(expenseCents), Modifier.weight(1f))
        }
        if (hasMore) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                Text("加载更多本年流水")
            }
        }
    }
}

internal fun filterLocalTransactions(
    transactions: List<TransactionEntity>,
    query: String,
    type: LocalSearchType
): List<TransactionEntity> {
    val terms = query
        .trim()
        .lowercase(Locale.ROOT)
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
    return transactions
        .asSequence()
        .filter { tx ->
            when (type) {
                LocalSearchType.All -> true
                LocalSearchType.Expense -> tx.type == "EXPENSE"
                LocalSearchType.Income -> tx.type == "INCOME"
            }
        }
        .filter { tx -> terms.isEmpty() || terms.all { term -> localSearchText(tx).contains(term) } }
        .sortedByDescending { it.occurredAtEpochMs }
        .toList()
}

private fun localSearchText(tx: TransactionEntity): String {
    val typeLabel = if (tx.type == "INCOME") "收入" else "支出"
    val amountYuan = "%.2f".format(Locale.US, tx.amountCents / 100.0)
    val timeText = Instant.ofEpochMilli(tx.occurredAtEpochMs).atZone(zone).format(rowTimeFormatter)
    return listOf(
        typeLabel,
        amountYuan,
        MoneyFormat.fromCents(tx.amountCents),
        tx.amountCents.toString(),
        tx.parentCategory,
        tx.childCategory,
        tx.source,
        formatSourceLabel(tx.source),
        userFacingNote(tx.note),
        timeText
    ).joinToString(" ").lowercase(Locale.ROOT)
}
