package com.example.coin_nest.util

import java.math.BigDecimal
import java.math.RoundingMode

object MoneyParser {
    fun parseYuanToCents(input: String): Long? {
        val normalized = input
            .trim()
            .replace("\u00a5", "")
            .replace("\uffe5", "")
            .replace("RMB", "", ignoreCase = true)
            .replace("CNY", "", ignoreCase = true)
            .replace(",", "")
            .replace(" ", "")
        if (normalized.isBlank()) return null
        val amount = normalized.toBigDecimalOrNull() ?: return null
        if (amount <= BigDecimal.ZERO) return null
        return runCatching {
            amount
                .movePointRight(2)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact()
        }.getOrNull()
    }
}
