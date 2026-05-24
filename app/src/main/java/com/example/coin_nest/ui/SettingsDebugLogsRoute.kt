package com.example.coin_nest.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.coin_nest.autobook.AutoBookTelemetry

private val AutoBookDebugLogEvents = setOf(
    "ai_decision_accept",
    "ai_decision_reject",
    "payment_recognized",
    "accessibility_parse_failed",
    "parse_failed"
)

@Composable
internal fun SettingsDebugLogsRoute() {
    val context = LocalContext.current
    var debugLogRefreshTick by rememberSaveable { mutableIntStateOf(0) }
    val debugEvents = remember(debugLogRefreshTick) {
        AutoBookTelemetry.readRecentAuditEvents(context)
            .filter { it.event in AutoBookDebugLogEvents }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            GlassCard {
                Text("识别日志", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "本地保留最近 ${debugEvents.size} 条自动识别事件，包含原始内容、解析结果、拦截原因和完整时间。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { debugLogRefreshTick++ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("刷新")
                    }
                    OutlinedButton(
                        onClick = {
                            AutoBookTelemetry.clearRecentAuditEvents(context)
                            debugLogRefreshTick++
                            Toast.makeText(context, "识别日志已清空", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("清空")
                    }
                }
            }
        }
        if (debugEvents.isEmpty()) {
            item {
                GlassCard {
                    Text("暂无识别日志", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "收到通知或无障碍识别到页面内容后，会在这里显示调试记录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(
                items = debugEvents,
                key = { "${it.occurredAtEpochMs}_${it.event}_${it.reason.hashCode()}" }
            ) { event ->
                AutoBookAuditEventRow(event = event, compact = false)
            }
        }
    }
}
