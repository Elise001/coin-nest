package com.example.coin_nest.ui

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.coin_nest.autobook.AutoBookTelemetry
import com.example.coin_nest.autobook.PaymentAccessibilityService
import com.example.coin_nest.autobook.PaymentNotificationListener
import com.example.coin_nest.data.db.TransactionEntity
import java.time.Instant
import java.time.format.DateTimeFormatter

private val autoSources = setOf(
    "ALIPAY",
    "WECHAT",
    "TAOBAO",
    "MEITUAN",
    "JD",
    "PDD",
    "CREDIT_CARD",
    "BANK_CARD",
    "UNIONPAY",
    "AUTO_NOTIFY",
    "IMAGE_IMPORT",
    "CSV_IMPORT",
    "EXCEL_IMPORT",
    "FILE_IMPORT"
)

internal data class AutoBookHealthStatus(
    val healthy: Boolean,
    val requiredChecks: List<AutoBookCheckItem>,
    val optionalChecks: List<AutoBookCheckItem>,
    val diagnostics: List<String>,
    val isXiaomiFamily: Boolean
)

internal enum class AutoBookCheckState {
    PASS,
    FAIL,
    UNKNOWN
}

internal data class AutoBookCheckItem(
    val title: String,
    val detail: String,
    val state: AutoBookCheckState
)

internal fun shouldAllowCategoryEdit(tx: TransactionEntity): Boolean {
    val isAutoSource = autoSources.contains(tx.source.uppercase())
    val isPendingCategory = tx.parentCategory == "待分类" || tx.childCategory == "自动识别"
    return isAutoSource || isPendingCategory
}

internal fun formatTrendRange(first: String, last: String): String = "$first - $last"

internal fun formatSourceLabel(source: String): String {
    return when (source.uppercase()) {
        "MANUAL" -> "手动记账"
        "ALIPAY" -> "支付宝自动识别"
        "WECHAT" -> "微信自动识别"
        "TAOBAO" -> "淘宝通知识别"
        "MEITUAN" -> "美团通知识别"
        "JD" -> "京东通知识别"
        "PDD" -> "拼多多通知识别"
        "CREDIT_CARD" -> "信用卡账单识别"
        "BANK_CARD" -> "银行卡账单识别"
        "UNIONPAY" -> "云闪付识别"
        "AUTO_NOTIFY" -> "通知自动识别"
        "IMAGE_IMPORT" -> "图片识别导入"
        "CSV_IMPORT" -> "CSV账单导入"
        "EXCEL_IMPORT" -> "Excel账单导入"
        "FILE_IMPORT" -> "文件账单导入"
        "UNKNOWN" -> "图片识别"
        else -> source
    }
}

internal fun formatEpoch(epochMs: Long): String {
    return Instant.ofEpochMilli(epochMs).atZone(zone).format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
}

internal const val WECHAT_PACKAGE_NAME = "com.tencent.mm"
internal const val ALIPAY_PACKAGE_NAME = "com.eg.android.AlipayGphone"

internal enum class PaymentNotifyActionResult {
    ALREADY_ENABLED,
    OPENED_SETTINGS,
    APP_NOT_INSTALLED
}

internal enum class ListenerPermissionActionResult {
    ALREADY_ENABLED,
    OPENED_SETTINGS
}

internal enum class AccessibilityPermissionActionResult {
    ALREADY_ENABLED,
    OPENED_SETTINGS
}

internal fun getAutoBookHealthStatus(context: Context): AutoBookHealthStatus {
    val requiredChecks = mutableListOf<AutoBookCheckItem>()
    val optionalChecks = mutableListOf<AutoBookCheckItem>()
    val diagnostics = mutableListOf<String>()
    val isXiaomi = isXiaomiFamilyDevice()

    val listenerEnabled = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    val selfNotificationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    requiredChecks += if (listenerEnabled) {
        AutoBookCheckItem(
            title = "通知监听权限（必要）",
            detail = "已开启",
            state = AutoBookCheckState.PASS
        )
    } else {
        AutoBookCheckItem(
            title = "通知监听权限（必要）",
            detail = "未开启",
            state = AutoBookCheckState.FAIL
        )
    }

    optionalChecks += if (selfNotificationEnabled) {
        AutoBookCheckItem(
            title = "Coin Nest 通知权限（非必要）",
            detail = "已开启",
            state = AutoBookCheckState.PASS
        )
    } else {
        AutoBookCheckItem(
            title = "Coin Nest 通知权限（非必要）",
            detail = "未开启",
            state = AutoBookCheckState.FAIL
        )
    }

    val lastConnectedMs = AutoBookTelemetry.readLastListenerConnectedMs(context)
    if (lastConnectedMs > 0L) {
        diagnostics += "最近连接：${formatEpoch(lastConnectedMs)}"
    } else {
        diagnostics += "最近连接：暂无"
    }

    val lastNotifyMs = AutoBookTelemetry.readLastNotifyReceivedMs(context)
    val lastNotifyPkg = AutoBookTelemetry.readLastNotifyPackage(context)
    val lastNotifyPreview = AutoBookTelemetry.readLastNotifyPreview(context)
    if (lastNotifyMs > 0L) {
        val pkgLabel = mapPaymentPackageLabel(lastNotifyPkg)
        val previewSuffix = lastNotifyPreview?.takeIf { it.isNotBlank() }?.let { " · ${it.take(20)}" }.orEmpty()
        diagnostics += "最近监听：$pkgLabel ${formatEpoch(lastNotifyMs)}$previewSuffix"
    } else {
        diagnostics += "最近监听：暂无"
    }

    AutoBookTelemetry.readLastReason(context)?.let { reason ->
        diagnostics += "最近异常：${toChineseReason(reason)}"
    }

    val wechatCheck = paymentAppCheck(context, WECHAT_PACKAGE_NAME, "微信支付通知（必要）")
    val alipayCheck = paymentAppCheck(context, ALIPAY_PACKAGE_NAME, "支付宝支付通知（必要）")
    requiredChecks += wechatCheck
    requiredChecks += alipayCheck

    optionalChecks += if (isAccessibilityServiceEnabled(context)) {
        AutoBookCheckItem(
            title = "支付成功页识别（无障碍）",
            detail = "已开启",
            state = AutoBookCheckState.PASS
        )
    } else {
        AutoBookCheckItem(
            title = "支付成功页识别（无障碍）",
            detail = "建议开启",
            state = AutoBookCheckState.UNKNOWN
        )
    }

    val batteryIgnored = isIgnoringBatteryOptimizations(context)
    optionalChecks += when (batteryIgnored) {
        true -> AutoBookCheckItem(
            title = "后台保活（非必要）",
            detail = "已开启",
            state = AutoBookCheckState.PASS
        )
        false -> AutoBookCheckItem(
            title = "后台保活（非必要）",
            detail = "未开启",
            state = AutoBookCheckState.FAIL
        )
        null -> AutoBookCheckItem(
            title = "后台保活（非必要）",
            detail = "待确认",
            state = AutoBookCheckState.UNKNOWN
        )
    }

    if (isXiaomi) {
        optionalChecks += AutoBookCheckItem(
            title = "自启动（小米/红米强烈建议）",
            detail = "建议开启",
            state = AutoBookCheckState.UNKNOWN
        )
        diagnostics += "小米/红米：优先开启自启动"
    }

    val healthy = requiredChecks.all { it.state == AutoBookCheckState.PASS }

    return AutoBookHealthStatus(
        healthy = healthy,
        requiredChecks = requiredChecks,
        optionalChecks = optionalChecks,
        diagnostics = diagnostics,
        isXiaomiFamily = isXiaomi
    )
}

private fun paymentAppCheck(context: Context, packageName: String, label: String): AutoBookCheckItem {
    val appInfo = runCatching { context.packageManager.getApplicationInfo(packageName, 0) }.getOrNull()
        ?: return AutoBookCheckItem(
            title = label,
            detail = "未安装",
            state = AutoBookCheckState.UNKNOWN
        )
    val enabled = isAppNotificationEnabled(context, packageName, appInfo.uid)
    return when (enabled) {
        false -> AutoBookCheckItem(
            title = label,
            detail = "未开启",
            state = AutoBookCheckState.FAIL
        )
        true -> AutoBookCheckItem(
            title = label,
            detail = "已开启",
            state = AutoBookCheckState.PASS
        )
        null -> AutoBookCheckItem(
            title = label,
            detail = "待确认",
            state = AutoBookCheckState.UNKNOWN
        )
    }
}

internal fun checkAndOpenNotificationListenerPermission(context: Context): ListenerPermissionActionResult {
    val isEnabled = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    if (isEnabled) {
        forceRebindNotificationListener(context)
        return ListenerPermissionActionResult.ALREADY_ENABLED
    }
    context.startActivity(
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
    return ListenerPermissionActionResult.OPENED_SETTINGS
}

internal fun checkAndOpenPaymentNotificationSettings(
    context: Context,
    packageName: String
): PaymentNotifyActionResult {
    val appInfo = runCatching {
        context.packageManager.getApplicationInfo(packageName, 0)
    }.getOrNull() ?: return PaymentNotifyActionResult.APP_NOT_INSTALLED

    val enabled = isAppNotificationEnabled(context, packageName, appInfo.uid)
    if (enabled == true) return PaymentNotifyActionResult.ALREADY_ENABLED

    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        putExtra("app_package", packageName)
        putExtra("app_uid", appInfo.uid)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
    return PaymentNotifyActionResult.OPENED_SETTINGS
}

private fun isAppNotificationEnabled(context: Context, packageName: String, uid: Int): Boolean? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return null
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return null
    val opPostNotification = "android:post_notification"
    return runCatching {
        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(opPostNotification, uid, packageName)
        mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_DEFAULT
    }.getOrNull()
}

internal fun checkAndOpenAccessibilityPermission(context: Context): AccessibilityPermissionActionResult {
    if (isAccessibilityServiceEnabled(context)) return AccessibilityPermissionActionResult.ALREADY_ENABLED
    context.startActivity(
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
    return AccessibilityPermissionActionResult.OPENED_SETTINGS
}

private fun forceRebindNotificationListener(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
    runCatching {
        val component = ComponentName(context, PaymentNotificationListener::class.java)
        android.service.notification.NotificationListenerService.requestRebind(component)
    }
}

internal fun openAutoStartSettings(context: Context): Boolean {
    val intents = listOf(
        Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.appmanager.ApplicationsDetailsActivity"
            )
            putExtra("package_name", context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
    intents.forEach { intent ->
        val opened = runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
        if (opened) return true
    }
    return runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        true
    }.getOrDefault(false)
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean? {
    val powerManager = context.getSystemService(PowerManager::class.java) ?: return null
    return runCatching { powerManager.isIgnoringBatteryOptimizations(context.packageName) }.getOrNull()
}

private fun isXiaomiFamilyDevice(): Boolean {
    val brand = Build.BRAND.orEmpty().lowercase()
    val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
    return brand.contains("xiaomi") ||
        brand.contains("redmi") ||
        brand.contains("poco") ||
        manufacturer.contains("xiaomi")
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, PaymentAccessibilityService::class.java).flattenToString()
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
    while (splitter.hasNext()) {
        if (splitter.next().equals(expected, ignoreCase = true)) return true
    }
    return false
}

private fun toChineseReason(raw: String): String {
    val reason = raw.uppercase()
    return when {
        reason.contains("SAME_SOURCE_DUPLICATE_BY_TXN_REF") -> "同源重复（同交易号）"
        reason.contains("DUPLICATE_OR_CONFLICT") -> "重复或数据库冲突"
        reason.contains("CROSS_SOURCE_LINKED") -> "跨渠道关联（已合并）"
        reason.contains("INSERTED") -> "已入库"
        reason.contains("PARSE") -> "通知解析失败"
        reason.contains("LISTENER") -> "监听服务异常"
        else -> raw.take(36)
    }
}

private fun mapPaymentPackageLabel(pkg: String?): String {
    return when (pkg) {
        "com.eg.android.AlipayGphone" -> "支付宝"
        "com.tencent.mm" -> "微信"
        "com.taobao.taobao" -> "淘宝"
        "com.jingdong.app.mall" -> "京东"
        "com.xunmeng.pinduoduo" -> "拼多多"
        "com.sankuai.meituan" -> "美团"
        "com.unionpay" -> "云闪付"
        "cmb.pb", "com.chinamworld.main", "com.icbc" -> "银行卡"
        null, "" -> "未知来源"
        else -> pkg
    }
}
