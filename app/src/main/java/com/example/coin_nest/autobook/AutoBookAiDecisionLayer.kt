package com.example.coin_nest.autobook

import java.util.LinkedHashMap

data class AutoBookAiDecision(
    val accepted: Boolean,
    val kind: AutoBookAiTextKind,
    val confidence: Int,
    val reason: String
)

enum class AutoBookAiTextKind {
    TRANSACTION,
    MARKETING,
    WEALTH,
    NOISE,
    NO_AMOUNT,
    LOW_CONFIDENCE
}

object AutoBookAiDecisionLayer {
    private const val REJECT_CACHE_WINDOW_MS = 2 * 60_000L
    private const val MAX_REJECT_CACHE_SIZE = 128
    private val recentRejects = LinkedHashMap<String, Long>()
    private val currencyLikeMoneyRegex = Regex("[￥¥]\\s*[+\\-]?\\d|\\d+(?:[\\.,]\\d{1,2})?\\s*(?:元|人民币)")

    private val realPaymentSignals = listOf(
        "支付成功", "成功付款", "付款成功", "已支付", "实付", "扣款", "消费", "收款",
        "收入", "到账", "退款", "转账", "还款", "交易成功", "支出"
    )
    private val amountSignals = listOf("金额", "实付", "应付", "付款", "支付", "扣款", "消费", "人民币", "元", "￥", "¥")
    private val marketingSignals = listOf(
        "优惠券", "消费券", "券包", "卡券", "补贴", "京豆", "积分", "专属优惠", "特惠",
        "可抵扣", "领取", "未领取", "还未领取", "快来领取", "领取即将截止", "面额",
        "满减", "活动", "广告", "推广", "88VIP", "VIP消费券", "芝麻分", "周报", "进度"
    )
    private val wealthSignals = listOf(
        "余额宝", "基金", "理财", "申购", "赎回", "确认金额", "确认份额", "收益",
        "分红", "净值", "持仓", "体验金", "买入成功", "卖出成功"
    )
    private val noiseSignals = listOf(
        "验证码", "口令", "待支付", "群聊", "语音通话", "视频通话", "拍了拍", "android.widget", "头像"
    )
    private val trustedPaymentPackages = setOf(
        "com.eg.android.AlipayGphone",
        "com.tencent.mm",
        "com.unionpay",
        "cmb.pb",
        "com.chinamworld.main",
        "com.icbc"
    )
    private val moneyRegex = Regex(
        "(?:￥|¥|RMB|CNY)?\\s*[+\\-]?\\d{1,7}(?:[\\.,]\\d{1,2})?\\s*(?:元|块|人民币)?",
        RegexOption.IGNORE_CASE
    )

    fun assess(packageName: String?, mergedText: String): AutoBookAiDecision {
        val text = mergedText.trim()
        if (text.isBlank()) return reject(AutoBookAiTextKind.NOISE, 0, "空文本")
        cachedReject(packageName, text)?.let { return it }

        val hasMoney = moneyRegex.containsMatchIn(text)
        val hasCurrencyLikeMoney = currencyLikeMoneyRegex.containsMatchIn(text)
        val paymentHits = realPaymentSignals.count { text.contains(it, ignoreCase = true) }
        val amountContextHits = amountSignals.count { text.contains(it, ignoreCase = true) }
        val marketingHits = marketingSignals.count { text.contains(it, ignoreCase = true) }
        val wealthHits = wealthSignals.count { text.contains(it, ignoreCase = true) }
        val noiseHits = noiseSignals.count { text.contains(it, ignoreCase = true) }
        val isTrustedPaymentPackage = packageName in trustedPaymentPackages

        if (text.length > 380 && paymentHits <= 1) {
            return cachedReject(packageName, text, AutoBookAiTextKind.LOW_CONFIDENCE, 10, "文本过长，不像付款结果页")
        }
        if (noiseHits > 0 && paymentHits == 0) {
            return cachedReject(packageName, text, AutoBookAiTextKind.NOISE, 8, "噪声文本")
        }
        if (wealthHits > 0) {
            return cachedReject(packageName, text, AutoBookAiTextKind.WEALTH, 10, "理财或资产变动")
        }
        if (marketingHits > 0 && !hasStrongSettlement(text)) {
            return cachedReject(packageName, text, AutoBookAiTextKind.MARKETING, 12, "营销权益文本")
        }
        if (!hasMoney) {
            return cachedReject(packageName, text, AutoBookAiTextKind.NO_AMOUNT, 18, "缺少有效金额")
        }
        if (!hasCurrencyLikeMoney && !hasStrongSettlement(text) && paymentHits <= 1) {
            return cachedReject(packageName, text, AutoBookAiTextKind.LOW_CONFIDENCE, 18, "缺少金额上下文")
        }

        var score = 0
        if (isTrustedPaymentPackage) score += 14
        score += paymentHits * 22
        score += amountContextHits * 6
        if (hasMoney) score += 22
        if (text.contains("尾号") || text.contains("信用卡") || text.contains("银行卡")) score += 8
        score -= marketingHits * 24
        score -= noiseHits * 30

        val confidence = score.coerceIn(0, 100)
        return if (confidence >= 50) {
            AutoBookAiDecision(true, AutoBookAiTextKind.TRANSACTION, confidence, "交易可信")
        } else {
            cachedReject(packageName, text, AutoBookAiTextKind.LOW_CONFIDENCE, confidence, "交易可信度不足")
        }
    }

    private fun reject(kind: AutoBookAiTextKind, confidence: Int, reason: String): AutoBookAiDecision {
        return AutoBookAiDecision(false, kind, confidence, reason)
    }

    @Synchronized
    private fun cachedReject(packageName: String?, text: String): AutoBookAiDecision? {
        val now = System.currentTimeMillis()
        pruneRejects(now)
        val key = rejectKey(packageName, text)
        val last = recentRejects[key] ?: return null
        return if (now - last <= REJECT_CACHE_WINDOW_MS) {
            reject(AutoBookAiTextKind.NOISE, 5, "重复拒绝窗口")
        } else {
            null
        }
    }

    @Synchronized
    private fun cachedReject(
        packageName: String?,
        text: String,
        kind: AutoBookAiTextKind,
        confidence: Int,
        reason: String
    ): AutoBookAiDecision {
        recentRejects[rejectKey(packageName, text)] = System.currentTimeMillis()
        while (recentRejects.size > MAX_REJECT_CACHE_SIZE) {
            val oldestKey = recentRejects.entries.firstOrNull()?.key ?: break
            recentRejects.remove(oldestKey)
        }
        return reject(kind, confidence, reason)
    }

    private fun pruneRejects(now: Long) {
        val iterator = recentRejects.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value > REJECT_CACHE_WINDOW_MS) iterator.remove()
        }
    }

    private fun rejectKey(packageName: String?, text: String): String {
        val normalized = text
            .replace(Regex("\\d{1,2}:\\d{2}(:\\d{2})?"), "")
            .replace(Regex("\\s+"), "")
            .take(180)
        return "${packageName.orEmpty()}|$normalized"
    }

    private fun hasStrongSettlement(text: String): Boolean {
        return listOf("支付成功", "成功付款", "已支付", "实付", "扣款", "退款", "还款", "交易成功")
            .any { text.contains(it, ignoreCase = true) }
    }
}
