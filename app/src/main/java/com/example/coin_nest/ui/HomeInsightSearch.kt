package com.example.coin_nest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.coin_nest.data.db.TransactionEntity
import com.example.coin_nest.ui.theme.Mist100
import com.example.coin_nest.ui.theme.Sky200
import com.example.coin_nest.ui.theme.Sky700
import com.example.coin_nest.util.MoneyFormat
import java.time.Instant
import java.util.Locale

internal enum class LocalSearchType(val title: String) {
    All("全部"),
    Expense("支出"),
    Income("收入")
}

internal enum class LocalSearchScope(val title: String) {
    Month("本月"),
    Year("本年")
}

@Composable
internal fun LocalSearchControlCard(
    query: String,
    onQueryChange: (String) -> Unit,
    type: LocalSearchType,
    onTypeChange: (LocalSearchType) -> Unit,
    scope: LocalSearchScope,
    onScopeChange: (LocalSearchScope) -> Unit
) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionTitle(title = "本地找账", subtitle = "只查已加载流水，不联网、不上传")
            SearchScopeBadge("${scope.title}范围")
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            label = { Text("金额 / 分类 / 来源 / 备注") },
            placeholder = { Text("例如 13.70、餐饮、支付宝") },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Sky700,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.52f)
            )
        )
        Spacer(modifier = Modifier.height(12.dp))
        SearchFilterRow(
            title = "类型",
            options = LocalSearchType.entries,
            selected = type,
            label = { it.title },
            onSelect = onTypeChange
        )
        Spacer(modifier = Modifier.height(8.dp))
        SearchFilterRow(
            title = "范围",
            options = LocalSearchScope.entries,
            selected = scope,
            label = { it.title },
            onSelect = onScopeChange
        )
    }
}

@Composable
internal fun LocalSearchSummaryCard(
    query: String,
    results: List<TransactionEntity>,
    scope: LocalSearchScope,
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
            text = if (query.isBlank()) "当前展示${scope.title}已加载流水" else "找到 ${results.size} 条匹配流水",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchTotalTile("收入", MoneyFormat.fromCents(incomeCents), SuccessColor, Modifier.weight(1f))
            SearchTotalTile("支出", MoneyFormat.fromCents(expenseCents), DangerColor, Modifier.weight(1f))
        }
        if (hasMore) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                Text("加载更多本年流水")
            }
        }
    }
}

@Composable
private fun SearchScopeBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Sky200.copy(alpha = 0.78f))
            .border(1.dp, Sky700.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Sky700,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun <T> SearchFilterRow(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.defaultMinSize(minWidth = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { item ->
                val itemSelected = item == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .defaultMinSize(minHeight = 44.dp)
                        .background(if (itemSelected) Sky200 else Color.Transparent)
                        .border(
                            width = 1.dp,
                            color = if (itemSelected) Sky700.copy(alpha = 0.36f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelect(item) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label(item),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (itemSelected) Sky700 else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchTotalTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Mist100)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .defaultMinSize(minHeight = 76.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
