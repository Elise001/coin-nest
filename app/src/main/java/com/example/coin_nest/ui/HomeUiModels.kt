package com.example.coin_nest.ui

import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal enum class MainTab(val title: String) {
    Overview("概览"),
    Record("记账"),
    Settings("设置")
}

internal enum class OverviewTabMode(val title: String) {
    Daily("日"),
    Weekly("周"),
    Monthly("月"),
    Yearly("年")
}

internal data class DayAmountSummary(
    val incomeCents: Long = 0L,
    val expenseCents: Long = 0L
) {
    val balanceCents: Long get() = incomeCents - expenseCents
}

internal data class TrendPoint(val label: String, val expenseCents: Long)
internal data class AxisMarker(val label: String, val value: Long)

internal data class CategoryShare(
    val name: String,
    val amountCents: Long,
    val ratio: Float
)

internal data class RecordTemplate(
    val label: String,
    val amountYuan: String,
    val isIncome: Boolean,
    val parent: String,
    val child: String,
    val note: String
)

internal data class ReportSnapshot(
    val title: String,
    val totalExpenseCents: Long,
    val topCategoryName: String,
    val topCategoryExpenseCents: Long,
    val maxExpenseCents: Long,
    val maxExpenseLabel: String,
    val changeSummary: String
)

internal val zone: ZoneId = ZoneId.systemDefault()
internal val rowTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
internal val dateOnlyFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
