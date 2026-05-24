package com.example.coin_nest.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.coin_nest.util.MoneyFormat
import java.math.BigDecimal

@Composable
internal fun SettingsBudgetRoute(
    state: HomeUiState,
    onAddCategory: (String, String) -> Unit,
    onSetMonthBudget: (String) -> Unit,
    onSetCategoryBudget: (String, String, String) -> Unit
) {
    val context = LocalContext.current
    var budget by rememberSaveable { mutableStateOf("") }
    var budgetError by rememberSaveable { mutableStateOf<String?>(null) }
    var newParent by rememberSaveable { mutableStateOf("") }
    var newChild by rememberSaveable { mutableStateOf("") }
    var budgetParentExpanded by rememberSaveable { mutableStateOf(false) }
    var budgetChildExpanded by rememberSaveable { mutableStateOf(false) }
    var budgetParent by rememberSaveable { mutableStateOf("") }
    var budgetChild by rememberSaveable { mutableStateOf("") }
    var categoryBudgetAmount by rememberSaveable { mutableStateOf("") }
    val canUpdateBudget = remember(budget) {
        val parsed = budget.toBigDecimalOrNull()
        parsed != null && parsed > BigDecimal.ZERO
    }
    val grouped = remember(state.categories) { state.categories.groupBy { it.parent } }
    val budgetParentOptions = remember(grouped) { grouped.keys.sortedWith(categoryParentComparator) }
    val budgetChildOptions = remember(budgetParent, grouped) {
        grouped[budgetParent].orEmpty().map { it.child }.distinct().sorted()
    }
    val canSetCategoryBudget = remember(budgetParent, budgetChild, categoryBudgetAmount) {
        budgetParent.isNotBlank() && budgetChild.isNotBlank() &&
            ((categoryBudgetAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO) > BigDecimal.ZERO)
    }
    val categoryBudgetUsage = remember(state.monthTransactions, state.selectedMonthCategoryBudgets) {
        val expenseMap = state.monthTransactions.asSequence()
            .filter { it.type == "EXPENSE" }
            .groupBy { it.parentCategory to it.childCategory }
            .mapValues { (_, txs) -> txs.sumOf { it.amountCents } }
        state.selectedMonthCategoryBudgets.map { budgetItem ->
            val used = expenseMap[budgetItem.parentCategory to budgetItem.childCategory] ?: 0L
            budgetItem to used
        }.sortedByDescending { (_, used) -> used }
    }
    LaunchedEffect(budgetParentOptions) {
        if (budgetParent !in budgetParentOptions) budgetParent = budgetParentOptions.firstOrNull().orEmpty()
    }
    LaunchedEffect(budgetChildOptions) {
        if (budgetChild !in budgetChildOptions) budgetChild = budgetChildOptions.firstOrNull().orEmpty()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            GlassCard {
                SectionTitle(title = "预算设置")
                Spacer(modifier = Modifier.height(8.dp))
                Text("当前预算：${state.monthBudgetCents?.let { MoneyFormat.fromCents(it) } ?: "未设置"}")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = budget,
                    onValueChange = {
                        budget = it
                        if (budgetError != null) budgetError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("本月预算（元）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = budgetError != null,
                    supportingText = { budgetError?.let { msg -> Text(msg) } }
                )
                Spacer(modifier = Modifier.height(10.dp))
                PrimaryActionButton(
                    text = "更新预算",
                    onClick = {
                        val parsed = budget.toBigDecimalOrNull()
                        if (parsed == null || parsed <= BigDecimal.ZERO) {
                            budgetError = "请输入正确预算"
                            return@PrimaryActionButton
                        }
                        onSetMonthBudget(budget)
                        budget = ""
                        Toast.makeText(context, "预算已更新", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canUpdateBudget
                )
            }
        }
        item {
            GlassCard {
                SectionTitle(title = "分类预算")
                Spacer(modifier = Modifier.height(8.dp))
                ReadonlyDropdownField(
                    value = budgetParent,
                    label = "一级分类",
                    expanded = budgetParentExpanded,
                    onExpandedChange = { budgetParentExpanded = it },
                    options = budgetParentOptions,
                    onOptionSelected = { parent ->
                        budgetParent = parent
                        budgetChild = grouped[parent].orEmpty().firstOrNull()?.child.orEmpty()
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ReadonlyDropdownField(
                    value = budgetChild,
                    label = "二级分类",
                    expanded = budgetChildExpanded,
                    onExpandedChange = { budgetChildExpanded = it },
                    options = budgetChildOptions,
                    onOptionSelected = { child -> budgetChild = child }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = categoryBudgetAmount,
                    onValueChange = { categoryBudgetAmount = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("该分类预算（元）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                PrimaryActionButton(
                    text = "保存分类预算",
                    onClick = {
                        onSetCategoryBudget(budgetParent, budgetChild, categoryBudgetAmount)
                        Toast.makeText(context, "分类预算已更新", Toast.LENGTH_SHORT).show()
                        categoryBudgetAmount = ""
                    },
                    enabled = canSetCategoryBudget,
                    modifier = Modifier.fillMaxWidth()
                )
                if (categoryBudgetUsage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    SectionTitle(title = "本月分类预算进度")
                    Spacer(modifier = Modifier.height(6.dp))
                    categoryBudgetUsage.take(6).forEach { (item, used) ->
                        val ratio = if (item.limitCents <= 0) 0f else (used.toFloat() / item.limitCents.toFloat()).coerceAtLeast(0f)
                        Text("${item.parentCategory}/${item.childCategory}  ${MoneyFormat.fromCents(used)} / ${MoneyFormat.fromCents(item.limitCents)}")
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { ratio.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
        item {
            GlassCard {
                SectionTitle(title = "新增二级分类")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newParent,
                    onValueChange = { newParent = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("一级分类") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newChild,
                    onValueChange = { newChild = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("二级分类") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                PrimaryActionButton(
                    text = "添加分类",
                    onClick = {
                        onAddCategory(newParent, newChild)
                        newParent = ""
                        newChild = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
