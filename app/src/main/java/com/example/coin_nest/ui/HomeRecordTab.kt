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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.example.coin_nest.autobook.PaymentActionNotifier
import com.example.coin_nest.data.db.TransactionEntity
import com.example.coin_nest.util.MoneyFormat
import com.example.coin_nest.util.MoneyParser
import java.time.Instant
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
                PendingAutoInboxCard(
                    review = pendingReview,
                    onConfirmPendingAuto = onConfirmPendingAuto,
                    onIgnorePendingAuto = onIgnorePendingAuto
                )
            }
        }

        item {
            GlassCard {
                SectionTitle(title = "记账状态", subtitle = "先看节奏，再补记录")
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricPill(
                        label = "今日支出",
                        value = MoneyFormat.fromCents(state.daily.expenseCents),
                        modifier = Modifier.weight(1f)
                    )
                    MetricPill(
                        label = "本月支出",
                        value = MoneyFormat.fromCents(state.selectedMonthSummary.expenseCents),
                        modifier = Modifier.weight(1f)
                    )
                    MetricPill(
                        label = "待确认",
                        value = "${state.pendingAutoTransactions.size}条",
                        modifier = Modifier.weight(1f)
                    )
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
                        selectedCategoryShortcut = ""
                        showNoteField = false
                        Toast.makeText(context, "已保存${if (isIncome) "收入" else "支出"} ${MoneyFormat.fromCents(amountCents)}", Toast.LENGTH_SHORT).show()
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
private fun PendingAutoInboxCard(
    review: PendingAutoReview,
    onConfirmPendingAuto: (Long) -> Unit,
    onIgnorePendingAuto: (Long) -> Unit
) {
    val context = LocalContext.current
    fun clearNotifications(tx: TransactionEntity, pendingCountAfterAction: Int) {
        NotificationManagerCompat.from(context).cancel(tx.id.toInt())
        if (pendingCountAfterAction <= 0) {
            PaymentActionNotifier.clearPendingAggregateNotification(context)
        }
    }

    fun ignoreAll() {
        review.all.forEach { tx ->
            onIgnorePendingAuto(tx.id)
            NotificationManagerCompat.from(context).cancel(tx.id.toInt())
        }
        PaymentActionNotifier.clearPendingAggregateNotification(context)
    }

    fun confirmRecommended() {
        val targets = review.recommended.ifEmpty { review.all.takeIf { review.needsReview.isEmpty() && review.duplicates.isEmpty() }.orEmpty() }
        targets.forEach { tx ->
            onConfirmPendingAuto(tx.id)
            NotificationManagerCompat.from(context).cancel(tx.id.toInt())
        }
        if (targets.size >= review.all.size) {
            PaymentActionNotifier.clearPendingAggregateNotification(context)
        }
    }

    fun mergeDuplicateGroups() {
        val keepers = review.duplicateGroups.map { it.keep }
        val duplicateItems = review.duplicateGroups.flatMap { it.duplicates }
        keepers.forEach { tx ->
            onConfirmPendingAuto(tx.id)
            NotificationManagerCompat.from(context).cancel(tx.id.toInt())
        }
        duplicateItems.forEach { tx ->
            onIgnorePendingAuto(tx.id)
            NotificationManagerCompat.from(context).cancel(tx.id.toInt())
        }
        if (keepers.size + duplicateItems.size >= review.all.size) {
            PaymentActionNotifier.clearPendingAggregateNotification(context)
        }
    }

    GlassCard {
        Text("待确认收件箱", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "自动识别先入队，不逐笔打扰。建议检查项先看一眼，再批量处理。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricPill(label = "待确认", value = "${review.all.size}条", modifier = Modifier.weight(1f))
            MetricPill(label = "建议检查", value = "${review.needsReview.size}条", modifier = Modifier.weight(1f))
            MetricPill(label = "疑似重复", value = "${review.duplicates.size}条", modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = ::ignoreAll, modifier = Modifier.weight(1f)) { Text("全部取消") }
            Button(
                onClick = ::confirmRecommended,
                modifier = Modifier.weight(1f),
                enabled = review.recommended.isNotEmpty() || (review.needsReview.isEmpty() && review.duplicates.isEmpty())
            ) {
                Text(if (review.recommended.isEmpty()) "全部确认" else "确认可确认")
            }
        }
        if (review.duplicateGroups.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = ::mergeDuplicateGroups, modifier = Modifier.fillMaxWidth()) {
                Text("合并疑似重复（保留最新）")
            }
        }

        PendingSection(
            title = "建议检查",
            subtitle = "包含优惠、理财、红包等敏感词，建议确认后再入账",
            items = review.needsReview,
            totalPendingCount = review.all.size,
            onConfirmPendingAuto = onConfirmPendingAuto,
            onIgnorePendingAuto = onIgnorePendingAuto,
            onClearNotifications = ::clearNotifications
        )
        PendingSection(
            title = "疑似重复",
            subtitle = "支付平台与银行卡可能同时捕获同一笔，建议只保留一条",
            items = review.duplicates,
            reasonLabel = "原因：金额、收支类型和时间接近",
            totalPendingCount = review.all.size,
            onConfirmPendingAuto = onConfirmPendingAuto,
            onIgnorePendingAuto = onIgnorePendingAuto,
            onClearNotifications = ::clearNotifications
        )
        PendingSection(
            title = "可确认",
            subtitle = "没有明显风险，可批量确认",
            items = review.recommended,
            totalPendingCount = review.all.size,
            onConfirmPendingAuto = onConfirmPendingAuto,
            onIgnorePendingAuto = onIgnorePendingAuto,
            onClearNotifications = ::clearNotifications
        )
    }
}

@Composable
private fun PendingSection(
    title: String,
    subtitle: String,
    items: List<TransactionEntity>,
    reasonLabel: String? = null,
    totalPendingCount: Int,
    onConfirmPendingAuto: (Long) -> Unit,
    onIgnorePendingAuto: (Long) -> Unit,
    onClearNotifications: (TransactionEntity, Int) -> Unit
) {
    if (items.isEmpty()) return
    Spacer(modifier = Modifier.height(12.dp))
    SectionTitle(title = "$title（${items.size}）", subtitle = subtitle)
    Spacer(modifier = Modifier.height(8.dp))
    items.take(6).forEachIndexed { index, tx ->
        if (!reasonLabel.isNullOrBlank()) {
            Text(
                reasonLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
            )
        }
        PendingTransactionRow(
            tx = tx,
            onConfirm = {
                onConfirmPendingAuto(tx.id)
                onClearNotifications(tx, totalPendingCount - 1)
            },
            onIgnore = {
                onIgnorePendingAuto(tx.id)
                onClearNotifications(tx, totalPendingCount - 1)
            }
        )
        if (index < items.lastIndex.coerceAtMost(5)) Spacer(modifier = Modifier.height(2.dp))
    }
    if (items.size > 6) {
        Spacer(modifier = Modifier.height(4.dp))
        Text("还有 ${items.size - 6} 条在队列中", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class PendingAutoReview(
    val all: List<TransactionEntity>,
    val needsReview: List<TransactionEntity>,
    val duplicateGroups: List<PendingDuplicateGroup>,
    val duplicates: List<TransactionEntity>,
    val recommended: List<TransactionEntity>
)

private data class PendingDuplicateGroup(
    val keep: TransactionEntity,
    val duplicates: List<TransactionEntity>
)

private fun buildPendingReviewGroups(items: List<TransactionEntity>): PendingAutoReview {
    val sensitive = items.filter { looksRiskyPendingAuto(it) }.toSet()
    val duplicateGroups = items
        .filterNot { it in sensitive }
        .groupBy { pendingDuplicateKey(it) }
        .values
        .filter { group -> group.size > 1 }
        .map { group ->
            val sorted = group.sortedByDescending { it.createdAtEpochMs }
            PendingDuplicateGroup(keep = sorted.first(), duplicates = sorted.drop(1))
        }
    val duplicateIds = duplicateGroups
        .flatMap { it.duplicates }
        .map { it.id }
        .toSet()
    val duplicates = duplicateGroups.flatMap { it.duplicates }
    val recommended = items.filter { it !in sensitive && it.id !in duplicateIds }
    return PendingAutoReview(
        all = items,
        needsReview = items.filter { it in sensitive },
        duplicateGroups = duplicateGroups,
        duplicates = duplicates,
        recommended = recommended
    )
}

private fun looksRiskyPendingAuto(tx: TransactionEntity): Boolean {
    val text = "${tx.note} ${tx.parentCategory} ${tx.childCategory}"
    val sensitiveKeywords = listOf(
        "优惠券", "券包", "卡券", "红包", "积分", "余额宝", "基金", "理财", "申购", "赎回", "收益", "分红", "净值",
        "持仓", "体验金", "确认份额", "买入成功", "卖出成功"
    )
    return sensitiveKeywords.any { text.contains(it, ignoreCase = true) }
}

private fun pendingDuplicateKey(tx: TransactionEntity): String {
    val minuteBucket = Instant.ofEpochMilli(tx.occurredAtEpochMs).epochSecond / 120
    return "${pendingDuplicateSourceGroup(tx.source)}|${tx.type}|${tx.amountCents}|$minuteBucket"
}

private fun pendingDuplicateSourceGroup(source: String): String {
    return when (source.uppercase()) {
        "ALIPAY", "WECHAT", "BANK_CARD", "CREDIT_CARD", "UNIONPAY" -> "PAYMENT_RAIL"
        else -> source.uppercase()
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
                    .heightIn(min = 44.dp)
                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                    .border(
                        width = 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .semantics {
                        role = Role.Tab
                        this.selected = selected
                    }
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
            .defaultMinSize(minHeight = 44.dp)
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
            .semantics {
                role = Role.Button
                this.selected = selected
            }
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
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
