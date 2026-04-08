package com.example.coin_nest.ui

import com.example.coin_nest.data.db.TransactionEntity
import com.example.coin_nest.util.MoneyFormat
import java.time.Instant
import java.time.LocalDate

internal fun buildWeekLineTrend(weekTx: List<TransactionEntity>, weekStart: LocalDate): List<TrendPoint> {
    val expenseByDay = weekTx.asSequence()
        .filter { it.type == "EXPENSE" }
        .groupBy { Instant.ofEpochMilli(it.occurredAtEpochMs).atZone(zone).toLocalDate() }
        .mapValues { it.value.sumOf { tx -> tx.amountCents } }
    return (0..6).map { offset ->
        val day = weekStart.plusDays(offset.toLong())
        TrendPoint(label = "${day.monthValue}/${day.dayOfMonth}", expenseCents = expenseByDay[day] ?: 0L)
    }
}

internal fun buildMonthLineTrend(monthTx: List<TransactionEntity>, month: java.time.YearMonth): List<TrendPoint> {
    if (monthTx.isEmpty()) return emptyList()
    val expenseByDay = monthTx.asSequence()
        .filter { it.type == "EXPENSE" }
        .groupBy { Instant.ofEpochMilli(it.occurredAtEpochMs).atZone(zone).toLocalDate().dayOfMonth }
        .mapValues { it.value.sumOf { tx -> tx.amountCents } }
    val allDays = (1..month.lengthOfMonth()).toList()
    val maxBars = 15
    val step = kotlin.math.ceil(allDays.size / maxBars.toDouble()).toInt().coerceAtLeast(1)
    return allDays
        .chunked(step)
        .map { chunk ->
            val label = chunk.first().toString()
            val sum = chunk.sumOf { day -> expenseByDay[day] ?: 0L }
            TrendPoint(label = label, expenseCents = sum)
        }
}

internal fun buildYearLineTrend(yearTx: List<TransactionEntity>): List<TrendPoint> {
    if (yearTx.isEmpty()) return emptyList()
    val expenseByMonth = yearTx.asSequence()
        .filter { it.type == "EXPENSE" }
        .groupBy { Instant.ofEpochMilli(it.occurredAtEpochMs).atZone(zone).toLocalDate().monthValue }
        .mapValues { it.value.sumOf { tx -> tx.amountCents } }
    return (1..12).map { month -> TrendPoint(label = "${month}月", expenseCents = expenseByMonth[month] ?: 0L) }
}

internal fun buildCategoryShare(txs: List<TransactionEntity>): List<CategoryShare> {
    val expenseTx = txs.filter { it.type == "EXPENSE" }
    if (expenseTx.isEmpty()) return emptyList()
    val total = expenseTx.sumOf { it.amountCents }.coerceAtLeast(1L)
    return expenseTx.groupBy { it.parentCategory }.map { (name, list) ->
        val amount = list.sumOf { it.amountCents }
        CategoryShare(name = name, amountCents = amount, ratio = amount.toFloat() / total.toFloat())
    }.sortedByDescending { it.amountCents }
}

internal fun calculateDaySummary(map: Map<LocalDate, List<TransactionEntity>>): Map<LocalDate, DayAmountSummary> {
    return map.mapValues { (_, txs) ->
        var income = 0L
        var expense = 0L
        txs.forEach { tx -> if (tx.type == "INCOME") income += tx.amountCents else expense += tx.amountCents }
        DayAmountSummary(incomeCents = income, expenseCents = expense)
    }
}

internal fun calculateSummary(txs: List<TransactionEntity>): DayAmountSummary {
    var income = 0L
    var expense = 0L
    txs.forEach { tx -> if (tx.type == "INCOME") income += tx.amountCents else expense += tx.amountCents }
    return DayAmountSummary(incomeCents = income, expenseCents = expense)
}

internal fun buildXAxisMarkers(points: List<TrendPoint>): List<AxisMarker> {
    if (points.isEmpty()) return emptyList()
    if (points.size <= 7) return points.map { AxisMarker(it.label, it.expenseCents) }

    val indexes = when {
        points.size <= 12 -> {
            val base = (0..points.lastIndex step 2).toMutableList()
            if (base.last() != points.lastIndex) base.add(points.lastIndex)
            base
        }
        else -> {
            listOf(0, points.size / 5, points.size * 2 / 5, points.size * 3 / 5, points.size * 4 / 5, points.lastIndex)
        }
    }.distinct()
    return indexes.map { idx -> AxisMarker(points[idx].label, points[idx].expenseCents) }
}

internal fun reportSnapshotLines(snapshot: ReportSnapshot): List<String> {
    return listOf(
        "Top 分类：${snapshot.topCategoryName}（${MoneyFormat.fromCents(snapshot.topCategoryExpenseCents)}）",
        "最大单笔：${MoneyFormat.fromCents(snapshot.maxExpenseCents)} · ${snapshot.maxExpenseLabel}",
        snapshot.changeSummary
    )
}

internal fun buildReportSnapshot(
    title: String,
    txs: List<TransactionEntity>,
    previousExpenseCents: Long
): ReportSnapshot {
    val expenseTx = txs.filter { it.type == "EXPENSE" }
    val totalExpense = expenseTx.sumOf { it.amountCents }
    val topEntry = expenseTx.groupBy { it.parentCategory }
        .mapValues { (_, list) -> list.sumOf { it.amountCents } }
        .maxByOrNull { it.value }
    val maxTx = expenseTx.maxByOrNull { it.amountCents }
    val delta = totalExpense - previousExpenseCents
    val ratio = if (previousExpenseCents > 0L) kotlin.math.abs(delta).toFloat() / previousExpenseCents.toFloat() * 100f else 0f
    val changeSummary = when {
        previousExpenseCents <= 0L && totalExpense <= 0L -> "与上一周期相比：暂无有效支出数据。"
        previousExpenseCents <= 0L -> "与上一周期相比：新增支出 ${MoneyFormat.fromCents(totalExpense)}。"
        delta > 0L -> "与上一周期相比：上升 ${ratio.toInt()}%（+${MoneyFormat.fromCents(delta)}）"
        delta < 0L -> "与上一周期相比：下降 ${ratio.toInt()}%（${MoneyFormat.fromCents(delta)}）"
        else -> "与上一周期相比：基本持平。"
    }
    val maxLabel = maxTx?.let {
        val d = Instant.ofEpochMilli(it.occurredAtEpochMs).atZone(zone).toLocalDate()
        "${it.parentCategory}/${it.childCategory} ${d.monthValue}/${d.dayOfMonth}"
    } ?: "无"
    return ReportSnapshot(
        title = title,
        totalExpenseCents = totalExpense,
        topCategoryName = topEntry?.key ?: "无",
        topCategoryExpenseCents = topEntry?.value ?: 0L,
        maxExpenseCents = maxTx?.amountCents ?: 0L,
        maxExpenseLabel = maxLabel,
        changeSummary = changeSummary
    )
}

