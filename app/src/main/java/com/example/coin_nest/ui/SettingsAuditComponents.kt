package com.example.coin_nest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.coin_nest.autobook.AutoBookAuditEvent

@Composable
internal fun AutoBookAuditEventRow(
    event: AutoBookAuditEvent,
    compact: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${auditPackageLabel(event.packageName)} · ${auditEventLabel(event.event)}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (compact) formatEpoch(event.occurredAtEpochMs) else formatFullEpoch(event.occurredAtEpochMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val reason = if (compact) {
            auditReasonLabel(event.reason.ifBlank { event.event })
        } else {
            event.reason.ifBlank { event.event }
        }
        if (reason.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun auditEventLabel(event: String): String {
    return when (event) {
        "notify_received" -> "收到通知"
        "ai_decision_accept" -> "决策通过"
        "ai_decision_reject" -> "决策拒绝"
        "payment_recognized" -> "解析成功"
        "insert_success", "accessibility_insert_success" -> "已入队"
        "insert_drop", "accessibility_insert_drop" -> "入库拦截"
        "parse_failed", "accessibility_parse_failed" -> "解析失败"
        "accessibility_detected" -> "识别到页面"
        "accessibility_parse_start" -> "开始解析"
        "accessibility_drop" -> "识别拦截"
        "listener_connected" -> "监听已连接"
        "accessibility_connected" -> "无障碍已连接"
        else -> event
    }
}

private fun auditReasonLabel(reason: String): String {
    val upper = reason.uppercase()
    return when {
        upper.contains("AUTO_DUPLICATE_BY_WINDOW") -> "短时间重复，已忽略"
        upper.contains("SAME_SOURCE_DUPLICATE_BY_TXN_REF") -> "同交易号重复，已忽略"
        upper.contains("AI_RELATED_LINKED") || upper.contains("CROSS_SOURCE_LINKED") -> "跨渠道重复，已合并"
        upper.contains("NON") || reason.contains("非支付") -> reason.take(32)
        upper.contains("INSERTED") -> "进入待确认"
        upper.contains("PARSE") || reason.contains("未提取") || reason.contains("无法判断") -> reason.take(32)
        else -> reason.take(32)
    }
}

private fun auditPackageLabel(packageName: String): String {
    return when (packageName) {
        "com.eg.android.AlipayGphone" -> "支付宝"
        "com.tencent.mm" -> "微信"
        "cmb.pb", "com.chinamworld.main", "com.icbc" -> "银行卡"
        "com.unionpay" -> "云闪付"
        "" -> "本机"
        else -> packageName.take(16)
    }
}
