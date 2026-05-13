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
            title = "微信支付",
            text = "支付成功，金额￥32.50，餐饮消费",
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
            title = "支付宝",
            text = "退款到账，金额￥18.80",
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
            title = "微信",
            text = "登录验证码 123456，请勿泄露",
            postTime = 1_710_001_000_000
        )
        assertNull(parsed)
    }

    @Test
    fun `ignore coupon success even when amount exists`() {
        val parsed = PaymentNotificationParser.parse(
            packageName = "com.eg.android.AlipayGphone",
            title = "优惠券领取成功",
            text = "优惠券到账，面额￥10.00，可用于下次支付",
            postTime = 1_710_001_100_000
        )

        assertNull(parsed)
    }

    @Test
    fun `ignore wealth management success even with payment keyword`() {
        val parsed = PaymentNotificationParser.parse(
            packageName = "com.eg.android.AlipayGphone",
            title = "理财买入成功",
            text = "支付成功，基金申购金额￥1200.00，确认份额以页面为准",
            postTime = 1_710_001_200_000
        )

        assertNull(parsed)
    }
}
