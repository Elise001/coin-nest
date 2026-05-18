package com.example.coin_nest.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBookMergeScorerTest {
    @Test
    fun `same alipay notify and accessibility capture should be dropped as duplicate`() {
        val decision = AutoBookMergeScorer.decide(
            existingSource = "ALIPAY",
            incomingSource = "ALIPAY",
            existingChannel = "NOTIFY",
            incomingChannel = "ACCESS",
            timeDiffMs = 8_000L
        )

        assertEquals(AutoBookMergeAction.DROP_DUPLICATE, decision.action)
        assertTrue(decision.confidence >= 90)
    }

    @Test
    fun `same source repeated notification in very short window should be dropped`() {
        val decision = AutoBookMergeScorer.decide(
            existingSource = "WECHAT",
            incomingSource = "WECHAT",
            existingChannel = "NOTIFY",
            incomingChannel = "NOTIFY",
            timeDiffMs = 12_000L
        )

        assertEquals(AutoBookMergeAction.DROP_DUPLICATE, decision.action)
    }

    @Test
    fun `payment app and bank card same amount nearby should link related`() {
        val decision = AutoBookMergeScorer.decide(
            existingSource = "ALIPAY",
            incomingSource = "BANK_CARD",
            existingChannel = "NOTIFY",
            incomingChannel = "NOTIFY",
            timeDiffMs = 105_000L
        )

        assertEquals(AutoBookMergeAction.LINK_RELATED, decision.action)
        assertTrue(decision.confidence >= 80)
    }

    @Test
    fun `payment apps should not be merged with each other`() {
        val decision = AutoBookMergeScorer.decide(
            existingSource = "ALIPAY",
            incomingSource = "WECHAT",
            existingChannel = "NOTIFY",
            incomingChannel = "NOTIFY",
            timeDiffMs = 10_000L
        )

        assertEquals(AutoBookMergeAction.NONE, decision.action)
    }

    @Test
    fun `cross source outside tolerance should not link`() {
        val decision = AutoBookMergeScorer.decide(
            existingSource = "CREDIT_CARD",
            incomingSource = "WECHAT",
            existingChannel = "NOTIFY",
            incomingChannel = "NOTIFY",
            timeDiffMs = AUTO_CROSS_SOURCE_WINDOW_DUPLICATE_MS + 1
        )

        assertEquals(AutoBookMergeAction.NONE, decision.action)
    }
}
