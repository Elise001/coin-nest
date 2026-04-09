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
    "支付成功", "向商家付款", "消费", "支出", "扣款", "实付", "还款", "自动扣款", "月结", "扣费"
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

private val transactionRefRegexes = listOf(
    Regex("(?:订单号|交易号|流水号|商户单号|交易单号|凭证号|单号)[:：\\s]*([A-Za-z0-9\\-]{6,40})"),
    Regex("(?:Order|Trade|Transaction)\\s*(?:No|ID|#)?[:：\\s]*([A-Za-z0-9\\-]{6,40})", RegexOption.IGNORE_CASE)
)

data class ParsedPayment(
    val amountCents: Long,
    val type: TransactionType,
    val source: String,
    val note: String,
    val fingerprint: String?,
    val transactionRef: String?,
    val occurredAtEpochMs: Long,
    val parentCategory: String,
    val childCategory: String
)

data class PaymentParseDebugResult(
    val payment: ParsedPayment?,
    val reason: String
)

object PaymentNotificationParser {
    fun isSupportedPackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        if (supportedPackages.containsKey(packageName)) return true
        val pkg = packageName.lowercase()
        return pkg.contains("alipay") || pkg == "com.tencent.mm"
    }

    fun parse(packageName: String?, title: String?, text: String?, postTime: Long): ParsedPayment? {
        return parseWithDebug(packageName, title, text, postTime).payment
    }

    fun parseWithDebug(packageName: String?, title: String?, text: String?, postTime: Long): PaymentParseDebugResult {
        if (!isSupportedPackage(packageName)) {
            return PaymentParseDebugResult(null, "非支付渠道包名: ${packageName ?: "null"}")
        }
        val merged = listOfNotNull(title, text)
            .joinToString(" ")
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        if (merged.isBlank()) return PaymentParseDebugResult(null, "空通知内容")
        if (noiseKeywords.any { merged.contains(it, ignoreCase = true) }) {
            return PaymentParseDebugResult(null, "命中噪声关键词")
        }
        val amountMatch = extractAmount(merged)
            ?: return PaymentParseDebugResult(null, "未提取到有效金额")
        val cents = (amountMatch.amount * 100).toLong()
        if (cents <= 0 || cents > 20_000_000) {
            return PaymentParseDebugResult(null, "金额越界: $cents")
        }

        val source = inferSource(packageName, merged)
        val type = inferType(merged, amountMatch.hasMinusSign, source)
            ?: return PaymentParseDebugResult(null, "无法判断收支类型")
        val (parentCategory, childCategory) = suggestCategory(merged, type)
        val note = merged.take(140)
        val transactionRef = extractTransactionRef(merged)
        val fingerprint = buildSameSourceFingerprint(source = source, transactionRef = transactionRef)

        return PaymentParseDebugResult(
            payment = ParsedPayment(
                amountCents = cents,
                type = type,
                source = source,
                note = note,
                fingerprint = fingerprint,
                transactionRef = transactionRef,
                occurredAtEpochMs = postTime,
                parentCategory = parentCategory,
                childCategory = childCategory
            ),
            reason = "解析成功"
        )
    }
}

private data class ParsedAmount(val amount: Double, val hasMinusSign: Boolean)

private fun looksLikeTransaction(packageName: String?, merged: String): Boolean {
    if (packageName == null || !supportedPackages.containsKey(packageName)) return false
    val hasKeyword = transactionKeywords.any { merged.contains(it, ignoreCase = true) }
    val hasCurrency = currencyAnchoredAmountRegex.containsMatchIn(merged)
    val hasAmountWithContext = extractAmount(merged) != null
    return hasKeyword || hasCurrency || hasAmountWithContext
}

private fun inferSource(packageName: String?, merged: String): String {
    supportedPackages[packageName].let { if (it != null) return it }
    if (!packageName.isNullOrBlank()) {
        val pkg = packageName.lowercase()
        if (pkg == "com.tencent.mm") return "WECHAT"
        if (pkg.contains("alipay")) return "ALIPAY"
    }
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

private fun inferType(merged: String, hasMinusSign: Boolean, source: String): TransactionType? {
    if (transferInKeywords.any { merged.contains(it, ignoreCase = true) }) return TransactionType.INCOME
    if (transferOutKeywords.any { merged.contains(it, ignoreCase = true) }) return TransactionType.EXPENSE
    if (incomeKeywords.any { merged.contains(it, ignoreCase = true) }) return TransactionType.INCOME
    if (expenseKeywords.any { merged.contains(it, ignoreCase = true) }) return TransactionType.EXPENSE
    return when {
        hasMinusSign && hasMoneyHint(merged) -> TransactionType.EXPENSE
        merged.contains("+") -> TransactionType.INCOME
        source in setOf("ALIPAY", "WECHAT", "BANK_CARD", "CREDIT_CARD", "UNIONPAY") -> TransactionType.EXPENSE
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

private fun extractTransactionRef(merged: String): String? {
    transactionRefRegexes.forEach { regex ->
        regex.find(merged)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.length >= 6 }?.let {
            return it.uppercase()
        }
    }
    return null
}

private fun buildSameSourceFingerprint(source: String, transactionRef: String?): String? {
    val ref = transactionRef?.trim()?.takeIf { it.length >= 6 } ?: return null
    return "SRC_TXN_${source}_${ref.uppercase()}"
}
