package com.example.coin_nest.ui

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
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
    return Instant.ofEpochMilli(epochMs).atZone(zone).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
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

internal fun checkAndOpenNotificationListenerPermission(context: Context): ListenerPermissionActionResult {
    val isEnabled = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    if (isEnabled) return ListenerPermissionActionResult.ALREADY_ENABLED
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
