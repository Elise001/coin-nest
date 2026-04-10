package com.example.coin_nest.ui

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.coin_nest.util.MoneyFormat
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsTab(
    state: HomeUiState,
    onAddCategory: (String, String) -> Unit,
    onSetMonthBudget: (String) -> Unit,
    onSetCategoryBudget: (String, String, String) -> Unit,
    onExportBackup: (onResult: (String) -> Unit, onError: (String) -> Unit) -> Unit,
    onClearSmartRules: () -> Unit,
    onImportBackup: (
        json: String,
        replaceExisting: Boolean,
        onResult: (Int, Int) -> Unit,
        onError: (String) -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    val zoneId = remember { ZoneId.systemDefault() }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("MM-dd HH:mm") }
    var budget by rememberSaveable { mutableStateOf("") }
    var budgetError by rememberSaveable { mutableStateOf<String?>(null) }
    var newParent by rememberSaveable { mutableStateOf("") }
    var newChild by rememberSaveable { mutableStateOf("") }
    var budgetParentExpanded by rememberSaveable { mutableStateOf(false) }
    var budgetChildExpanded by rememberSaveable { mutableStateOf(false) }
    var budgetParent by rememberSaveable { mutableStateOf("") }
    var budgetChild by rememberSaveable { mutableStateOf("") }
    var categoryBudgetAmount by rememberSaveable { mutableStateOf("") }
    var replaceExisting by rememberSaveable { mutableStateOf(false) }
    var showClearSmartRuleConfirm by rememberSaveable { mutableStateOf(false) }
    var autoBookHealthRefreshTick by rememberSaveable { mutableIntStateOf(0) }
    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    var pendingExportBytes by remember { mutableStateOf<Long?>(null) }
    var lastBackupAtMs by rememberSaveable { mutableStateOf<Long?>(null) }
    var lastBackupSizeBytes by rememberSaveable { mutableStateOf<Long?>(null) }
    var lastBackupStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var lastImportAtMs by rememberSaveable { mutableStateOf<Long?>(null) }
    var lastImportStatus by rememberSaveable { mutableStateOf<String?>(null) }
    val profileEntries = remember {
        listOf(
            ProfileNavEntry("自动记账与权限", "通知监听、后台保活", "autobook"),
            ProfileNavEntry("预算与分类", "月预算、分类预算、新增分类", "budget"),
            ProfileNavEntry("数据与备份", "导出、导入与恢复", "data")
        )
    }
    val formatTime: (Long?) -> String = { epochMs ->
        if (epochMs == null) "暂无"
        else Instant.ofEpochMilli(epochMs).atZone(zoneId).format(timeFormatter)
    }
    val formatFileSize: (Long?) -> String = { size ->
        when {
            size == null -> "--"
            size < 1024 -> "${size} B"
            size < 1024 * 1024 -> String.format("%.1f KB", size / 1024f)
            else -> String.format("%.2f MB", size / (1024f * 1024f))
        }
    }
    val settingsNav = rememberNavController()
    val exportJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = pendingExportJson
        if (uri != null && !json.isNullOrBlank()) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                lastBackupAtMs = System.currentTimeMillis()
                lastBackupSizeBytes = pendingExportBytes
                lastBackupStatus = "导出成功"
                Toast.makeText(context, "备份文件已导出", Toast.LENGTH_SHORT).show()
            }.onFailure {
                lastBackupStatus = "导出失败"
                Toast.makeText(context, "导出失败：${it.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
            }
        }
        pendingExportJson = null
        pendingExportBytes = null
    }
    val importJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        }.onSuccess { json ->
            if (json.isBlank()) {
                Toast.makeText(context, "文件为空", Toast.LENGTH_SHORT).show()
            } else {
                onImportBackup(
                    json,
                    replaceExisting,
                    { txCount, catCount ->
                        lastImportAtMs = System.currentTimeMillis()
                        lastImportStatus = "导入成功：$txCount 笔记录 / $catCount 个分类"
                        Toast.makeText(context, "导入完成：$txCount 笔记录，$catCount 个分类", Toast.LENGTH_SHORT).show()
                    },
                    {
                        error ->
                        lastImportAtMs = System.currentTimeMillis()
                        lastImportStatus = "导入失败：$error"
                        Toast.makeText(context, "导入失败：$error", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }.onFailure {
            lastImportAtMs = System.currentTimeMillis()
            lastImportStatus = "读取失败：${it.message ?: "未知错误"}"
            Toast.makeText(context, "读取文件失败：${it.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
        }
    }
    val autoBookHealth = remember(autoBookHealthRefreshTick) { getAutoBookHealthStatus(context) }
    val healthPassRate = remember(autoBookHealth) {
        val checks = listOf(
            autoBookHealth.hints.any { it.contains("通知监听权限：已开启") },
            autoBookHealth.hints.any { it.contains("Coin Nest 通知权限：已开启") },
            autoBookHealth.hints.any { it.contains("微信 支付通知：已开启") || it.contains("微信 支付通知：已开启或系统限制无法检测") },
            autoBookHealth.hints.any { it.contains("支付宝 支付通知：已开启") || it.contains("支付宝 支付通知：已开启或系统限制无法检测") }
        )
        checks.count { it }.toFloat() / checks.size.toFloat()
    }
    val canUpdateBudget = remember(budget) {
        val parsed = budget.toBigDecimalOrNull()
        parsed != null && parsed > BigDecimal.ZERO
    }
    val grouped = remember(state.categories) { state.categories.groupBy { it.parent } }
    val budgetParentOptions = remember(grouped) { grouped.keys.sorted() }
    val budgetChildOptions = remember(budgetParent, grouped) { grouped[budgetParent].orEmpty().map { it.child }.distinct().sorted() }
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

    NavHost(
        navController = settingsNav,
        startDestination = "overview",
        modifier = Modifier.fillMaxSize()
    ) {
        composable("overview") {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    GlassCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { settingsNav.navigate("profile_detail") },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .width(52.dp)
                                        .height(52.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("zh", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text("已记账${state.retentionFeedback.activeDaysInSelectedMonth}天", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MetricPill(label = "自动记账", value = if (autoBookHealth.healthy) "可用" else "待修复", modifier = Modifier.weight(1f))
                                MetricPill(label = "预算", value = state.monthBudgetCents?.let { MoneyFormat.fromCents(it) } ?: "未设置", modifier = Modifier.weight(1f))
                                MetricPill(label = "学习规则", value = "${state.smartLearningStatus.totalRules}条", modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                item {
                    GlassCard {
                        profileEntries.forEachIndexed { index, entry ->
                            ProfileEntryCard(
                                title = entry.title,
                                subtitle = entry.subtitle
                            ) {
                                settingsNav.navigate(entry.route)
                            }
                            if (index != profileEntries.lastIndex) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
                item {
                    CompactSmartLearningCard(
                        status = state.smartLearningStatus,
                        onOpenDetail = { settingsNav.navigate("learning_detail") }
                    )
                }
            }
        }
        composable("autobook") {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    GlassCard {
                        Text("自动记账", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("通过监听支付消息通知自动提取交易金额。请先做一次状态体检。")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("健康体检进度 ${(healthPassRate * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(progress = { healthPassRate.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(8.dp)))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(if (autoBookHealth.healthy) "自动记账状态：可用" else "自动记账状态：待修复", color = if (autoBookHealth.healthy) SuccessColor else DangerColor, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        autoBookHealth.hints.forEach { hint -> Text("• $hint", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Spacer(modifier = Modifier.height(10.dp))
                        PrimaryActionButton(text = "打开通知监听权限", onClick = { when (checkAndOpenNotificationListenerPermission(context)) { ListenerPermissionActionResult.ALREADY_ENABLED -> Toast.makeText(context, "通知监听权限已开启", Toast.LENGTH_SHORT).show(); ListenerPermissionActionResult.OPENED_SETTINGS -> Toast.makeText(context, "请开启 Coin Nest 通知监听权限", Toast.LENGTH_SHORT).show() }; autoBookHealthRefreshTick++ }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), containerColor = WarningColor)
                        Spacer(modifier = Modifier.height(8.dp))
                        PrimaryActionButton(text = "打开微信支付通知", onClick = { when (checkAndOpenPaymentNotificationSettings(context, WECHAT_PACKAGE_NAME)) { PaymentNotifyActionResult.ALREADY_ENABLED -> Toast.makeText(context, "微信通知已开启", Toast.LENGTH_SHORT).show(); PaymentNotifyActionResult.OPENED_SETTINGS -> Toast.makeText(context, "请在微信通知设置页确认已开启支付通知", Toast.LENGTH_SHORT).show(); PaymentNotifyActionResult.APP_NOT_INSTALLED -> Toast.makeText(context, "未检测到微信", Toast.LENGTH_SHORT).show() }; autoBookHealthRefreshTick++ }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), containerColor = WarningColor)
                        Spacer(modifier = Modifier.height(8.dp))
                        PrimaryActionButton(text = "打开支付宝支付通知", onClick = { when (checkAndOpenPaymentNotificationSettings(context, ALIPAY_PACKAGE_NAME)) { PaymentNotifyActionResult.ALREADY_ENABLED -> Toast.makeText(context, "支付宝通知已开启", Toast.LENGTH_SHORT).show(); PaymentNotifyActionResult.OPENED_SETTINGS -> Toast.makeText(context, "请在支付宝通知设置页确认已开启支付通知", Toast.LENGTH_SHORT).show(); PaymentNotifyActionResult.APP_NOT_INSTALLED -> Toast.makeText(context, "未检测到支付宝", Toast.LENGTH_SHORT).show() }; autoBookHealthRefreshTick++ }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), containerColor = WarningColor)
                        Spacer(modifier = Modifier.height(8.dp))
                        PrimaryActionButton(text = "允许后台保活（可选）", onClick = { try { val pm = context.getSystemService(PowerManager::class.java); if (pm != null && !pm.isIgnoringBatteryOptimizations(context.packageName)) { val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${context.packageName}") }; context.startActivity(intent); Toast.makeText(context, "请允许忽略电池优化", Toast.LENGTH_SHORT).show() } else { Toast.makeText(context, "后台保活已开启", Toast.LENGTH_SHORT).show() } } catch (_: Exception) { Toast.makeText(context, "打开失败", Toast.LENGTH_SHORT).show() }; autoBookHealthRefreshTick++ }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), containerColor = WarningColor)
                    }
                }
            }
        }
        composable("budget") {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    GlassCard {
                        SectionTitle(title = "预算设置")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("当前预算：${state.monthBudgetCents?.let { MoneyFormat.fromCents(it) } ?: "未设置"}")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = budget, onValueChange = { budget = it; if (budgetError != null) budgetError = null }, modifier = Modifier.fillMaxWidth(), label = { Text("本月预算（元）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = budgetError != null, supportingText = { budgetError?.let { msg -> Text(msg) } })
                        Spacer(modifier = Modifier.height(10.dp))
                        PrimaryActionButton(text = "更新预算", onClick = { val parsed = budget.toBigDecimalOrNull(); if (parsed == null || parsed <= BigDecimal.ZERO) { budgetError = "请输入正确预算"; return@PrimaryActionButton }; onSetMonthBudget(budget); budget = ""; Toast.makeText(context, "预算已更新", Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth(), enabled = canUpdateBudget)
                    }
                }
                item {
                    GlassCard {
                        SectionTitle(title = "分类预算")
                        Spacer(modifier = Modifier.height(8.dp))
                        ReadonlyDropdownField(value = budgetParent, label = "一级分类", expanded = budgetParentExpanded, onExpandedChange = { budgetParentExpanded = it }, options = budgetParentOptions, onOptionSelected = { parent -> budgetParent = parent; budgetChild = grouped[parent].orEmpty().firstOrNull()?.child.orEmpty() })
                        Spacer(modifier = Modifier.height(8.dp))
                        ReadonlyDropdownField(value = budgetChild, label = "二级分类", expanded = budgetChildExpanded, onExpandedChange = { budgetChildExpanded = it }, options = budgetChildOptions, onOptionSelected = { child -> budgetChild = child })
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = categoryBudgetAmount, onValueChange = { categoryBudgetAmount = it }, modifier = Modifier.fillMaxWidth(), label = { Text("该分类预算（元）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        Spacer(modifier = Modifier.height(8.dp))
                        PrimaryActionButton(text = "保存分类预算", onClick = { onSetCategoryBudget(budgetParent, budgetChild, categoryBudgetAmount); Toast.makeText(context, "分类预算已更新", Toast.LENGTH_SHORT).show(); categoryBudgetAmount = "" }, enabled = canSetCategoryBudget, modifier = Modifier.fillMaxWidth())
                        if (categoryBudgetUsage.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            SectionTitle(title = "本月分类预算进度")
                            Spacer(modifier = Modifier.height(6.dp))
                            categoryBudgetUsage.take(6).forEach { (item, used) ->
                                val ratio = if (item.limitCents <= 0) 0f else (used.toFloat() / item.limitCents.toFloat()).coerceAtLeast(0f)
                                Text("${item.parentCategory}/${item.childCategory}  ${MoneyFormat.fromCents(used)} / ${MoneyFormat.fromCents(item.limitCents)}")
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(progress = { ratio.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(8.dp)))
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }
                }
                item {
                    GlassCard {
                        SectionTitle(title = "新增二级分类")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = newParent, onValueChange = { newParent = it }, modifier = Modifier.fillMaxWidth(), label = { Text("一级分类") }, singleLine = true)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = newChild, onValueChange = { newChild = it }, modifier = Modifier.fillMaxWidth(), label = { Text("二级分类") }, singleLine = true)
                        Spacer(modifier = Modifier.height(10.dp))
                        PrimaryActionButton(text = "添加分类", onClick = { onAddCategory(newParent, newChild); newParent = ""; newChild = "" }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        composable("data") {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    GlassCard {
                        SectionTitle(title = "数据安全中心（本地）")
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("直接导出 JSON 文件；导入时选择 JSON 文件即可。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                onExportBackup(
                                    { json ->
                                        pendingExportJson = json
                                        pendingExportBytes = json.toByteArray(Charsets.UTF_8).size.toLong()
                                        exportJsonLauncher.launch("coin_nest_backup_${System.currentTimeMillis()}.json")
                                    },
                                    { error -> Toast.makeText(context, "导出失败：$error", Toast.LENGTH_SHORT).show() }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("导出 JSON 文件") }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = replaceExisting, onCheckedChange = { replaceExisting = it })
                            Text("导入前清空现有数据（谨慎）", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        PrimaryActionButton(
                            text = "导入 JSON 文件",
                            onClick = { importJsonLauncher.launch(arrayOf("application/json", "text/plain")) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("最近备份：${formatTime(lastBackupAtMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("备份大小：${formatFileSize(lastBackupSizeBytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("备份状态：${lastBackupStatus ?: "未执行"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("最近恢复：${formatTime(lastImportAtMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("恢复状态：${lastImportStatus ?: "未执行"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        composable("profile_detail") {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    GlassCard {
                        Text("账号信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricPill(label = "昵称", value = "zh", modifier = Modifier.weight(1f))
                            MetricPill(label = "品牌", value = "Coin Nest", modifier = Modifier.weight(1f))
                        }
                    }
                }
                item {
                    GlassCard {
                        Text("记录概览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricPill(label = "本月活跃", value = "${state.retentionFeedback.activeDaysInSelectedMonth}天", modifier = Modifier.weight(1f))
                            MetricPill(label = "当前连记", value = "${state.retentionFeedback.currentStreakDays}天", modifier = Modifier.weight(1f))
                            MetricPill(label = "最长连记", value = "${state.retentionFeedback.longestStreakDays}天", modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        composable("learning_detail") {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    SmartLearningStatusCard(
                        status = state.smartLearningStatus,
                        onClearRules = { showClearSmartRuleConfirm = true }
                    )
                }
            }
        }
    }

    if (showClearSmartRuleConfirm) {
        AlertDialog(
            onDismissRequest = { showClearSmartRuleConfirm = false },
            title = { Text("清空学习规则？") },
            text = { Text("此操作会删除本地智能分类学习结果，但不会删除已有流水。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearSmartRules()
                        showClearSmartRuleConfirm = false
                        Toast.makeText(context, "已清空智能分类学习规则", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("确认清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearSmartRuleConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SmartLearningStatusCard(
    status: SmartLearningStatus,
    onClearRules: () -> Unit
) {
    GlassCard {
        Text("智能分类学习状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        if (status.totalRules <= 0) {
            Text("当前暂无学习规则。先在流水里手动改几次分类，系统会自动学习。")
            return@GlassCard
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricPill(label = "已学习规则", value = "${status.totalRules}条", modifier = Modifier.weight(1f))
            MetricPill(label = "高置信规则", value = "${status.highConfidenceRules}条", modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "近7天学习热度",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        SmartLearningTrendBars(hits = status.recent7DayHits)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "高频关键词",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        status.topKeywords.forEach { item ->
            Text(
                "• ${item.keyword}（${item.hitCount}）→ ${item.categoryPath}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onClearRules, modifier = Modifier.fillMaxWidth()) {
            Text("清空学习规则")
        }
    }
}

@Composable
private fun SmartLearningTrendBars(hits: List<Int>) {
    val safeHits = if (hits.size == 7) hits else List(7) { 0 }
    val maxHit = safeHits.maxOrNull()?.coerceAtLeast(1) ?: 1
    val dayLabels = listOf("D-6", "D-5", "D-4", "D-3", "D-2", "D-1", "今天")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        safeHits.forEachIndexed { index, hit ->
            val ratio = (hit.toFloat() / maxHit.toFloat()).coerceIn(0.15f, 1f)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = hit.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((32f * ratio).dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = dayLabels[index],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CompactSmartLearningCard(
    status: SmartLearningStatus,
    onOpenDetail: () -> Unit
) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("智能分类学习", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "查看详情 >",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onOpenDetail() }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "规则 ${status.totalRules} 条  ·  高置信 ${status.highConfidenceRules} 条  ·  热词 ${status.topKeywords.size} 个",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        CompactLearningTrend(hits = status.recent7DayHits)
    }
}

@Composable
private fun CompactLearningTrend(hits: List<Int>) {
    val safeHits = if (hits.size == 7) hits else List(7) { 0 }
    val maxHit = safeHits.maxOrNull()?.coerceAtLeast(1) ?: 1
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        safeHits.forEach { hit ->
            val ratio = (hit.toFloat() / maxHit.toFloat()).coerceIn(0f, 1f)
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (ratio > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((22f * ratio).dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(hit.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}


private data class ProfileNavEntry(
    val title: String,
    val subtitle: String,
    val route: String
)

@Composable
private fun ProfileEntryCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(">", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), fontWeight = FontWeight.SemiBold)
        }
    }
}


