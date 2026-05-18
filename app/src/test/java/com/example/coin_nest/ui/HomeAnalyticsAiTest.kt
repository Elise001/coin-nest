package com.example.coin_nest.ui

import com.example.coin_nest.data.db.TransactionEntity
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAnalyticsAiTest {
    @Test
    fun `empty transactions should produce low confidence guidance`() {
        val insight = buildSpendingAiInsight(
            monthTx = emptyList(),
            previousMonthExpenseCents = 0L,
            monthBudgetCents = null,
            anomalies = emptyList()
        )

        assertEquals("样本不足", insight.confidenceLabel)
        assertEquals(null, insight.suggestedBudgetCents)
        assertTrue(insight.habitProfile.contains("画像待生成"))
    }

    @Test
    fun `high restaurant concentration should explain habit and suggest budget`() {
        val txs = listOf(
            expense(1, 8_00, "工作日", "通勤", 2026, 5, 18, 8),
            expense(2, 4_200_00, "网购", "日常网购", 2026, 5, 18, 12),
            expense(3, 3_600_00, "网购", "日常网购", 2026, 5, 19, 20),
            expense(4, 2_000_00, "工作日", "工作餐", 2026, 5, 20, 12),
            expense(5, 6_000_00, "休息日", "休闲餐饮", 2026, 5, 23, 19)
        )
        val anomalies = buildMonthlyAnomalies(
            monthTx = txs,
            previousMonthExpenseCents = 8_000_00,
            monthBudgetCents = 12_000_00,
            categoryBudgets = emptyList()
        )

        val insight = buildSpendingAiInsight(
            monthTx = txs,
            previousMonthExpenseCents = 8_000_00,
            monthBudgetCents = 12_000_00,
            anomalies = anomalies
        )

        assertTrue(insight.habitProfile.contains("休息日") || insight.habitProfile.contains("工作日"))
        assertTrue(insight.anomalyExplanation.contains("最需要看的是"))
        assertNotNull(insight.suggestedBudgetCents)
        assertTrue(insight.budgetAdvice.contains("预算"))
    }

    @Test
    fun `positive anomaly should explain reduced spending`() {
        val txs = listOf(
            expense(1, 1_000_00, "工作日", "工作餐", 2026, 5, 18, 12),
            expense(2, 1_000_00, "工作日", "通勤", 2026, 5, 19, 8)
        )

        val insight = buildSpendingAiInsight(
            monthTx = txs,
            previousMonthExpenseCents = 3_000_00,
            monthBudgetCents = 5_000_00,
            anomalies = emptyList()
        )

        assertTrue(insight.anomalyExplanation.contains("正向"))
    }

    private fun expense(
        id: Long,
        amountCents: Long,
        parent: String,
        child: String,
        year: Int,
        month: Int,
        day: Int,
        hour: Int
    ): TransactionEntity {
        return TransactionEntity(
            id = id,
            amountCents = amountCents,
            type = "EXPENSE",
            parentCategory = parent,
            childCategory = child,
            source = "TEST",
            note = "",
            occurredAtEpochMs = LocalDateTime.of(year, month, day, hour, 0)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        )
    }
}
