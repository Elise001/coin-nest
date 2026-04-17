package com.example.coin_nest.autobook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentNotificationDedupeTest {

    @Test
    fun `same source without transaction ref should not create same-source dedupe key`() {
        val first = PaymentNotificationParser.parseWithDebug(
            packageName = "com.eg.android.AlipayGphone",
            title = "支付宝",
            text = "支付成功 金额￥10.00 咖啡",
            postTime = 1_710_000_000_000
        ).payment
        val second = PaymentNotificationParser.parseWithDebug(
            packageName = "com.eg.android.AlipayGphone",
            title = "支付宝",
            text = "支付成功 金额￥10.00 咖啡",
            postTime = 1_710_000_005_000
        ).payment

        assertNotNull(first)
        assertNotNull(second)
        assertNull(first!!.transactionRef)
        assertNull(second!!.transactionRef)
        assertNull(first.fingerprint)
        assertNull(second.fingerprint)
    }

    @Test
    fun `same source with same transaction ref should build identical fingerprint`() {
        val first = PaymentNotificationParser.parseWithDebug(
            packageName = "com.eg.android.AlipayGphone",
            title = "支付宝",
            text = "支付成功 金额￥10.00 订单号:T202604080001",
            postTime = 1_710_000_000_000
        ).payment
        val second = PaymentNotificationParser.parseWithDebug(
            packageName = "com.eg.android.AlipayGphone",
            title = "支付宝",
            text = "支付成功 金额￥10.00 订单号:T202604080001",
            postTime = 1_710_000_010_000
        ).payment

        assertNotNull(first)
        assertNotNull(second)
        assertEquals("T202604080001", first!!.transactionRef)
        assertEquals(first.fingerprint, second!!.fingerprint)
    }

    @Test
    fun `unsupported package returns clear debug reason`() {
        val result = PaymentNotificationParser.parseWithDebug(
            packageName = "com.example.unknown",
            title = "支付",
            text = "扣款￥10.00",
            postTime = 1_710_000_000_000
        )

        assertNull(result.payment)
        assertTrue(result.reason.contains("com.example.unknown"))
    }
}

