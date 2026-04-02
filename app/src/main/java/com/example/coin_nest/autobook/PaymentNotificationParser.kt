package com.example.coin_nest.autobook

private val currencyAmountRegex = Regex(
    "(?:[\\u00A5\\uFFE5]|RMB|CNY)\\s*([1-9]\\d{0,5}(?:\\.\\d{1,2})?|0(?:\\.\\d{1,2})?)",
    RegexOption.IGNORE_CASE
)
private val yuanAmountRegex = Regex("([1-9]\\d{0,5}(?:\\.\\d{1,2})?|0(?:\\.\\d{1,2})?)\\s*元")
private val expenseKeywords = listOf(
    "\u652f\u4ed8\u6210\u529f",
    "\u5411\u5546\u5bb6\u4ed8\u6b3e",
    "\u6d88\u8d39",
    "\u652f\u51fa",
    "\u4ed8\u6b3e",
    "\u652f\u4ed8"
)
private val nonExpenseKeywords = listOf(
    "\u6536\u6b3e",
    "\u6536\u5165",
    "\u5230\u8d26",
    "\u8f6c\u5165",
    "\u7ea2\u5305",
    "\u9000\u6b3e"
)
private val expenseContextKeywords = listOf(
    "\u652f\u4ed8",
    "\u4ed8\u6b3e",
    "\u6263\u6b3e",
    "\u652f\u51fa",
    "\u6d88\u8d39",
    "\u5b9e\u4ed8",
    "\u4ea4\u6613\u91d1\u989d",
    "\u82b1\u4e86"
)

data class ParsedPayment(
    val amountCents: Long,
    val source: String,
    val note: String,
    val fingerprint: String
)

object PaymentNotificationParser {
    fun parse(packageName: String?, title: String?, text: String?, postTime: Long): ParsedPayment? {
        val source = when (packageName) {
            "com.eg.android.AlipayGphone" -> "ALIPAY"
            "com.tencent.mm" -> "WECHAT"
            else -> return null
        }
        val merged = listOfNotNull(title, text).joinToString(" ").replace('\n', ' ').trim()
        if (merged.isBlank()) return null
        if (nonExpenseKeywords.any { merged.contains(it, ignoreCase = true) }) return null
        if (expenseKeywords.none { merged.contains(it, ignoreCase = true) } && !merged.contains("-")) return null

        val amountText = extractExpenseAmountText(merged) ?: return null
        val cents = (amountText.toDoubleOrNull()?.times(100))?.toLong() ?: return null
        if (cents <= 0 || cents > 20_000_000) return null
        val note = merged.take(140)
        val fingerprint = "${source}_${cents}_${note.hashCode()}_${postTime / 10_000}"
        return ParsedPayment(amountCents = cents, source = source, note = note, fingerprint = fingerprint)
    }
}

private fun extractExpenseAmountText(merged: String): String? {
    currencyAmountRegex.find(merged)?.groupValues?.getOrNull(1)?.let { return it }

    val candidates = yuanAmountRegex.findAll(merged).mapNotNull { match ->
        val amount = match.groupValues.getOrNull(1).orEmpty()
        if (amount.isBlank()) return@mapNotNull null
        val start = (match.range.first - 12).coerceAtLeast(0)
        val end = (match.range.last + 12).coerceAtMost(merged.lastIndex)
        val context = merged.substring(start, end + 1)
        if (expenseContextKeywords.any { context.contains(it, ignoreCase = true) }) amount else null
    }.toList()
    if (candidates.isNotEmpty()) return candidates.first()

    return null
}
