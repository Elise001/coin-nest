package com.example.coin_nest.util

import java.text.NumberFormat
import java.util.Locale

object MoneyFormat {
    private val format = NumberFormat.getCurrencyInstance(Locale.CHINA)

    fun fromCents(cents: Long): String = format.format(cents / 100.0)
}

