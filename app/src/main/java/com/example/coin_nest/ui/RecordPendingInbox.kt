package com.example.coin_nest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.example.coin_nest.autobook.PaymentActionNotifier
import com.example.coin_nest.data.db.TransactionEntity
import java.time.Instant

@Composable
internal fun PendingAutoInboxCard(
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

internal data class PendingAutoReview(
    val all: List<TransactionEntity>,
    val needsReview: List<TransactionEntity>,
    val duplicateGroups: List<PendingDuplicateGroup>,
    val duplicates: List<TransactionEntity>,
    val recommended: List<TransactionEntity>
)

internal data class PendingDuplicateGroup(
    val keep: TransactionEntity,
    val duplicates: List<TransactionEntity>
)

internal fun buildPendingReviewGroups(items: List<TransactionEntity>): PendingAutoReview {
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

