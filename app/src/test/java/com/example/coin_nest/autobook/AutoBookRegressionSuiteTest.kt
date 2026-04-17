package com.example.coin_nest.autobook

import com.example.coin_nest.data.AUTO_SAME_SOURCE_WINDOW_DUPLICATE_MS
import com.example.coin_nest.data.isWithinAutoSameSourceWindow
import com.example.coin_nest.data.shouldDedupeByAutoChannel
import com.example.coin_nest.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBookRegressionSuiteTest {

    @Test
    fun `case 1 notification success page should parse payment amount`() {
        val parsed = PaymentNotificationParser.parseWithDebug(
            packageName = "com.eg.android.AlipayGphone",
            title = "支付成功通知",
            text = "账户183***@163.com 于04月13日12时17分 成功付款10.80元",
            postTime = 1_710_200_000_000
        ).payment

        assertNotNull(parsed)
        assertEquals(1080L, parsed!!.amountCents)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("ALIPAY", parsed.source)
    }

    @Test
    fun `case 2 no-notification success page content should still parse`() {
        val parsed = PaymentNotificationParser.parseWithDebug(
            packageName = "com.tencent.mm",
            title = "支付结果",
            text = "支付成功 已支付¥23.50",
            postTime = 1_710_200_100_000
        ).payment

        assertNotNull(parsed)
        assertEquals(2350L, parsed!!.amountCents)
        assertEquals("WECHAT", parsed.source)
    }

    @Test
    fun `case 3 same order from notify plus accessibility should share dedupe fingerprint when txn ref exists`() {
        val fromNotify = PaymentNotificationParser.parseWithDebug(
            packageName = "com.eg.android.AlipayGphone",
            title = "支付成功通知",
            text = "支付成功 金额￥230.00 订单号:T202604130001",
            postTime = 1_710_200_200_000
        ).payment
        val fromAccessibility = PaymentNotificationParser.parseWithDebug(
            packageName = "com.eg.android.AlipayGphone",
            title = "支付成功",
            text = "支付成功 已支付230.00元 订单号:T202604130001",
            postTime = 1_710_200_205_000
        ).payment

        assertNotNull(fromNotify)
        assertNotNull(fromAccessibility)
        assertEquals(fromNotify!!.fingerprint, fromAccessibility!!.fingerprint)
        assertEquals("SRC_TXN_ALIPAY_T202604130001", fromNotify.fingerprint)
    }

    @Test
    fun `regression account tail number should not be recognized as amount`() {
        val result = PaymentNotificationParser.parseWithDebug(
            packageName = "cmb.pb",
            title = "招行信用卡",
            text = "您尾号4921的招行信用卡消费230.00人民币",
            postTime = 1_710_200_300_000
        )
        assertNotNull(result.payment)
        assertEquals(23_000L, result.payment!!.amountCents)
    }

    @Test
    fun `regression fund confirmation should be ignored`() {
        val result = PaymentNotificationParser.parseWithDebug(
            packageName = "com.eg.android.AlipayGphone",
            title = "基金申购确认成功通知",
            text = "确认金额1200.00元，手续费0元，基金已确认成功",
            postTime = 1_710_200_400_000
        )

        assertNull(result.payment)
        assertTrue(result.reason.contains("非支付确认类通知"))
    }

    @Test
    fun `window dedupe rule should treat close events as duplicates`() {
        val t1 = 1_710_200_500_000L
        val within = t1 + AUTO_SAME_SOURCE_WINDOW_DUPLICATE_MS - 5
        val outside = t1 + AUTO_SAME_SOURCE_WINDOW_DUPLICATE_MS + 5

        assertTrue(isWithinAutoSameSourceWindow(t1, within))
        assertTrue(!isWithinAutoSameSourceWindow(t1, outside))
    }

    @Test
    fun `channel dedupe should only work across notify and access`() {
        assertTrue(shouldDedupeByAutoChannel("NOTIFY", "ACCESS"))
        assertTrue(shouldDedupeByAutoChannel("ACCESS", "NOTIFY"))
        assertTrue(!shouldDedupeByAutoChannel("NOTIFY", "NOTIFY"))
        assertTrue(!shouldDedupeByAutoChannel("ACCESS", "ACCESS"))
    }
}
