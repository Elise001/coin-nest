package com.example.coin_nest.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DateRangeUtils {
    private val zone = ZoneId.systemDefault()
    private val monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

    fun todayRange(nowMs: Long = System.currentTimeMillis()): LongRange {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start until end
    }

    fun monthRange(nowMs: Long = System.currentTimeMillis()): LongRange {
        val date = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val first = date.withDayOfMonth(1)
        val start = first.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = first.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start until end
    }

    fun yearRange(nowMs: Long = System.currentTimeMillis()): LongRange {
        val date = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val first = LocalDate.of(date.year, 1, 1)
        val start = first.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = first.plusYears(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start until end
    }

    fun currentMonthKey(nowMs: Long = System.currentTimeMillis()): String {
        val date = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        return date.format(monthFormatter)
    }
}

