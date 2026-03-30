package com.example.coin_nest.autobook

private val amountRegex = Regex("(\\d+(?:\\.\\d{1,2})?)")

data class ParsedPayment(
    val amountCents: Long,
    val source: String,
    val note: String
)

object PaymentNotificationParser {
    fun parse(packageName: String?, title: String?, text: String?): ParsedPayment? {
        val source = when (packageName) {
            "com.eg.android.AlipayGphone" -> "ALIPAY"
            "com.tencent.mm" -> "WECHAT"
            else -> return null
        }
        val merged = listOfNotNull(title, text).joinToString(" ")
        val amountText = amountRegex.find(merged)?.groupValues?.getOrNull(1) ?: return null
        val cents = (amountText.toDoubleOrNull()?.times(100))?.toLong() ?: return null
        if (cents <= 0) return null
        val note = merged.take(120)
        return ParsedPayment(amountCents = cents, source = source, note = note)
    }
}

