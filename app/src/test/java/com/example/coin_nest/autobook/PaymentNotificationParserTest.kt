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

    @Test
    fun `ignore wechat refund notice count without real amount`() {
        val parsed = PaymentNotificationParser.parse(
            packageName = "com.tencent.mm",
            title = "微信支付",
            text = "[2条]微信支付: 退款到账通知",
            postTime = 1_710_001_300_000
        )

        assertNull(parsed)
    }

    @Test
    fun `ignore jd beans notice and product model numbers`() {
        val parsed = PaymentNotificationParser.parse(
            packageName = "com.jingdong.app.mall",
            title = "京东通知",
            text = "您的京豆已到账【领取即将截止】【九号（Ninebot）远航家 M85C 电动...】可抵扣",
            postTime = 1_710_001_400_000
        )

        assertNull(parsed)
    }

    @Test
    fun `ignore jd promotion percent and product size numbers`() {
        val parsed = PaymentNotificationParser.parse(
            packageName = "com.jingdong.app.mall",
            title = "京东通知",
            text = "您的专属优惠【希川科颜20%王二酸凝胶祛痘精华霜15g...】特惠已到账，快来领取！",
            postTime = 1_710_001_500_000
        )

        assertNull(parsed)
    }

    @Test
    fun `prefer paid amount over original price and discount on alipay success page`() {
        val parsed = PaymentNotificationParser.parse(
            packageName = "com.eg.android.AlipayGphone",
            title = "支付宝",
            text = "支付宝 支付成功 回首页 ￥ 12.70 获得森林能量 柒一拾壹（北京）有限公司 ￥12.80 碰一下立减 -￥0.10 付款方式",
            postTime = 1_716_000_000_000
        )

        assertNotNull(parsed)
        assertEquals(1270L, parsed!!.amountCents)
        assertEquals(TransactionType.EXPENSE, parsed.type)
    }

    @Test
    fun `ignore jd cash reminder duration without real amount`() {
        val parsed = PaymentNotificationParser.parse(
            packageName = "com.jingdong.app.mall",
            title = "京东通知",
            text = "【提现提醒】您的现金打款已于24小时前到账，尚未处理，将于23:59过期，请及时",
            postTime = 1_716_000_000_000
        )

        assertNull(parsed)
    }

    @Test
    fun `ignore taobao unclaimed coupon amount`() {
        val parsed = PaymentNotificationParser.parse(
            packageName = "com.taobao.taobao",
            title = "淘宝通知",
            text = "您有485元88VIP消费券还未领取 开通88VIP即可领取，立即查看>>",
            postTime = 1_716_000_000_000
        )

        assertNull(parsed)
    }

    @Test
    fun `ignore jd subsidy ad popup amount`() {
        val parsed = PaymentNotificationParser.parse(
            packageName = "com.jingdong.app.mall",
            title = "京东通知",
            text = "你好 已到账: [1000.0元补贴]",
            postTime = 1_716_000_000_000
        )

        assertNull(parsed)
    }
}
