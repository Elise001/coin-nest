package com.example.coin_nest.data

internal const val AUTO_CHANNEL_WINDOW_DUPLICATE_MS = 60_000L
internal const val AUTO_CROSS_SOURCE_WINDOW_DUPLICATE_MS = 180_000L
internal const val AUTO_SAME_SOURCE_WINDOW_DUPLICATE_MS = AUTO_CHANNEL_WINDOW_DUPLICATE_MS

internal fun isWithinAutoSameSourceWindow(existingOccurredAt: Long, incomingOccurredAt: Long): Boolean {
    return kotlin.math.abs(existingOccurredAt - incomingOccurredAt) <= AUTO_SAME_SOURCE_WINDOW_DUPLICATE_MS
}

internal enum class AutoBookMergeAction {
    NONE,
    DROP_DUPLICATE,
    LINK_RELATED
}

internal data class AutoBookMergeDecision(
    val action: AutoBookMergeAction,
    val confidence: Int,
    val reason: String
)

internal object AutoBookMergeScorer {
    private val paymentSources = setOf("ALIPAY", "WECHAT")
    private val bankSources = setOf("BANK_CARD", "CREDIT_CARD", "UNIONPAY")
    private val autoChannels = setOf("NOTIFY", "ACCESS")

    fun decide(
        existingSource: String,
        incomingSource: String,
        existingChannel: String,
        incomingChannel: String,
        timeDiffMs: Long
    ): AutoBookMergeDecision {
        val oldSource = existingSource.uppercase()
        val newSource = incomingSource.uppercase()
        val oldChannel = existingChannel.uppercase()
        val newChannel = incomingChannel.uppercase()
        val absTimeDiff = kotlin.math.abs(timeDiffMs)

        if (oldSource == newSource && oldChannel in autoChannels && newChannel in autoChannels) {
            if (oldChannel != newChannel && absTimeDiff <= AUTO_CHANNEL_WINDOW_DUPLICATE_MS) {
                return AutoBookMergeDecision(
                    action = AutoBookMergeAction.DROP_DUPLICATE,
                    confidence = 96,
                    reason = "同来源通知与屏幕识别重复"
                )
            }
            if (oldChannel == newChannel && absTimeDiff <= 15_000L) {
                return AutoBookMergeDecision(
                    action = AutoBookMergeAction.DROP_DUPLICATE,
                    confidence = 88,
                    reason = "同来源短时间重复通知"
                )
            }
        }

        if (isPaymentBankPair(oldSource, newSource) && absTimeDiff <= AUTO_CROSS_SOURCE_WINDOW_DUPLICATE_MS) {
            val confidence = when {
                absTimeDiff <= 90_000L -> 92
                absTimeDiff <= 120_000L -> 86
                else -> 78
            }
            return AutoBookMergeDecision(
                action = AutoBookMergeAction.LINK_RELATED,
                confidence = confidence,
                reason = "支付平台与银行卡关联交易"
            )
        }

        return AutoBookMergeDecision(
            action = AutoBookMergeAction.NONE,
            confidence = 0,
            reason = "无合并关系"
        )
    }

    private fun isPaymentBankPair(first: String, second: String): Boolean {
        return (first in paymentSources && second in bankSources) ||
            (first in bankSources && second in paymentSources)
    }
}
