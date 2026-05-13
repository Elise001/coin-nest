package com.example.coin_nest.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyParserTest {
    @Test
    fun `parse decimal yuan without losing cents`() {
        assertEquals(201L, MoneyParser.parseYuanToCents("2.01"))
        assertEquals(1029L, MoneyParser.parseYuanToCents("10.29"))
        assertEquals(120050L, MoneyParser.parseYuanToCents("1,200.50"))
    }

    @Test
    fun `reject unsupported precision and invalid amounts`() {
        assertNull(MoneyParser.parseYuanToCents("1.001"))
        assertNull(MoneyParser.parseYuanToCents("0"))
        assertNull(MoneyParser.parseYuanToCents("-1"))
        assertNull(MoneyParser.parseYuanToCents("abc"))
    }
}
