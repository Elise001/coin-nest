package com.example.coin_nest.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun SettingsDataRoute(
    onExportBackup: (onResult: (String) -> Unit, onError: (String) -> Unit) -> Unit,
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
    var replaceExisting by rememberSaveable { mutableStateOf(false) }
    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    var pendingExportBytes by remember { mutableStateOf<Long?>(null) }
    var lastBackupAtMs by rememberSaveable { mutableStateOf<Long?>(null) }
    var lastBackupSizeBytes by rememberSaveable { mutableStateOf<Long?>(null) }
    var lastBackupStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var lastImportAtMs by rememberSaveable { mutableStateOf<Long?>(null) }
    var lastImportStatus by rememberSaveable { mutableStateOf<String?>(null) }
    val formatTime: (Long?) -> String = { epochMs ->
        if (epochMs == null) "暂无"
        else Instant.ofEpochMilli(epochMs).atZone(zoneId).format(timeFormatter)
    }
    val formatFileSize: (Long?) -> String = { size ->
        when {
            size == null -> "--"
            size < 1024 -> "${size} B"
            size < 1024 * 1024 -> String.format(Locale.ROOT, "%.1f KB", size / 1024f)
            else -> String.format(Locale.ROOT, "%.2f MB", size / (1024f * 1024f))
        }
    }
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
                    { error ->
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

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            GlassCard {
                SectionTitle(title = "数据安全中心（本地）")
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "直接导出 JSON 文件；导入时选择 JSON 文件即可。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
