package com.example.coin_nest.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.coin_nest.data.db.TransactionEntity
import com.example.coin_nest.data.model.CategoryItem
import com.example.coin_nest.util.MoneyFormat
import java.time.Instant

@Composable
internal fun PendingTransactionRow(
    tx: TransactionEntity,
    onConfirm: () -> Unit,
    onIgnore: () -> Unit
) {
    val smartTag = remember(tx.note) { parseSmartTag(tx.note) }
    val displayNote = remember(tx.note) { userFacingNote(tx.note) }
    val sourceLabel = formatSourceLabel(tx.source)
    val timeText = remember(tx.occurredAtEpochMs) {
        Instant.ofEpochMilli(tx.occurredAtEpochMs).atZone(zone).format(rowTimeFormatter)
    }
    var showNoteDialog by rememberSaveable(tx.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.clickable { showNoteDialog = true },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            val prefix = if (tx.type == "INCOME") "+" else "-"
            val txLabel = if (tx.type == "INCOME") "收入" else "支出"
            Text("待确认$txLabel $prefix${MoneyFormat.fromCents(tx.amountCents)}", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "来源：$sourceLabel · 时间：$timeText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (displayNote.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "备注：$displayNote",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!smartTag.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "智能命中：$smartTag",
                    style = MaterialTheme.typography.bodySmall,
                    color = SuccessColor
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onIgnore, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text("确认入账") }
            }
        }
    }
    if (showNoteDialog) {
        NoteDetailDialog(
            title = "待确认备注",
            source = "$sourceLabel · $timeText",
            note = displayNote,
            onDismiss = { showNoteDialog = false }
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
internal fun TransactionRow(
    tx: TransactionEntity,
    categories: List<CategoryItem> = emptyList(),
    allowCategoryEdit: Boolean = false,
    onUpdateTransaction: ((Long, String, String, String) -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val prefix = if (tx.type == "INCOME") "+" else "-"
    val amountColor = if (tx.type == "INCOME") SuccessColor else DangerColor
    val sourceLabel = formatSourceLabel(tx.source)
    val timeText = remember(tx.occurredAtEpochMs) {
        Instant.ofEpochMilli(tx.occurredAtEpochMs).atZone(zone).format(rowTimeFormatter)
    }
    val smartTag = remember(tx.note) { parseSmartTag(tx.note) }
    val displayNote = remember(tx.note) { userFacingNote(tx.note) }
    var showNoteDialog by rememberSaveable(tx.id) { mutableStateOf(false) }
    var showEditDialog by rememberSaveable(tx.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.clickable { showNoteDialog = true },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$prefix${MoneyFormat.fromCents(tx.amountCents)}",
                    fontWeight = FontWeight.Bold,
                    color = amountColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "${tx.parentCategory}/${tx.childCategory}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "来源：$sourceLabel  时间：$timeText",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (displayNote.isNotBlank()) {
                Text(
                    "备注：$displayNote",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!smartTag.isNullOrBlank()) {
                Text(
                    "智能命中：$smartTag",
                    style = MaterialTheme.typography.bodySmall,
                    color = SuccessColor
                )
            }
            if ((allowCategoryEdit && onUpdateTransaction != null) || onDelete != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (allowCategoryEdit && onUpdateTransaction != null) {
                        Text(
                            "编辑",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showEditDialog = true }
                                .defaultMinSize(minHeight = 44.dp)
                                .padding(horizontal = 8.dp, vertical = 10.dp)
                        )
                    }
                    if (onDelete != null) {
                        Text(
                            "删除",
                            color = DangerColor,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onDelete() }
                                .defaultMinSize(minHeight = 44.dp)
                                .padding(horizontal = 8.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
    if (showNoteDialog) {
        NoteDetailDialog(
            title = "流水备注",
            source = sourceLabel,
            note = displayNote,
            onDismiss = { showNoteDialog = false }
        )
    }
    if (showEditDialog && onUpdateTransaction != null) {
        TransactionEditDialog(
            tx = tx,
            categories = categories,
            onDismiss = { showEditDialog = false },
            onSave = { parent, child, note ->
                onUpdateTransaction(tx.id, parent, child, note)
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun TransactionEditDialog(
    tx: TransactionEntity,
    categories: List<CategoryItem>,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    val grouped = remember(categories) { categories.groupBy { it.parent } }
    val parentOptions = remember(grouped, tx.type) {
        val keys = grouped.keys.sorted()
        if (tx.type == "INCOME") keys.filter { it == "收入" }.ifEmpty { keys } else keys.filter { it != "收入" }.ifEmpty { keys }
    }
    var parentExpanded by rememberSaveable(tx.id) { mutableStateOf(false) }
    var childExpanded by rememberSaveable(tx.id) { mutableStateOf(false) }
    var parent by rememberSaveable(tx.id) { mutableStateOf(tx.parentCategory) }
    var child by rememberSaveable(tx.id) { mutableStateOf(tx.childCategory) }
    var note by rememberSaveable(tx.id) { mutableStateOf(userFacingNote(tx.note)) }
    val childOptions = remember(parent, grouped) { grouped[parent].orEmpty().map { it.child }.distinct().sorted() }

    LaunchedEffect(parentOptions) {
        if (parent !in parentOptions) parent = parentOptions.firstOrNull().orEmpty()
    }
    LaunchedEffect(childOptions) {
        if (child !in childOptions) child = childOptions.firstOrNull().orEmpty()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑流水", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = MoneyFormat.fromCents(tx.amountCents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
                ReadonlyDropdownField(
                    value = parent,
                    label = "一级分类",
                    expanded = parentExpanded,
                    onExpandedChange = { parentExpanded = it },
                    options = parentOptions,
                    onOptionSelected = { selected ->
                        parent = selected
                        child = grouped[selected]?.firstOrNull()?.child.orEmpty()
                    }
                )
                ReadonlyDropdownField(
                    value = child,
                    label = "二级分类",
                    expanded = childExpanded,
                    onExpandedChange = { childExpanded = it },
                    options = childOptions,
                    onOptionSelected = { child = it }
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备注") },
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(parent, child, note) },
                enabled = parent.isNotBlank() && child.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
