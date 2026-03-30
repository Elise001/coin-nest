package com.example.coin_nest.autobook

private val currencyAmountRegex = Regex("(?:[\\u00A5\\uFFE5]|RMB|CNY)\\s*(\\d+(?:\\.\\d{1,2})?)", RegexOption.IGNORE_CASE)
private val fallbackAmountRegex = Regex("(\\d+(?:\\.\\d{1,2})?)")
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

        val amountText = currencyAmountRegex.find(merged)?.groupValues?.getOrNull(1)
            ?: fallbackAmountRegex.findAll(merged).mapNotNull { it.groupValues.getOrNull(1) }.maxByOrNull { it.length }
            ?: return null
        val cents = (amountText.toDoubleOrNull()?.times(100))?.toLong() ?: return null
        if (cents <= 0) return null
        val note = merged.take(140)
        val fingerprint = "${source}_${cents}_${note.hashCode()}_${postTime / 10_000}"
        return ParsedPayment(amountCents = cents, source = source, note = note, fingerprint = fingerprint)
    }
}
