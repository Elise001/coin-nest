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
    // 防抖窗口：屏蔽同一页面短时间重复 Accessibility 回调
    private val rawSnapshotWindowMs = 2_500L
    // 页面级去重窗口：防止同一笔成功页在短时间内连续入库
    private val logicalWindowMs = 5 * 60_000L
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
            val eventClass = event.className?.toString().orEmpty()
            AutoBookTelemetry.track(
                applicationContext,
                event = "accessibility_detected",
                packageName = pkg,
                reason = "$eventClass | raw=${merged.take(760)}"
            )
            scope.launch {
                runCatching {
                    AutoBookProcessor.process(
                        context = applicationContext,
                        repository = ServiceLocator.repository(),
                        event = AutoBookRawEvent(
                            packageName = pkg,
                            title = eventClass,
                            text = merged,
                            occurredAtEpochMs = System.currentTimeMillis(),
                            channel = AutoBookChannel.ACCESS
                        ),
                        shouldDropBeforeParse = {
                            if (isDuplicateRawSnapshot(pkg, eventClass, merged)) {
                                "RAW_DUPLICATE_WINDOW"
                            } else {
                                null
                            }
                        },
                        shouldDropBeforeInsert = { parsed ->
                            if (isDuplicateLogicalPayment(parsed, merged)) {
                                "ACCESS_DUPLICATE_WINDOW"
                            } else {
                                null
                            }
                        }
                    )
                }.onSuccess { result ->
                    if (result is AutoBookProcessResult.Inserted &&
                        result.insertResult.insertedId != null &&
                        result.insertResult.shouldNotify
                    ) {
                        val parsed = result.payment
                        PaymentActionNotifier.notifyPendingPayment(
                            context = applicationContext,
                            txId = result.insertResult.insertedId,
                            amountCents = parsed.amountCents,
                            type = parsed.type.name,
                            source = parsed.source,
                            note = parsed.note
                        )
                        showAutoDetectedToast()
                        debugPopup("无障碍记账成功")
                    }
                }.onFailure {
                    AutoBookTelemetry.track(
                        applicationContext,
                        event = "accessibility_error",
                        packageName = pkg,
                        reason = it.message ?: it.javaClass.simpleName
                    )
                    Log.e("AutoBookDebug", "accessibility processing failed", it)
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

    private fun buildMergedContent(event: AccessibilityEvent, root: AccessibilityNodeInfo?): String {
        val chunks = mutableListOf<String>()
        event.text.forEach { cs ->
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
        val stableRef = parsed.transactionRef
            ?: parsed.fingerprint
            ?: buildStablePageSignature(merged)
        val key = "${parsed.source}|${parsed.type}|${parsed.amountCents}|$stableRef"
        pruneRecentMap(recentLogicalKeyMs, now, logicalWindowMs)
        val last = recentLogicalKeyMs[key]
        if (last != null && now - last <= logicalWindowMs) return true
        recentLogicalKeyMs[key] = now
        return false
    }

    private fun buildStablePageSignature(text: String): String {
        return text
            .replace(Regex("\\d{4}[-/.年]\\d{1,2}[-/.月]\\d{1,2}日?"), "")
            .replace(Regex("\\d{1,2}:\\d{2}(:\\d{2})?"), "")
            .replace(Regex("\\s+"), "")
            .take(120)
    }

    private fun pruneRecentMap(target: LinkedHashMap<String, Long>, now: Long, windowMs: Long) {
        val it = target.entries.iterator()
        while (it.hasNext()) {
            val (_, ts) = it.next()
            if (now - ts > windowMs) it.remove()
        }
    }

    private fun debugPopup(message: String) {
        Log.d("AutoBookDebug", message)
    }

    private fun showAutoDetectedToast() {
        val now = System.currentTimeMillis()
        if (now - lastToastMs < 1500L) return
        lastToastMs = now
        mainHandler.post {
            runCatching {
                Toast.makeText(applicationContext, "自动记账已识别，已放入待确认", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
