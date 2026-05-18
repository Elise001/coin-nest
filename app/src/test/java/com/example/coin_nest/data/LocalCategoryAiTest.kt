package com.example.coin_nest.data

import com.example.coin_nest.data.model.TransactionType
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCategoryAiTest {
    private val zone = ZoneId.systemDefault()

    @Test
    fun `generic weekday morning payment is classified as commute`() {
        val decision = LocalCategoryAi.decide(
            type = TransactionType.EXPENSE,
            source = "ALIPAY",
            note = "支付宝一笔5元的支出",
            amountCents = 500L,
            occurredAtEpochMs = epoch(2026, 5, 18, 8, 12)
        )

        assertEquals("工作日", decision?.parentCategory)
        assertEquals("通勤", decision?.childCategory)
        assertTrue((decision?.confidence ?: 0) >= 80)
    }

    @Test
    fun `shopping platform is classified as online shopping`() {
        val decision = LocalCategoryAi.decide(
            type = TransactionType.EXPENSE,
            source = "JD",
            note = "京东支付成功 128.00",
            amountCents = 12_800L,
            occurredAtEpochMs = epoch(2026, 5, 18, 15, 20)
        )

        assertEquals("网购", decision?.parentCategory)
        assertEquals("日常网购", decision?.childCategory)
    }

    @Test
    fun `weekend generic payment falls into rest day`() {
        val decision = LocalCategoryAi.decide(
            type = TransactionType.EXPENSE,
            source = "WECHAT",
            note = "微信支付一笔支出",
            amountCents = 3_500L,
            occurredAtEpochMs = epoch(2026, 5, 23, 18, 30)
        )

        assertEquals("休息日", decision?.parentCategory)
        assertEquals("休闲餐饮", decision?.childCategory)
    }

    @Test
    fun `learning tokens include amount time and day features`() {
        val tokens = LocalCategoryAi.learningTokens(
            source = "ALIPAY",
            amountCents = 500L,
            occurredAtEpochMs = epoch(2026, 5, 18, 8, 12)
        )

        assertTrue("SRC_ALIPAY" in tokens)
        assertTrue("AMT_0_5" in tokens)
        assertTrue("HOUR_8" in tokens)
        assertTrue("DAY_WORK" in tokens)
    }

    private fun epoch(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }
}
