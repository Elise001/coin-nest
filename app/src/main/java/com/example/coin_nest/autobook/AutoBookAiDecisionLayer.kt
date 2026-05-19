package com.example.coin_nest.autobook

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
    private val realPaymentSignals = listOf(
        "支付成功", "成功付款", "付款成功", "已支付", "实付", "扣款", "消费", "收款",
        "收入", "到账", "退款", "转账", "还款", "交易成功", "支出"
    )
    private val amountSignals = listOf("金额", "实付", "应付", "付款", "支付", "扣款", "消费", "人民币", "元", "￥", "¥")
    private val marketingSignals = listOf(
        "优惠券", "消费券", "券包", "卡券", "补贴", "京豆", "积分", "专属优惠", "特惠",
        "可抵扣", "领取", "未领取", "还未领取", "快来领取", "领取即将截止", "面额",
        "满减", "活动", "广告", "推广", "88VIP", "VIP消费券"
    )
    private val wealthSignals = listOf(
        "余额宝", "基金", "理财", "申购", "赎回", "确认金额", "确认份额", "收益",
        "分红", "净值", "持仓", "体验金", "买入成功", "卖出成功"
    )
    private val noiseSignals = listOf(
        "验证码", "口令", "待支付", "群聊", "语音通话", "视频通话", "拍了拍", "android.widget"
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

        val hasMoney = moneyRegex.containsMatchIn(text)
        val paymentHits = realPaymentSignals.count { text.contains(it, ignoreCase = true) }
        val amountContextHits = amountSignals.count { text.contains(it, ignoreCase = true) }
        val marketingHits = marketingSignals.count { text.contains(it, ignoreCase = true) }
        val wealthHits = wealthSignals.count { text.contains(it, ignoreCase = true) }
        val noiseHits = noiseSignals.count { text.contains(it, ignoreCase = true) }
        val isTrustedPaymentPackage = packageName in trustedPaymentPackages

        if (text.length > 380 && paymentHits <= 1) {
            return reject(AutoBookAiTextKind.LOW_CONFIDENCE, 10, "文本过长，不像付款结果页")
        }
        if (noiseHits > 0 && paymentHits == 0) {
            return reject(AutoBookAiTextKind.NOISE, 8, "噪声文本")
        }
        if (wealthHits > 0) {
            return reject(AutoBookAiTextKind.WEALTH, 10, "理财或资产变动")
        }
        if (marketingHits > 0 && !hasStrongSettlement(text)) {
            return reject(AutoBookAiTextKind.MARKETING, 12, "营销权益文本")
        }
        if (!hasMoney) {
            return reject(AutoBookAiTextKind.NO_AMOUNT, 18, "缺少有效金额")
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
            reject(AutoBookAiTextKind.LOW_CONFIDENCE, confidence, "交易可信度不足")
        }
    }

    private fun reject(kind: AutoBookAiTextKind, confidence: Int, reason: String): AutoBookAiDecision {
        return AutoBookAiDecision(false, kind, confidence, reason)
    }

    private fun hasStrongSettlement(text: String): Boolean {
        return listOf("支付成功", "成功付款", "已支付", "实付", "扣款", "退款", "还款", "交易成功")
            .any { text.contains(it, ignoreCase = true) }
    }
}
