package com.example.coin_nest.autobook

import com.example.coin_nest.data.model.TransactionType
import kotlin.math.abs

private val amountRegex = Regex(
    "([+\\-−]?\\s*(?:[¥￥]|RMB|CNY)?\\s*\\d{1,7}(?:[\\.,]\\d{1,2})?)",
    RegexOption.IGNORE_CASE
)
private val currencyAnchoredAmountRegex = Regex(
    "(?:[¥￥]|RMB|CNY)\\s*([+\\-−]?\\s*\\d{1,7}(?:[\\.,]\\d{1,2})?)",
    RegexOption.IGNORE_CASE
)

private val supportedPackages = mapOf(
    "com.eg.android.AlipayGphone" to "ALIPAY",
    "com.tencent.mm" to "WECHAT",
    "com.taobao.taobao" to "TAOBAO",
    "com.jingdong.app.mall" to "JD",
    "com.xunmeng.pinduoduo" to "PDD",
    "com.sankuai.meituan" to "MEITUAN",
    "com.unionpay" to "UNIONPAY",
    "cmb.pb" to "BANK_CARD",
    "com.chinamworld.main" to "BANK_CARD",
    "com.icbc" to "BANK_CARD"
)

private val transactionKeywords = listOf(
    "支付", "付款", "扣款", "消费", "到账", "收款", "退款", "转账", "还款", "账单", "交易"
)
private val amountContextKeywords = listOf(
    "金额", "应付", "实付", "支付", "付款", "扣款", "消费", "到账", "收款", "退款", "转账", "元"
)

private val expenseKeywords = listOf(
    "支付成功", "向商家付款", "消费", "支出", "扣款", "实付", "还款", "自动扣款", "月结"
)

private val incomeKeywords = listOf(
    "收款", "收入", "到账", "退款", "入账", "返现", "退回"
)

private val transferOutKeywords = listOf(
    "转账给", "转出", "转账支出", "转账付款"
)

private val transferInKeywords = listOf(
    "转入", "转账收入", "转账到账", "收款到账"
)

private val noiseKeywords = listOf(
    "验证码", "口令", "待支付", "广告", "活动", "优惠券", "账单助手", "积分",
    "条新消息", "群聊", "内部群", "拍了拍", "@你", "语音通话", "视频通话"
)

data class ParsedPayment(
    val amountCents: Long,
    val type: TransactionType,
    val source: String,
    val note: String,
    val fingerprint: String,
    val occurredAtEpochMs: Long,
    val parentCategory: String,
    val childCategory: String
)

object PaymentNotificationParser {
    fun parse(packageName: String?, title: String?, text: String?, postTime: Long): ParsedPayment? {
        val merged = listOfNotNull(title, text)
            .joinToString(" ")
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        if (merged.isBlank()) return null
        if (noiseKeywords.any { merged.contains(it, ignoreCase = true) }) return null
        if (!looksLikeTransaction(packageName, merged)) return null

        val amountMatch = extractAmount(merged) ?: return null
        val cents = (amountMatch.amount * 100).toLong()
        if (cents <= 0 || cents > 20_000_000) return null

        val type = inferType(merged, amountMatch.hasMinusSign) ?: return null
        val source = inferSource(packageName, merged)
        val (parentCategory, childCategory) = suggestCategory(merged, type)
        val note = merged.take(140)
        val fingerprint = buildFingerprint(
            source = source,
            type = type,
            cents = cents,
            normalizedNote = note,
            occurredAtEpochMs = postTime
        )

        return ParsedPayment(
            amountCents = cents,
            type = type,
            source = source,
            note = note,
            fingerprint = fingerprint,
            occurredAtEpochMs = postTime,
            parentCategory = parentCategory,
            childCategory = childCategory
        )
    }
}

private data class ParsedAmount(val amount: Double, val hasMinusSign: Boolean)

private fun looksLikeTransaction(packageName: String?, merged: String): Boolean {
    val hasKeyword = transactionKeywords.any { merged.contains(it, ignoreCase = true) }
    val hasCurrency = currencyAnchoredAmountRegex.containsMatchIn(merged)
    val hasAmountWithContext = extractAmount(merged) != null
    if (packageName != null && supportedPackages.containsKey(packageName)) {
        return hasKeyword || hasCurrency || hasAmountWithContext
    }
    return hasKeyword || hasCurrency
}

private fun inferSource(packageName: String?, merged: String): String {
    supportedPackages[packageName].let { if (it != null) return it }
    return when {
        merged.contains("微信", ignoreCase = true) || merged.contains("wechat", ignoreCase = true) -> "WECHAT"
        merged.contains("支付宝", ignoreCase = true) || merged.contains("alipay", ignoreCase = true) -> "ALIPAY"
        merged.contains("淘宝", ignoreCase = true) -> "TAOBAO"
        merged.contains("美团", ignoreCase = true) -> "MEITUAN"
        merged.contains("京东", ignoreCase = true) -> "JD"
        merged.contains("拼多多", ignoreCase = true) -> "PDD"
        merged.contains("信用卡", ignoreCase = true) -> "CREDIT_CARD"
        merged.contains("银行卡", ignoreCase = true) -> "BANK_CARD"
        else -> "AUTO_NOTIFY"
    }
}

private fun inferType(merged: String, hasMinusSign: Boolean): TransactionType? {
    if (transferInKeywords.any { merged.contains(it, ignoreCase = true) }) return TransactionType.INCOME
    if (transferOutKeywords.any { merged.contains(it, ignoreCase = true) }) return TransactionType.EXPENSE
    if (incomeKeywords.any { merged.contains(it, ignoreCase = true) }) return TransactionType.INCOME
    if (expenseKeywords.any { merged.contains(it, ignoreCase = true) }) return TransactionType.EXPENSE
    return when {
        hasMinusSign && hasMoneyHint(merged) -> TransactionType.EXPENSE
        merged.contains("+") -> TransactionType.INCOME
        else -> null
    }
}

private fun suggestCategory(merged: String, type: TransactionType): Pair<String, String> {
    if (type == TransactionType.INCOME) {
        return when {
            merged.contains("退款", ignoreCase = true) -> "收入" to "退款"
            merged.contains("转账", ignoreCase = true) -> "收入" to "转账"
            else -> "收入" to "其他"
        }
    }

    if (merged.contains("信用卡", ignoreCase = true) && merged.contains("还款", ignoreCase = true)) {
        return "理财" to "信用卡还款"
    }
    if (merged.contains("美团", ignoreCase = true) || merged.contains("外卖", ignoreCase = true)) {
        return "生活" to "餐饮"
    }
    if (
        merged.contains("淘宝", ignoreCase = true) ||
        merged.contains("京东", ignoreCase = true) ||
        merged.contains("拼多多", ignoreCase = true)
    ) {
        return "购物" to "日常购物"
    }
    if (merged.contains("转账", ignoreCase = true)) {
        return "待分类" to "转账转出"
    }
    return "待分类" to "自动识别"
}

private fun extractAmount(merged: String): ParsedAmount? {
    currencyAnchoredAmountRegex.find(merged)?.groupValues?.getOrNull(1)?.let { raw ->
        val normalized = raw
            .replace("−", "-")
            .replace(",", ".")
            .replace(" ", "")
        val amount = normalized.toDoubleOrNull()
        if (amount != null) {
            return ParsedAmount(amount = abs(amount), hasMinusSign = normalized.startsWith("-"))
        }
    }

    val match = amountRegex.findAll(merged)
        .map { it.value.trim() }
        .firstOrNull { value ->
            value.any { it.isDigit() } &&
                !value.contains("尾号") &&
                !value.contains("****") &&
                isAmountWithContext(merged, value)
        } ?: return null

    val normalized = match
        .replace("¥", "")
        .replace("￥", "")
        .replace("RMB", "", ignoreCase = true)
        .replace("CNY", "", ignoreCase = true)
        .replace("−", "-")
        .replace(",", ".")
        .replace(" ", "")
    val amount = normalized.toDoubleOrNull() ?: return null
    return ParsedAmount(amount = abs(amount), hasMinusSign = normalized.startsWith("-"))
}

private fun isAmountWithContext(merged: String, rawValue: String): Boolean {
    val idx = merged.indexOf(rawValue)
    if (idx < 0) return false
    val start = (idx - 10).coerceAtLeast(0)
    val end = (idx + rawValue.length + 10).coerceAtMost(merged.length)
    val context = merged.substring(start, end)
    if (currencyAnchoredAmountRegex.containsMatchIn(context)) return true
    return amountContextKeywords.any { context.contains(it, ignoreCase = true) }
}

private fun hasMoneyHint(merged: String): Boolean {
    return merged.contains("¥") ||
        merged.contains("￥") ||
        merged.contains("RMB", ignoreCase = true) ||
        merged.contains("CNY", ignoreCase = true) ||
        merged.contains("金额")
}

private fun buildFingerprint(
    source: String,
    type: TransactionType,
    cents: Long,
    normalizedNote: String,
    occurredAtEpochMs: Long
): String {
    // Use a coarser bucket + stable text normalization to avoid duplicate inserts
    // from repeated or updated notifications for the same payment.
    val threeMinuteBucket = occurredAtEpochMs / (3 * 60_000)
    val normalizedKey = normalizedNote
        .replace(amountRegex, " ")
        .replace(Regex("[0-9¥￥+\\-−:：]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(24)
        .ifBlank { "unknown" }
    return "${source}_${type.name}_${cents}_${normalizedKey.hashCode()}_$threeMinuteBucket"
}
