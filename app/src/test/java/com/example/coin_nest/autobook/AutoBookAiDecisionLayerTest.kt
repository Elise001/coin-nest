package com.example.coin_nest.autobook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBookAiDecisionLayerTest {
    @Test
    fun `accepts real alipay payment success text`() {
        val decision = AutoBookAiDecisionLayer.assess(
            packageName = "com.eg.android.AlipayGphone",
            mergedText = "支付宝 支付成功 回首页 ￥12.70 获得森林能量 柒一拾壹（北京）有限公司 ￥12.80 碰一下立减 -￥0.10 付款方式"
        )

        assertTrue(decision.accepted)
        assertEquals(AutoBookAiTextKind.TRANSACTION, decision.kind)
        assertTrue(decision.confidence >= 50)
    }

    @Test
    fun `accepts bank card consumption notice`() {
        val decision = AutoBookAiDecisionLayer.assess(
            packageName = "cmb.pb",
            mergedText = "招商银行 信用卡通知：您尾号4921的招商信用卡消费230.00人民币"
        )

        assertTrue(decision.accepted)
        assertEquals(AutoBookAiTextKind.TRANSACTION, decision.kind)
        assertTrue(decision.confidence >= 50)
    }

    @Test
    fun `rejects coupon ad with money`() {
        val decision = AutoBookAiDecisionLayer.assess(
            packageName = "com.taobao.taobao",
            mergedText = "您有485元88VIP消费券还未领取 开通88VIP即可领取，立即查看>>"
        )

        assertFalse(decision.accepted)
        assertEquals(AutoBookAiTextKind.MARKETING, decision.kind)
    }

    @Test
    fun `rejects subsidy popup pretending to arrive`() {
        val decision = AutoBookAiDecisionLayer.assess(
            packageName = "com.jingdong.app.mall",
            mergedText = "你好 已到账: [1000.0元补贴]"
        )

        assertFalse(decision.accepted)
        assertEquals(AutoBookAiTextKind.MARKETING, decision.kind)
    }

    @Test
    fun `rejects wealth management text even when payment success appears`() {
        val decision = AutoBookAiDecisionLayer.assess(
            packageName = "com.eg.android.AlipayGphone",
            mergedText = "支付成功，基金申购金额￥1200.00，确认份额以页面为准"
        )

        assertFalse(decision.accepted)
        assertEquals(AutoBookAiTextKind.WEALTH, decision.kind)
    }

    @Test
    fun `rejects repeated rejected content inside short window`() {
        val text = "芝麻周报 0511-0517 周报 分享 我的芝麻分 830 分 行为积累 15次 +135"
        val first = AutoBookAiDecisionLayer.assess(
            packageName = "com.eg.android.AlipayGphone",
            mergedText = text
        )
        val second = AutoBookAiDecisionLayer.assess(
            packageName = "com.eg.android.AlipayGphone",
            mergedText = text
        )

        assertFalse(first.accepted)
        assertFalse(second.accepted)
        assertEquals("重复拒绝窗口", second.reason)
    }
}
