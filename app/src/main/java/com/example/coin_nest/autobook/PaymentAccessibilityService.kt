package com.example.coin_nest.autobook

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.example.coin_nest.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.LinkedHashMap

class PaymentAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastToastMs: Long = 0L

    private val targetPackages = setOf(
        "com.eg.android.AlipayGphone",
        "com.tencent.mm"
    )
    private val successKeywords = listOf(
        "支付成功", "付款成功", "交易成功", "已支付", "成功支付", "支付完成"
    )
    private val nonPaymentKeywords = listOf(
        "余额宝", "基金", "理财", "申购", "赎回", "收益", "确认金额", "确认份额", "买入成功"
    )

    // 防抖窗口：屏蔽同一页面短时间重复 Accessibility 回调
    private val rawSnapshotWindowMs = 2_500L
    // 页面级去重窗口：防止同一笔成功页在短时间内连续入库
    private val logicalWindowMs = 10_000L
    private val recentRawSnapshotMs = LinkedHashMap<String, Long>()
    private val recentLogicalKeyMs = LinkedHashMap<String, Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        ServiceLocator.init(applicationContext)
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 120
        }
        AutoBookTelemetry.track(applicationContext, event = "accessibility_connected")
        debugPopup("无障碍识别已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString().orEmpty()
        if (pkg !in targetPackages) return
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event?.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        runCatching {
            val merged = buildMergedContent(event, rootInActiveWindow)
            if (merged.isBlank()) return
            if (!looksLikePaymentSuccess(merged)) return
            if (looksLikeNonPayment(merged)) return

            val eventClass = event.className?.toString().orEmpty()
            if (isDuplicateRawSnapshot(pkg, eventClass, merged)) {
                AutoBookTelemetry.track(
                    applicationContext,
                    event = "accessibility_drop",
                    packageName = pkg,
                    reason = "RAW_DUPLICATE_WINDOW"
                )
                return
            }

            AutoBookTelemetry.track(
                applicationContext,
                event = "accessibility_detected",
                packageName = pkg,
                reason = merged.take(60)
            )

            val parsedResult = PaymentNotificationParser.parseWithDebug(
                packageName = pkg,
                title = eventClass,
                text = merged,
                postTime = System.currentTimeMillis()
            )
            val parsed = parsedResult.payment ?: run {
                AutoBookTelemetry.track(
                    applicationContext,
                    event = "accessibility_parse_failed",
                    packageName = pkg,
                    reason = parsedResult.reason
                )
                return
            }

            if (isDuplicateLogicalPayment(parsed, merged)) {
                AutoBookTelemetry.track(
                    applicationContext,
                    event = "accessibility_drop",
                    packageName = pkg,
                    reason = "ACCESS_DUPLICATE_WINDOW"
                )
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
                    channel = "ACCESS",
                    parent = parsed.parentCategory,
                    child = parsed.childCategory
                )
                when {
                    insertResult.insertedId != null && insertResult.shouldNotify -> {
                        AutoBookTelemetry.track(
                            applicationContext,
                            event = "accessibility_insert_success",
                            packageName = pkg,
                            reason = insertResult.reason
                        )
                        PaymentActionNotifier.notifyPendingPayment(
                            context = applicationContext,
                            txId = insertResult.insertedId,
                            amountCents = parsed.amountCents,
                            type = parsed.type.name,
                            source = parsed.source,
                            note = parsed.note
                        )
                        debugPopup("无障碍记账成功")
                    }
                    else -> {
                        AutoBookTelemetry.track(
                            applicationContext,
                            event = "accessibility_insert_drop",
                            packageName = pkg,
                            reason = insertResult.reason
                        )
                    }
                }
            }
        }.onFailure {
            AutoBookTelemetry.track(
                applicationContext,
                event = "accessibility_error",
                packageName = pkg,
                reason = it.message ?: it.javaClass.simpleName
            )
            Log.e("AutoBookDebug", "accessibility handling failed", it)
        }
    }

    override fun onInterrupt() {
        AutoBookTelemetry.track(applicationContext, event = "accessibility_interrupt")
    }

    override fun onDestroy() {
        AutoBookTelemetry.track(applicationContext, event = "accessibility_destroy")
        scope.cancel()
        super.onDestroy()
    }

    private fun looksLikePaymentSuccess(text: String): Boolean {
        return successKeywords.any { text.contains(it, ignoreCase = true) }
    }

    private fun looksLikeNonPayment(text: String): Boolean {
        val nonPayment = nonPaymentKeywords.any { text.contains(it, ignoreCase = true) }
        if (!nonPayment) return false
        return successKeywords.none { text.contains(it, ignoreCase = true) }
    }

    private fun buildMergedContent(event: AccessibilityEvent, root: AccessibilityNodeInfo?): String {
        val chunks = mutableListOf<String>()
        event.text?.forEach { cs ->
            cs?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { chunks += it }
        }
        root?.let { collectText(it, chunks, depth = 0) }
        return chunks.joinToString(" ")
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun collectText(node: AccessibilityNodeInfo, out: MutableList<String>, depth: Int) {
        if (depth > 6 || out.size > 120) return
        node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { out += it }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { out += it }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectText(child, out, depth + 1)
            child.recycle()
        }
    }

    private fun isDuplicateRawSnapshot(packageName: String, eventClass: String, merged: String): Boolean {
        val now = System.currentTimeMillis()
        val key = "$packageName|$eventClass|${merged.take(160)}"
        pruneRecentMap(recentRawSnapshotMs, now, rawSnapshotWindowMs)
        val last = recentRawSnapshotMs[key]
        if (last != null && now - last <= rawSnapshotWindowMs) return true
        recentRawSnapshotMs[key] = now
        return false
    }

    private fun isDuplicateLogicalPayment(parsed: ParsedPayment, merged: String): Boolean {
        val now = System.currentTimeMillis()
        val stableRef = parsed.transactionRef ?: parsed.fingerprint ?: merged.take(80)
        val key = "${parsed.source}|${parsed.type}|${parsed.amountCents}|$stableRef"
        pruneRecentMap(recentLogicalKeyMs, now, logicalWindowMs)
        val last = recentLogicalKeyMs[key]
        if (last != null && now - last <= logicalWindowMs) return true
        recentLogicalKeyMs[key] = now
        return false
    }

    private fun pruneRecentMap(target: LinkedHashMap<String, Long>, now: Long, windowMs: Long) {
        val it = target.entries.iterator()
        while (it.hasNext()) {
            val (_, ts) = it.next()
            if (now - ts > windowMs) it.remove()
        }
    }

    private fun debugPopup(message: String) {
        val now = System.currentTimeMillis()
        if (now - lastToastMs < 1500L) return
        lastToastMs = now
        mainHandler.post {
            runCatching {
                Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
