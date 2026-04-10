package com.example.coin_nest.ui

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.example.coin_nest.util.MoneyFormat
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.max

private enum class QuickFillMode { FULL_TEMPLATE, CATEGORY_ONLY }

@Composable
internal fun RecordTab(
    state: HomeUiState,
    onAddTransaction: (String, Boolean, String, String, String, Long) -> Unit,
    onConfirmPendingAuto: (Long) -> Unit,
    onIgnorePendingAuto: (Long) -> Unit
) {
    val context = LocalContext.current
    var amount by rememberSaveable { mutableStateOf("") }
    var isIncome by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    var parentExpanded by rememberSaveable { mutableStateOf(false) }
    var childExpanded by rememberSaveable { mutableStateOf(false) }
    var parentCategory by rememberSaveable { mutableStateOf("") }
    var childCategory by rememberSaveable { mutableStateOf("") }
    var selectedTemplateLabel by rememberSaveable { mutableStateOf("") }
    var selectedCategoryShortcut by rememberSaveable { mutableStateOf("") }
    var selectedRecordDate by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var amountError by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryError by rememberSaveable { mutableStateOf(false) }
    var quickFillMode by rememberSaveable { mutableStateOf(QuickFillMode.FULL_TEMPLATE) }
    var showNoteField by rememberSaveable { mutableStateOf(false) }

    val templates = remember {
        listOf(
            RecordTemplate("早餐", "15", false, "生活", "餐饮", "早餐"),
            RecordTemplate("午饭", "35", false, "生活", "餐饮", "午饭"),
            RecordTemplate("咖啡", "18", false, "生活", "餐饮", "咖啡"),
            RecordTemplate("地铁", "4", false, "工作", "通勤", "通勤"),
            RecordTemplate("工资", "5000", true, "收入", "工资", "工资入账")
        )
    }
    val shownTemplates = remember(isIncome, templates) { templates.filter { it.isIncome == isIncome } }

    val grouped = remember(state.categories) { state.categories.groupBy { it.parent } }
    val parentOptions = remember(grouped, isIncome) {
        val keys = grouped.keys.toList().sorted()
        if (isIncome) keys.filter { it == "收入" }.ifEmpty { keys } else keys.filter { it != "收入" }.ifEmpty { keys }
    }
    val childOptions = remember(parentCategory, grouped) { grouped[parentCategory].orEmpty().map { it.child }.distinct().sorted() }
    val recentCategoryPairs = remember(state.monthTransactions, isIncome) {
        val targetType = if (isIncome) "INCOME" else "EXPENSE"
        state.monthTransactions.asSequence()
            .filter { it.type == targetType }
            .sortedByDescending { it.occurredAtEpochMs }
            .map { it.parentCategory to it.childCategory }
            .distinct()
            .take(8)
            .toList()
    }
    val quickAmounts = remember(isIncome) {
        if (isIncome) listOf("500", "1000", "3000", "5000") else listOf("10", "20", "30", "50", "100")
    }
    val parsedAmountCents = remember(amount) {
        amount.toBigDecimalOrNull()?.let { decimal ->
            if (decimal > BigDecimal.ZERO) decimal.multiply(BigDecimal(100)).toLong() else null
        }
    }
    val recommendedCategoryPair = remember(state.monthTransactions, isIncome, parsedAmountCents) {
        val targetType = if (isIncome) "INCOME" else "EXPENSE"
        val candidates = state.monthTransactions
            .asSequence()
            .filter { it.type == targetType }
            .filter { tx ->
                val amountCents = parsedAmountCents
                if (amountCents == null) true
                else abs(tx.amountCents - amountCents) <= max(amountCents / 2, 2000L)
            }
            .toList()
        candidates
            .groupBy { it.parentCategory to it.childCategory }
            .maxWithOrNull(
                compareBy<Map.Entry<Pair<String, String>, List<com.example.coin_nest.data.db.TransactionEntity>>> { it.value.size }
                    .thenBy { it.value.maxOfOrNull { tx -> tx.occurredAtEpochMs } ?: 0L }
            )
            ?.key
    }
    val saveButtonText = remember(isIncome, parsedAmountCents) {
        val typeLabel = if (isIncome) "收入" else "支出"
        val amountLabel = parsedAmountCents?.let { MoneyFormat.fromCents(it) } ?: "--"
        "保存$typeLabel $amountLabel"
    }
    val canSubmit = remember(amount, parentCategory, childCategory) {
        val parsed = amount.toBigDecimalOrNull()
        parsed != null && parsed > BigDecimal.ZERO && parentCategory.isNotBlank() && childCategory.isNotBlank()
    }
    val today = remember { LocalDate.now() }

    LaunchedEffect(parentOptions) {
        if (parentCategory !in parentOptions) parentCategory = parentOptions.firstOrNull().orEmpty()
    }
    LaunchedEffect(childOptions) {
        if (childCategory !in childOptions) childCategory = childOptions.firstOrNull().orEmpty()
    }
    LaunchedEffect(isIncome) {
        selectedTemplateLabel = ""
        selectedCategoryShortcut = ""
        quickFillMode = QuickFillMode.FULL_TEMPLATE
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (state.pendingAutoTransactions.isNotEmpty()) {
            item {
                GlassCard {
                    Text("待确认自动记账", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("通知错过也没关系，可在这里确认或取消。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                state.pendingAutoTransactions.forEach { tx ->
                                    onIgnorePendingAuto(tx.id)
                                    NotificationManagerCompat.from(context).cancel(tx.id.toInt())
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("全部取消") }
                        Button(
                            onClick = {
                                state.pendingAutoTransactions.forEach { tx ->
                                    onConfirmPendingAuto(tx.id)
                                    NotificationManagerCompat.from(context).cancel(tx.id.toInt())
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("全部确认") }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    state.pendingAutoTransactions.take(5).forEach { tx ->
                        PendingTransactionRow(
                            tx = tx,
                            onConfirm = {
                                onConfirmPendingAuto(tx.id)
                                NotificationManagerCompat.from(context).cancel(tx.id.toInt())
                            },
                            onIgnore = {
                                onIgnorePendingAuto(tx.id)
                                NotificationManagerCompat.from(context).cancel(tx.id.toInt())
                            }
                        )
                    }
                    if (state.pendingAutoTransactions.size > 5) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("还有 ${state.pendingAutoTransactions.size - 5} 条待处理", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item {
            GlassCard {
                SectionTitle(title = "快速记账", subtitle = "先选类型与金额，再确认分类")
                Spacer(modifier = Modifier.height(10.dp))

                SegmentedSelector(
                    options = listOf("支出", "收入"),
                    selectedIndex = if (isIncome) 1 else 0,
                    onSelect = { index -> isIncome = index == 1 }
                )

                Spacer(modifier = Modifier.height(10.dp))
                SectionTitle(
                    title = "快捷填充",
                    subtitle = if (quickFillMode == QuickFillMode.FULL_TEMPLATE) {
                        "完整模板：会填充金额、分类、备注"
                    } else {
                        "仅分类：只修改分类，不改金额与备注"
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))

                SegmentedSelector(
                    options = listOf("完整模板", "仅分类"),
                    selectedIndex = if (quickFillMode == QuickFillMode.FULL_TEMPLATE) 0 else 1,
                    onSelect = { index ->
                        quickFillMode = if (index == 0) QuickFillMode.FULL_TEMPLATE else QuickFillMode.CATEGORY_ONLY
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (quickFillMode == QuickFillMode.FULL_TEMPLATE) {
                            "当前模式：完整模板（会修改金额、分类、备注）"
                        } else {
                            "当前模式：仅分类（不会修改金额和备注）"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (quickFillMode == QuickFillMode.FULL_TEMPLATE) {
                        shownTemplates.forEach { tpl ->
                            QuickActionChip(
                                label = tpl.label,
                                selected = selectedTemplateLabel == tpl.label,
                                onClick = {
                                    amount = tpl.amountYuan
                                    isIncome = tpl.isIncome
                                    parentCategory = tpl.parent
                                    childCategory = tpl.child
                                    note = tpl.note
                                    selectedTemplateLabel = tpl.label
                                    selectedCategoryShortcut = ""
                                    amountError = null
                                    categoryError = false
                                }
                            )
                        }
                    } else {
                        if (recentCategoryPairs.isEmpty()) {
                            Text("暂无可复用分类", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            recentCategoryPairs.forEach { pair ->
                                val key = "${pair.first}/${pair.second}"
                                QuickActionChip(
                                    label = key,
                                    selected = selectedCategoryShortcut == key,
                                    onClick = {
                                        parentCategory = pair.first
                                        childCategory = pair.second
                                        selectedCategoryShortcut = key
                                        selectedTemplateLabel = ""
                                        categoryError = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                SectionTitle(title = "快捷金额")
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickAmounts.forEach { quick ->
                        QuickActionChip(
                            label = "￥$quick",
                            selected = amount == quick,
                            onClick = {
                                amount = quick
                                amountError = null
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it
                        if (amountError != null) amountError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("金额（元）") },
                    prefix = { Text("￥", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    placeholder = { Text("例如 23.50") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    ),
                    isError = amountError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = {
                        if (amountError != null) Text(amountError!!)
                        else Text("支持整数与两位小数")
                    }
                )

                if (recommendedCategoryPair != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "推荐分类",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        QuickActionChip(
                            label = "${recommendedCategoryPair.first}/${recommendedCategoryPair.second}",
                            selected = parentCategory == recommendedCategoryPair.first && childCategory == recommendedCategoryPair.second,
                            onClick = {
                                parentCategory = recommendedCategoryPair.first
                                childCategory = recommendedCategoryPair.second
                                selectedCategoryShortcut = "${recommendedCategoryPair.first}/${recommendedCategoryPair.second}"
                                categoryError = false
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                ReadonlyDropdownField(
                    value = parentCategory,
                    label = "一级分类",
                    expanded = parentExpanded,
                    onExpandedChange = { parentExpanded = it },
                    options = parentOptions,
                    isError = categoryError && parentCategory.isBlank(),
                    onOptionSelected = { item ->
                        parentCategory = item
                        childCategory = grouped[item]?.firstOrNull()?.child.orEmpty()
                        categoryError = false
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))
                ReadonlyDropdownField(
                    value = childCategory,
                    label = "二级分类",
                    expanded = childExpanded,
                    onExpandedChange = { childExpanded = it },
                    options = childOptions,
                    isError = categoryError && childCategory.isBlank(),
                    onOptionSelected = { item ->
                        childCategory = item
                        categoryError = false
                    }
                )
                if (categoryError) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("请选择完整分类", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(8.dp))
                if (showNoteField) {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("备注（可选）") },
                        maxLines = 2
                    )
                } else {
                    Text(
                        "＋ 添加备注（可选）",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showNoteField = true }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val picked = LocalDate.of(year, month + 1, dayOfMonth)
                                if (!picked.isAfter(today)) selectedRecordDate = picked
                            },
                            selectedRecordDate.year,
                            selectedRecordDate.monthValue - 1,
                            selectedRecordDate.dayOfMonth
                        ).apply {
                            datePicker.maxDate = System.currentTimeMillis()
                        }.show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("日期：${selectedRecordDate.format(dateOnlyFormatter)}")
                }

                Spacer(modifier = Modifier.height(12.dp))
                PrimaryActionButton(
                    text = saveButtonText,
                    onClick = {
                        val parsedAmount = amount.toBigDecimalOrNull()
                        if (parsedAmount == null || parsedAmount <= BigDecimal.ZERO) {
                            amountError = "请输入正确金额"
                            return@PrimaryActionButton
                        }
                        if (parentCategory.isBlank() || childCategory.isBlank()) {
                            categoryError = true
                            return@PrimaryActionButton
                        }
                        amountError = null
                        categoryError = false
                        onAddTransaction(
                            amount,
                            isIncome,
                            parentCategory,
                            childCategory,
                            note,
                            selectedRecordDate.atTime(LocalTime.now()).atZone(zone).toInstant().toEpochMilli()
                        )
                        amount = ""
                        note = ""
                        selectedTemplateLabel = ""
                        selectedCategoryShortcut = ""
                        showNoteField = false
                        Toast.makeText(context, "已保存${if (isIncome) "收入" else "支出"} ${MoneyFormat.fromCents((parsedAmount * BigDecimal(100)).toLong())}", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = canSubmit,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}

@Composable
private fun SegmentedSelector(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEachIndexed { index, text ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                    .border(
                        width = 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun QuickActionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surface
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                RoundedCornerShape(999.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}
