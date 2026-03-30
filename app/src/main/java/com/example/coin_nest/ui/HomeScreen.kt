package com.example.coin_nest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.coin_nest.data.db.TransactionEntity
import com.example.coin_nest.util.MoneyFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    state: HomeUiState,
    onAddTransaction: (String, Boolean, String, String, String) -> Unit,
    onAddCategory: (String, String) -> Unit,
    onSetMonthBudget: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var amount by remember { mutableStateOf("") }
    var isIncome by remember { mutableStateOf(false) }
    var parentCategory by remember { mutableStateOf("") }
    var childCategory by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf("") }
    var newParent by remember { mutableStateOf("") }
    var newChild by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SummaryCard(
                title = "今日",
                income = state.daily.incomeCents,
                expense = state.daily.expenseCents,
                balance = state.daily.balanceCents
            )
        }
        item {
            SummaryCard(
                title = "本月",
                income = state.monthly.incomeCents,
                expense = state.monthly.expenseCents,
                balance = state.monthly.balanceCents
            )
        }
        item {
            SummaryCard(
                title = "本年",
                income = state.yearly.incomeCents,
                expense = state.yearly.expenseCents,
                balance = state.yearly.balanceCents
            )
        }
        item {
            Card {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("快速记账", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (isIncome) "收入" else "支出")
                        Switch(checked = isIncome, onCheckedChange = { isIncome = it })
                    }
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("金额（元）") }
                    )
                    OutlinedTextField(
                        value = parentCategory,
                        onValueChange = { parentCategory = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("一级分类，例如 生活/工作") }
                    )
                    OutlinedTextField(
                        value = childCategory,
                        onValueChange = { childCategory = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("二级分类，例如 餐饮/通勤") }
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("备注（可选）") }
                    )
                    Button(onClick = {
                        onAddTransaction(amount, isIncome, parentCategory, childCategory, note)
                        amount = ""
                        note = ""
                    }) {
                        Text("保存")
                    }
                }
            }
        }
        item {
            Card {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("预算设置", fontWeight = FontWeight.SemiBold)
                    Text("当前预算：${state.monthBudgetCents?.let { MoneyFormat.fromCents(it) } ?: "未设置"}")
                    OutlinedTextField(
                        value = budget,
                        onValueChange = { budget = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("本月预算（元）") }
                    )
                    Button(onClick = { onSetMonthBudget(budget) }) {
                        Text("更新预算")
                    }
                }
            }
        }
        item {
            Card {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("新增二级分类", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = newParent,
                        onValueChange = { newParent = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("一级分类") }
                    )
                    OutlinedTextField(
                        value = newChild,
                        onValueChange = { newChild = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("二级分类") }
                    )
                    Button(onClick = {
                        onAddCategory(newParent, newChild)
                        newParent = ""
                        newChild = ""
                    }) {
                        Text("添加分类")
                    }
                    Text("已配置分类：${state.categories.joinToString("、") { "${it.parent}/${it.child}" }}")
                }
            }
        }
        item {
            Text("最近流水", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
        }
        items(state.transactions, key = { it.id }) { tx ->
            TransactionRow(tx = tx)
        }
    }
}

@Composable
private fun SummaryCard(title: String, income: Long, expense: Long, balance: Long) {
    Card {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text("收入：${MoneyFormat.fromCents(income)}")
            Text("支出：${MoneyFormat.fromCents(expense)}")
            Text("结余：${MoneyFormat.fromCents(balance)}")
        }
    }
}

@Composable
private fun TransactionRow(tx: TransactionEntity) {
    val formatter = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    val prefix = if (tx.type == "INCOME") "+" else "-"
    Card {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "$prefix${MoneyFormat.fromCents(tx.amountCents)}  ${tx.parentCategory}/${tx.childCategory}",
                fontWeight = FontWeight.Medium
            )
            Text("来源：${tx.source}  时间：${formatter.format(Date(tx.occurredAtEpochMs))}")
            if (tx.note.isNotBlank()) {
                Text("备注：${tx.note}")
            }
        }
    }
}

