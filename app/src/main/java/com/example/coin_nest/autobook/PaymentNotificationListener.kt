package com.example.coin_nest.autobook

import android.content.ComponentName
import android.app.Notification
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast
import com.example.coin_nest.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PaymentNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastPopupMs: Long = 0L

    // Layer 1: whitelist + ignore self app notifications.
    private val allowedPackages = setOf(
        "com.eg.android.AlipayGphone",
        "com.tencent.mm",
        "com.taobao.taobao",
        "com.jingdong.app.mall",
        "com.xunmeng.pinduoduo",
        "com.sankuai.meituan",
        "com.unionpay",
        "cmb.pb",
        "com.chinamworld.main",
        "com.icbc"
    )

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(applicationContext)
        PaymentActionNotifier.ensureChannel(applicationContext)
        AutoBookTelemetry.track(applicationContext, event = "listener_create")
        debugPopup("AUTOBK service started")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        runCatching {
            val n = sbn?.notification ?: return
            val packageName = sbn.packageName.orEmpty()
            if (!isWhitelistedPaymentNotification(packageName)) return

            val extras = n.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = buildMergedNotificationText(extras)
            val preview = listOfNotNull(title, text)
                .joinToString(" ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(48)
            AutoBookTelemetry.track(
                applicationContext,
                event = "notify_received",
                packageName = packageName,
                reason = preview
            )
            val summaryTag = if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) "[SUMMARY]" else ""
            debugPopup("PAYMENT_NOTIFY$summaryTag: $packageName ${title.orEmpty().take(12)}")

            val parsedResult = PaymentNotificationParser.parseWithDebug(packageName, title, text, sbn.postTime)
            val parsed = parsedResult.payment
            if (parsed == null) {
                AutoBookTelemetry.track(
                    applicationContext,
                    event = "parse_failed",
                    packageName = packageName,
                    reason = parsedResult.reason
                )
                debugPopup("IGNORE_NOTIFY($packageName): ${parsedResult.reason}")
                return
            }

            scope.launch {
                val insertResult = ServiceLocator.repository().addAutoTransaction(
                    amountCents = parsed.amountCents,
                    type = parsed.type,
                    source = parsed.source,
                    note = parsed.note,
                    fingerprint = parsed.fingerprint,
                    occurredAtEpochMs = parsed.occurredAtEpochMs,
                    channel = "NOTIFY",
                    parent = parsed.parentCategory,
                    child = parsed.childCategory
                )
                when {
                    insertResult.insertedId != null && insertResult.shouldNotify -> {
                        AutoBookTelemetry.track(
                            applicationContext,
                            event = "insert_success",
                            packageName = packageName,
                            reason = insertResult.reason
                        )
                        debugPopup("AUTOBOOK_OK: ${parsed.source} ${parsed.amountCents / 100.0}")
                        PaymentActionNotifier.notifyPendingPayment(
                            context = applicationContext,
                            txId = insertResult.insertedId,
                            amountCents = parsed.amountCents,
                            type = parsed.type.name,
                            source = parsed.source,
                            note = parsed.note
                        )
                    }
                    insertResult.insertedId != null -> {
                        AutoBookTelemetry.track(
                            applicationContext,
                            event = "insert_linked",
                            packageName = packageName,
                            reason = insertResult.reason
                        )
                        debugPopup("AUTOBOOK_LINKED: reason=${insertResult.reason}")
                    }
                    else -> {
                        AutoBookTelemetry.track(
                            applicationContext,
                            event = "insert_drop",
                            packageName = packageName,
                            reason = insertResult.reason
                        )
                        debugPopup("AUTOBOOK_DROP: reason=${insertResult.reason}")
                    }
                }
            }
        }.onFailure { error ->
            AutoBookTelemetry.track(
                applicationContext,
                event = "listener_error",
                packageName = sbn?.packageName,
                reason = error.message ?: error.javaClass.simpleName
            )
            Log.e("AutoBookDebug", "onNotificationPosted failed", error)
        }
    }

    override fun onDestroy() {
        AutoBookTelemetry.track(applicationContext, event = "listener_destroy")
        debugPopup("AUTOBK service destroyed")
        scope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        AutoBookTelemetry.track(applicationContext, event = "listener_connected")
        debugPopup("Listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        AutoBookTelemetry.track(applicationContext, event = "listener_disconnected")
        debugPopup("Listener disconnected, rebind")
        runCatching {
            requestRebind(ComponentName(applicationContext, PaymentNotificationListener::class.java))
        }
    }

    private fun isWhitelistedPaymentNotification(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (packageName == applicationContext.packageName) return false
        if (allowedPackages.contains(packageName)) return true
        return PaymentNotificationParser.isSupportedPackage(packageName)
    }

    private fun debugPopup(msg: String) {
        Log.d("AutoBookDebug", msg)
        val now = System.currentTimeMillis()
        if (now - lastPopupMs < 500) return
        lastPopupMs = now
        val display = mapDebugMessageToChinese(msg)
        mainHandler.post {
            runCatching {
                Toast.makeText(applicationContext, display.take(80), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun mapDebugMessageToChinese(raw: String): String {
        if (raw.startsWith("AUTOBK service started")) return "自动记账监听服务已启动"
        if (raw.startsWith("AUTOBK service destroyed")) return "自动记账监听服务已停止"
        if (raw.startsWith("Listener connected")) return "通知监听已连接"
        if (raw.startsWith("Listener disconnected")) return "通知监听已断开，正在重连"

        if (raw.startsWith("PAYMENT_NOTIFY")) {
            val pkg = raw.substringAfter(": ", "").substringBefore(" ").trim()
            return "收到支付通知：${mapPackageLabel(pkg)}"
        }
        if (raw.startsWith("IGNORE_NOTIFY")) {
            val reason = raw.substringAfter(": ", "")
            return "忽略通知：${mapReasonToChinese(reason)}"
        }
        if (raw.startsWith("AUTOBOOK_OK")) {
            val source = raw.substringAfter(": ", "").substringBefore(" ").trim()
            return "自动记账成功：${mapSourceLabel(source)}"
        }
        if (raw.startsWith("AUTOBOOK_LINKED")) {
            val reason = raw.substringAfter("reason=", "")
            return "自动记账已关联：${mapReasonToChinese(reason)}"
        }
        if (raw.startsWith("AUTOBOOK_DROP")) {
            val reason = raw.substringAfter("reason=", "")
            return "自动记账未入库：${mapReasonToChinese(reason)}"
        }
        return raw
    }

    private fun mapSourceLabel(source: String): String {
        return when (source.uppercase()) {
            "ALIPAY" -> "支付宝"
            "WECHAT" -> "微信"
            "TAOBAO" -> "淘宝"
            "MEITUAN" -> "美团"
            "JD" -> "京东"
            "PDD" -> "拼多多"
            "UNIONPAY" -> "云闪付"
            "BANK_CARD" -> "银行卡"
            "CREDIT_CARD" -> "信用卡"
            else -> source
        }
    }

    private fun mapPackageLabel(pkg: String): String {
        return when (pkg) {
            "com.eg.android.AlipayGphone" -> "支付宝"
            "com.tencent.mm" -> "微信"
            "com.taobao.taobao" -> "淘宝"
            "com.jingdong.app.mall" -> "京东"
            "com.xunmeng.pinduoduo" -> "拼多多"
            "com.sankuai.meituan" -> "美团"
            "com.unionpay" -> "云闪付"
            "cmb.pb", "com.chinamworld.main", "com.icbc" -> "银行卡"
            else -> pkg
        }
    }

    private fun mapReasonToChinese(reason: String): String {
        val text = reason.trim()
        val upper = text.uppercase()
        return when {
            upper.contains("SAME_SOURCE_DUPLICATE_BY_TXN_REF") -> "同源重复（同交易号）"
            upper.contains("DUPLICATE_OR_CONFLICT") -> "重复或数据库冲突"
            upper.contains("CROSS_SOURCE_LINKED") -> "跨渠道关联（已合并）"
            upper.contains("INSERTED") -> "已入库"
            upper.contains("命中噪声关键词") -> "命中噪声关键词"
            upper.contains("未提取到有效金额") -> "未提取到有效金额"
            upper.contains("无法判断收支类型") -> "无法判断收支类型"
            upper.contains("空通知内容") -> "通知内容为空"
            upper.contains("非支付渠道包名") -> "非支付渠道通知"
            else -> text.take(24)
        }
    }

    private fun buildMergedNotificationText(extras: android.os.Bundle?): String {
        if (extras == null) return ""
        val pieces = buildList<String> {
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
            extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
            extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { line ->
                line?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }
        return pieces.joinToString(" ")
    }
}
