package com.example.coin_nest.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.coin_nest.util.MoneyFormat
import com.example.coin_nest.util.MoneyParser
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.max

private enum class QuickFillMode { WORKDAY, RESTDAY }

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
    var selectedRecordDate by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var amountError by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryError by rememberSaveable { mutableStateOf(false) }
    var quickFillMode by rememberSaveable {
        mutableStateOf(if (LocalDate.now().dayOfWeek.value in 1..5) QuickFillMode.WORKDAY else QuickFillMode.RESTDAY)
    }
    var showNoteField by rememberSaveable { mutableStateOf(false) }

    val templates = remember {
        listOf(
            RecordTemplate("通勤", "4", false, "工作日", "通勤", "通勤"),
            RecordTemplate("早餐", "8", false, "工作日", "工作餐", "早餐"),
            RecordTemplate("午饭", "35", false, "工作日", "工作餐", "午饭"),
            RecordTemplate("咖啡", "18", false, "工作日", "日常", "咖啡"),
            RecordTemplate("便利店", "16", false, "工作日", "日常", "便利店"),
            RecordTemplate("周末餐", "60", false, "休息日", "休闲餐饮", "周末餐饮"),
            RecordTemplate("奶茶", "18", false, "休息日", "休闲餐饮", "奶茶"),
            RecordTemplate("打车", "35", false, "休息日", "出行", "打车"),
            RecordTemplate("电影", "45", false, "休息日", "日常", "电影"),
            RecordTemplate("购物", "100", false, "休息日", "日常", "周末购物"),
            RecordTemplate("工资", "5000", true, "收入", "工资", "工资入账")
        )
    }
    val shownTemplates = remember(isIncome, quickFillMode, templates) {
        if (isIncome) {
            templates.filter { it.isIncome }
        } else {
            val targetParent = if (quickFillMode == QuickFillMode.WORKDAY) "工作日" else "休息日"
            templates.filter { !it.isIncome && it.parent == targetParent }
        }
    }

    val grouped = remember(state.categories) { state.categories.groupBy { it.parent } }
    val parentOptions = remember(grouped, isIncome) {
        val keys = grouped.keys.toList().sortedWith(categoryParentComparator)
        if (isIncome) keys.filter { it == "收入" }.ifEmpty { keys } else keys.filter { it != "收入" }.ifEmpty { keys }
    }
    val childOptions = remember(parentCategory, grouped) { grouped[parentCategory].orEmpty().map { it.child }.distinct().sorted() }
    val quickAmounts = remember(isIncome) {
        if (isIncome) listOf("5000", "10000", "15000", "20000") else listOf("10", "20", "30", "50", "100")
    }
    val parsedAmountCents = remember(amount) { MoneyParser.parseYuanToCents(amount) }
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
        MoneyParser.parseYuanToCents(amount) != null && parentCategory.isNotBlank() && childCategory.isNotBlank()
    }
    val pendingReview = remember(state.pendingAutoTransactions) {
        buildPendingReviewGroups(state.pendingAutoTransactions)
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
        quickFillMode = if (selectedRecordDate.dayOfWeek.value in 1..5) QuickFillMode.WORKDAY else QuickFillMode.RESTDAY
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (state.pendingAutoTransactions.isNotEmpty()) {
            item {
                PendingAutoInboxCard(
                    review = pendingReview,
                    onConfirmPendingAuto = onConfirmPendingAuto,
                    onIgnorePendingAuto = onIgnorePendingAuto
                )
            }
        }

        item {
            RecordMomentumCard(
                todayExpense = state.daily.expenseCents,
                monthExpense = state.selectedMonthSummary.expenseCents,
                pendingCount = state.pendingAutoTransactions.size,
                isIncome = isIncome,
                amount = amount
            )
        }

        item {
            GlassCard {
                SectionTitle(title = "快速记账", subtitle = "先选收支和模板，再补金额与分类")
                Spacer(modifier = Modifier.height(10.dp))

                SegmentedSelector(
                    options = listOf("支出", "收入"),
                    selectedIndex = if (isIncome) 1 else 0,
                    onSelect = { index -> isIncome = index == 1 }
                )

                Spacer(modifier = Modifier.height(12.dp))
                SectionTitle(title = "常用场景")
                Spacer(modifier = Modifier.height(8.dp))

                SegmentedSelector(
                    options = listOf("工作日", "休息日"),
                    selectedIndex = if (quickFillMode == QuickFillMode.WORKDAY) 0 else 1,
                    onSelect = { index ->
                        quickFillMode = if (index == 0) QuickFillMode.WORKDAY else QuickFillMode.RESTDAY
                        selectedTemplateLabel = ""
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                                amountError = null
                                categoryError = false
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
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
                        val error = amountError
                        if (error != null) Text(error)
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
                    TextButton(onClick = { showNoteField = true }) {
                        Text("添加备注（可选）")
                    }
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
                        val amountCents = MoneyParser.parseYuanToCents(amount)
                        if (amountCents == null) {
                            amountError = "请输入正确金额，最多两位小数"
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
                        showNoteField = false
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
