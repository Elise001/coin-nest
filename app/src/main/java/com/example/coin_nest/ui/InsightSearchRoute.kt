package com.example.coin_nest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun InsightSearchRoute(
    state: HomeUiState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchType: LocalSearchType,
    onSearchTypeChange: (LocalSearchType) -> Unit,
    searchScope: LocalSearchScope,
    onSearchScopeChange: (LocalSearchScope) -> Unit,
    onUpdateTransactionDetails: (Long, String, String, String) -> Unit,
    onDelete: (com.example.coin_nest.data.db.TransactionEntity) -> Unit,
    onLoadMoreYearTransactions: () -> Unit
) {
    val searchTransactions = if (searchScope == LocalSearchScope.Month) {
        state.monthTransactions
    } else {
        state.yearTransactions
    }
    val results = remember(searchTransactions, searchQuery, searchType) {
        filterLocalTransactions(
            transactions = searchTransactions,
            query = searchQuery,
            type = searchType
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            LocalSearchControlCard(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                type = searchType,
                onTypeChange = onSearchTypeChange,
                scope = searchScope,
                onScopeChange = onSearchScopeChange
            )
        }
        item {
            LocalSearchSummaryCard(
                query = searchQuery,
                results = results,
                scope = searchScope,
                hasMore = searchScope == LocalSearchScope.Year && state.yearHasMore,
                onLoadMore = onLoadMoreYearTransactions
            )
        }
        if (results.isEmpty()) {
            item {
                GlassCard {
                    Text(
                        "没有找到匹配流水",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (searchScope == LocalSearchScope.Year) {
                            "换一个金额、来源、分类或备注关键词；年份数据较多时可继续加载。"
                        } else {
                            "换一个关键词，或切到本年查看更早流水。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            items(results.take(80), key = { it.id }) { tx ->
                InsightTransactionRow(
                    tx = tx,
                    categories = state.categories,
                    onUpdateTransaction = onUpdateTransactionDetails,
                    onDelete = { onDelete(tx) }
                )
            }
            if (results.size > 80) {
                item {
                    GlassCard {
                        Text("已显示前 80 条，请增加关键词缩小范围。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
