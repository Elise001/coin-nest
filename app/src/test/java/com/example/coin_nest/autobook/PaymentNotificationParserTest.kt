package com.example.coin_nest.autobook

import com.example.coin_nest.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentNotificationParserTest {
    @Test
    fun `parse wechat expense notification`() {
        val parsed = PaymentNotificationParser.parse(
            packageName = "com.tencent.mm",
            title = "WeChat Pay",
            text = "Payment success CNY32.50 for food",
            postTime = 1_710_000_000_000
        )

        assertNotNull(parsed)
        assertEquals(3250L, parsed!!.amountCents)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("WECHAT", parsed.source)
    }

    @Test
    fun `parse alipay refund as income`() {
        val parsed = PaymentNotificationParser.parse(
            packageName = "com.eg.android.AlipayGphone",
            title = "Alipay",
            text = "Refund received CNY18.80",
            postTime = 1_710_000_500_000
        )

        assertNotNull(parsed)
        assertEquals(1880L, parsed!!.amountCents)
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals("收入", parsed.parentCategory)
        assertEquals("退款", parsed.childCategory)
    }

    @Test
    fun `ignore non transaction notification`() {
        val parsed = PaymentNotificationParser.parse(
            packageName = "com.tencent.mm",
            title = "WeChat",
            text = "Login verification code 123456",
            postTime = 1_710_001_000_000
        )
        assertNull(parsed)
    }
}
