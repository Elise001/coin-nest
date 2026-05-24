package com.example.coin_nest.ui

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.coin_nest.autobook.AutoBookTelemetry

@Composable
internal fun SettingsAutoBookRoute() {
    val context = LocalContext.current
    var autoBookHealthRefreshTick by rememberSaveable { mutableIntStateOf(0) }
    var showAutoBookDiagnostics by rememberSaveable { mutableStateOf(false) }
    val autoBookHealth = remember(autoBookHealthRefreshTick) { getAutoBookHealthStatus(context) }
    val requiredChecks = autoBookHealth.requiredChecks
    val requiredDone = requiredChecks.count { it.state == AutoBookCheckState.PASS }
    val remaining = (requiredChecks.size - requiredDone).coerceAtLeast(0)
    val healthPassRate = if (requiredChecks.isEmpty()) {
        0f
    } else {
        requiredDone.toFloat() / requiredChecks.size.toFloat()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            GlassCard {
                Text("自动记账", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "必要权限完成度 ${(healthPassRate * 100).toInt()}% · 还差 $remaining/${requiredChecks.size} 项",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { healthPassRate.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
                AutoBookStatusPill(
                    statusText = if (autoBookHealth.healthy) "自动记账状态：可用" else "自动记账状态：待修复",
                    healthy = autoBookHealth.healthy
                )
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = { showAutoBookDiagnostics = !showAutoBookDiagnostics },
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                ) {
                    Text(if (showAutoBookDiagnostics) "收起诊断详情" else "展开诊断详情")
                }
                if (showAutoBookDiagnostics) {
                    Spacer(modifier = Modifier.height(6.dp))
                    autoBookHealth.diagnostics.take(4).forEach { hint ->
                        Text("• $hint", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val recentAuditEvents = remember(autoBookHealthRefreshTick) {
                        AutoBookTelemetry.readRecentAuditEvents(context)
                    }
                    if (recentAuditEvents.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("最近自动记账事件", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        recentAuditEvents.take(5).forEach { event ->
                            AutoBookAuditEventRow(event)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("必要权限", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                PermissionActionRow(
                    done = requiredChecks.getOrNull(0)?.state == AutoBookCheckState.PASS,
                    actionText = "去开启通知监听权限",
                    doneText = "通知监听权限已开启",
                    onClick = {
                        when (checkAndOpenNotificationListenerPermission(context)) {
                            ListenerPermissionActionResult.ALREADY_ENABLED -> Toast.makeText(context, "通知监听权限已开启", Toast.LENGTH_SHORT).show()
                            ListenerPermissionActionResult.OPENED_SETTINGS -> Toast.makeText(context, "请开启 Coin Nest 通知监听权限", Toast.LENGTH_SHORT).show()
                        }
                        autoBookHealthRefreshTick++
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                PermissionActionRow(
                    done = requiredChecks.getOrNull(1)?.state == AutoBookCheckState.PASS,
                    actionText = "去开启微信支付通知",
                    doneText = "微信支付通知已开启",
                    onClick = {
                        when (checkAndOpenPaymentNotificationSettings(context, WECHAT_PACKAGE_NAME)) {
                            PaymentNotifyActionResult.ALREADY_ENABLED -> Toast.makeText(context, "微信通知已开启", Toast.LENGTH_SHORT).show()
                            PaymentNotifyActionResult.OPENED_SETTINGS -> Toast.makeText(context, "请在微信通知设置页确认已开启支付通知", Toast.LENGTH_SHORT).show()
                            PaymentNotifyActionResult.APP_NOT_INSTALLED -> Toast.makeText(context, "未检测到微信", Toast.LENGTH_SHORT).show()
                        }
                        autoBookHealthRefreshTick++
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                PermissionActionRow(
                    done = requiredChecks.getOrNull(2)?.state == AutoBookCheckState.PASS,
                    actionText = "去开启支付宝支付通知",
                    doneText = "支付宝支付通知已开启",
                    onClick = {
                        when (checkAndOpenPaymentNotificationSettings(context, ALIPAY_PACKAGE_NAME)) {
                            PaymentNotifyActionResult.ALREADY_ENABLED -> Toast.makeText(context, "支付宝通知已开启", Toast.LENGTH_SHORT).show()
                            PaymentNotifyActionResult.OPENED_SETTINGS -> Toast.makeText(context, "请在支付宝通知设置页确认已开启支付通知", Toast.LENGTH_SHORT).show()
                            PaymentNotifyActionResult.APP_NOT_INSTALLED -> Toast.makeText(context, "未检测到支付宝", Toast.LENGTH_SHORT).show()
                        }
                        autoBookHealthRefreshTick++
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("优化项", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                PrimaryActionButton(
                    text = "去开启无障碍功能（提升稳定）",
                    onClick = {
                        when (checkAndOpenAccessibilityPermission(context)) {
                            AccessibilityPermissionActionResult.ALREADY_ENABLED ->
                                Toast.makeText(context, "无障碍识别已开启", Toast.LENGTH_SHORT).show()
                            AccessibilityPermissionActionResult.OPENED_SETTINGS ->
                                Toast.makeText(context, "请在系统无障碍页开启 Coin Nest 无障碍功能", Toast.LENGTH_SHORT).show()
                        }
                        autoBookHealthRefreshTick++
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    containerColor = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                PrimaryActionButton(
                    text = "去开启后台保活（提升稳定）",
                    onClick = {
                        try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = "package:${context.packageName}".toUri()
                                    }
                            context.startActivity(intent)
                            Toast.makeText(context, "请允许忽略电池优化", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {
                            Toast.makeText(context, "打开失败", Toast.LENGTH_SHORT).show()
                        }
                        autoBookHealthRefreshTick++
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    containerColor = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                PrimaryActionButton(
                    text = if (autoBookHealth.isXiaomiFamily) "去开启自启动（小米/红米建议）" else "去检查自启动（如手机支持）",
                    onClick = {
                        val opened = openAutoStartSettings(context)
                        Toast.makeText(
                            context,
                            if (opened) "请在系统页允许 Coin Nest 自启动" else "打开失败，请手动到系统设置开启自启动",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    containerColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
