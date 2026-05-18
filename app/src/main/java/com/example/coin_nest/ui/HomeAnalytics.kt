package com.example.coin_nest.ui

import com.example.coin_nest.data.db.CategoryBudgetEntity
import com.example.coin_nest.data.db.TransactionEntity
import com.example.coin_nest.util.MoneyFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

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

internal data class AnomalyInsight(
    val id: String,
    val title: String,
    val detail: String,
    val level: String,
    val reason: String,
    val suggestions: List<String>,
    val relatedTransactions: List<TransactionEntity>
)

internal data class SpendingAiInsight(
    val anomalyExplanation: String,
    val habitProfile: String,
    val budgetAdvice: String,
    val suggestedBudgetCents: Long?,
    val confidenceLabel: String
)

internal fun buildSpendingAiInsight(
    monthTx: List<TransactionEntity>,
    previousMonthExpenseCents: Long,
    monthBudgetCents: Long?,
    anomalies: List<AnomalyInsight>
): SpendingAiInsight {
    val expenseTx = monthTx.filter { it.type == "EXPENSE" }
    if (expenseTx.isEmpty()) {
        return SpendingAiInsight(
            anomalyExplanation = "本月还没有足够支出样本，暂不判断异常。",
            habitProfile = "画像待生成：先积累 7 天以上流水，系统会更稳。",
            budgetAdvice = "预算建议待生成：建议先设一个保守月预算，再根据自动记账校准。",
            suggestedBudgetCents = null,
            confidenceLabel = "样本不足"
        )
    }

    val totalExpense = expenseTx.sumOf { it.amountCents }
    val expenseByDate = expenseTx.groupBy { Instant.ofEpochMilli(it.occurredAtEpochMs).atZone(zone).toLocalDate() }
    val activeDays = expenseByDate.size.coerceAtLeast(1)
    val dailyAverage = totalExpense / activeDays
    val topCategory = expenseTx
        .groupBy { it.parentCategory }
        .mapValues { (_, list) -> list.sumOf { it.amountCents } }
        .maxByOrNull { it.value }
    val topCategoryRatio = topCategory?.let { it.value.toDouble() / totalExpense.coerceAtLeast(1L).toDouble() } ?: 0.0
    val weekendExpense = expenseTx
        .filter {
            val day = Instant.ofEpochMilli(it.occurredAtEpochMs).atZone(zone).dayOfWeek
            day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
        }
        .sumOf { it.amountCents }
    val weekendRatio = weekendExpense.toDouble() / totalExpense.coerceAtLeast(1L).toDouble()
    val smallFrequentCount = expenseTx.count { it.amountCents in 1L..3_000L }
    val confidenceLabel = when {
        activeDays >= 20 && expenseTx.size >= 40 -> "高"
        activeDays >= 10 && expenseTx.size >= 18 -> "中"
        else -> "低"
    }

    val anomalyExplanation = when {
        anomalies.isNotEmpty() -> {
            val first = anomalies.first()
            "最需要看的是「${first.title}」：${first.reason} 这不是简单报错，而是在提示本月节奏已经偏离你的常态。"
        }
        previousMonthExpenseCents > 0L && totalExpense < previousMonthExpenseCents * 0.85 -> {
            "本月支出低于上月 15% 以上，异常方向是正向的：消费节奏变稳，可以继续观察是否来自真实节省。"
        }
        else -> "没有发现强异常，当前主要任务是保持自动记账，让样本继续变厚。"
    }

    val habitProfile = buildHabitProfileText(
        topCategoryName = topCategory?.key,
        topCategoryRatio = topCategoryRatio,
        weekendRatio = weekendRatio,
        smallFrequentCount = smallFrequentCount,
        txCount = expenseTx.size,
        dailyAverage = dailyAverage
    )

    val suggestedBudget = suggestMonthlyBudget(
        currentExpenseCents = totalExpense,
        previousExpenseCents = previousMonthExpenseCents,
        currentBudgetCents = monthBudgetCents
    )
    val budgetAdvice = buildBudgetAdviceText(
        totalExpense = totalExpense,
        dailyAverage = dailyAverage,
        monthBudgetCents = monthBudgetCents,
        suggestedBudgetCents = suggestedBudget
    )

    return SpendingAiInsight(
        anomalyExplanation = anomalyExplanation,
        habitProfile = habitProfile,
        budgetAdvice = budgetAdvice,
        suggestedBudgetCents = suggestedBudget,
        confidenceLabel = confidenceLabel
    )
}

internal fun buildMonthlyAnomalies(
    monthTx: List<TransactionEntity>,
    previousMonthExpenseCents: Long,
    monthBudgetCents: Long?,
    categoryBudgets: List<CategoryBudgetEntity>
): List<AnomalyInsight> {
    val expenseTx = monthTx.filter { it.type == "EXPENSE" }
    if (expenseTx.isEmpty()) return emptyList()

    val anomalies = mutableListOf<AnomalyInsight>()
    val totalExpense = expenseTx.sumOf { it.amountCents }
    val avgExpense = (totalExpense / expenseTx.size).coerceAtLeast(1L)

    val maxTx = expenseTx.maxByOrNull { it.amountCents }
    if (maxTx != null && maxTx.amountCents >= maxOf(200_00L, avgExpense * 4)) {
        anomalies += AnomalyInsight(
            id = "LARGE_SINGLE_${maxTx.id}",
            title = "出现大额单笔支出",
            detail = "${maxTx.parentCategory}/${maxTx.childCategory} 单笔 ${MoneyFormat.fromCents(maxTx.amountCents)}，显著高于本月均值。",
            level = "HIGH",
            reason = "单笔金额超过本月平均支出的 4 倍，或超过 200 元阈值。",
            suggestions = listOf("回看这笔支出的必要性", "如属于可控消费，下周设置同类日上限"),
            relatedTransactions = listOf(maxTx)
        )
    }

    if (previousMonthExpenseCents > 0L) {
        val delta = totalExpense - previousMonthExpenseCents
        val ratio = kotlin.math.abs(delta).toDouble() / previousMonthExpenseCents.toDouble()
        if (delta > 0L && ratio >= 0.3) {
            anomalies += AnomalyInsight(
                id = "MONTHLY_SURGE",
                title = "月支出较上月明显上涨",
                detail = "本月较上月上涨 ${(ratio * 100).roundToInt()}%（+${MoneyFormat.fromCents(delta)}）。",
                level = "MEDIUM",
                reason = "当月总支出较上月增幅达到 30% 以上。",
                suggestions = listOf("优先查看本月 Top 分类与大额流水", "为高频分类加预算约束"),
                relatedTransactions = expenseTx.sortedByDescending { it.amountCents }.take(5)
            )
        }
    }

    val expenseByParent = expenseTx.groupBy { it.parentCategory }.mapValues { (_, list) -> list.sumOf { it.amountCents } }
    val topParent = expenseByParent.maxByOrNull { it.value }
    if (topParent != null && totalExpense > 0L) {
        val ratio = topParent.value.toDouble() / totalExpense.toDouble()
        if (ratio >= 0.45 && topParent.value >= 500_00L) {
            anomalies += AnomalyInsight(
                id = "CATEGORY_CONCENTRATION_${topParent.key}",
                title = "支出集中在单一分类",
                detail = "${topParent.key} 占比 ${(ratio * 100).roundToInt()}%，存在结构性风险。",
                level = "MEDIUM",
                reason = "单一一级分类占比超过 45%，且金额已超过 500 元。",
                suggestions = listOf("检查该分类是否出现短期冲动消费", "把该分类设为重点预算监控项"),
                relatedTransactions = expenseTx.filter { it.parentCategory == topParent.key }
                    .sortedByDescending { it.amountCents }
                    .take(5)
            )
        }
    }

    if (monthBudgetCents != null && monthBudgetCents > 0) {
        val ratio = totalExpense.toDouble() / monthBudgetCents.toDouble()
        if (ratio >= 1.0) {
            anomalies += AnomalyInsight(
                id = "MONTH_BUDGET_EXCEEDED",
                title = "总预算已超额",
                detail = "本月支出 ${MoneyFormat.fromCents(totalExpense)} / 预算 ${MoneyFormat.fromCents(monthBudgetCents)}。",
                level = "HIGH",
                reason = "总预算使用率已达到或超过 100%。",
                suggestions = listOf("本周仅保留必要支出", "暂停非必要高频消费场景"),
                relatedTransactions = expenseTx.sortedByDescending { it.amountCents }.take(5)
            )
        } else if (ratio >= 0.8) {
            anomalies += AnomalyInsight(
                id = "MONTH_BUDGET_WARN",
                title = "总预算接近上限",
                detail = "预算使用率 ${(ratio * 100).roundToInt()}%，建议控制接下来一周支出。",
                level = "LOW",
                reason = "总预算使用率达到 80% 预警阈值。",
                suggestions = listOf("接下来 3-7 天优先控制可选消费", "保持自动记账，避免漏记导致误判"),
                relatedTransactions = expenseTx.sortedByDescending { it.amountCents }.take(5)
            )
        }
    }

    if (categoryBudgets.isNotEmpty()) {
        val categoryExpenseMap = expenseTx.groupBy { it.parentCategory to it.childCategory }
            .mapValues { (_, list) -> list.sumOf { it.amountCents } }
        categoryBudgets.forEach { budget ->
            val used = categoryExpenseMap[budget.parentCategory to budget.childCategory] ?: 0L
            if (budget.limitCents > 0L && used > budget.limitCents) {
                anomalies += AnomalyInsight(
                    id = "CATEGORY_BUDGET_EXCEEDED_${budget.parentCategory}_${budget.childCategory}",
                    title = "分类预算超额：${budget.parentCategory}/${budget.childCategory}",
                    detail = "已用 ${MoneyFormat.fromCents(used)} / 预算 ${MoneyFormat.fromCents(budget.limitCents)}。",
                    level = "HIGH",
                    reason = "该二级分类月度支出已超过设定预算。",
                    suggestions = listOf("本月暂停该分类非刚需支出", "下月重新评估该分类预算上限"),
                    relatedTransactions = expenseTx.filter {
                        it.parentCategory == budget.parentCategory && it.childCategory == budget.childCategory
                    }.sortedByDescending { it.amountCents }.take(5)
                )
            }
        }
    }

    return anomalies.take(5)
}

private fun buildHabitProfileText(
    topCategoryName: String?,
    topCategoryRatio: Double,
    weekendRatio: Double,
    smallFrequentCount: Int,
    txCount: Int,
    dailyAverage: Long
): String {
    val categoryText = if (topCategoryName != null && topCategoryRatio >= 0.35) {
        "你的消费明显集中在「$topCategoryName」，占比 ${(topCategoryRatio * 100).roundToInt()}%。"
    } else {
        "你的消费结构相对分散，没有单一分类过度主导。"
    }
    val rhythmText = if (weekendRatio >= 0.42) {
        "休息日支出占比较高，更像“周末释放型”。"
    } else {
        "工作日支出更稳定，更像“日常节奏型”。"
    }
    val frequencyText = if (txCount > 0 && smallFrequentCount.toDouble() / txCount.toDouble() >= 0.45) {
        "高频小额不少，适合重点盯通勤、餐饮、咖啡这类无感累积。"
    } else {
        "支出更偏少量中大额，适合重点复盘单笔必要性。"
    }
    return "$categoryText $rhythmText $frequencyText 日均支出约 ${MoneyFormat.fromCents(dailyAverage)}。"
}

private fun suggestMonthlyBudget(
    currentExpenseCents: Long,
    previousExpenseCents: Long,
    currentBudgetCents: Long?
): Long? {
    val baseline = when {
        previousExpenseCents > 0L -> ((currentExpenseCents + previousExpenseCents) / 2)
        currentExpenseCents > 0L -> currentExpenseCents
        else -> return currentBudgetCents
    }
    val suggested = (baseline * 0.92).toLong().coerceAtLeast(300_00L)
    return roundBudgetCents(suggested)
}

private fun buildBudgetAdviceText(
    totalExpense: Long,
    dailyAverage: Long,
    monthBudgetCents: Long?,
    suggestedBudgetCents: Long?
): String {
    if (suggestedBudgetCents == null) return "暂时无法给出预算建议。"
    val suggestedText = MoneyFormat.fromCents(suggestedBudgetCents)
    if (monthBudgetCents == null || monthBudgetCents <= 0L) {
        return "建议先把下月总预算设为 $suggestedText，再给最大分类单独设上限。"
    }
    val ratio = totalExpense.toDouble() / monthBudgetCents.toDouble()
    return when {
        ratio >= 1.0 -> "当前预算已经超额。下月建议预算参考 $suggestedText，同时把日均支出压到 ${MoneyFormat.fromCents(dailyAverage * 9 / 10)} 左右。"
        ratio >= 0.85 -> "当前预算偏紧。下月建议预算参考 $suggestedText，并提前给高频分类加提醒。"
        ratio <= 0.55 -> "当前预算偏宽。下月可尝试把预算收敛到 $suggestedText，让目标更有约束感。"
        else -> "当前预算节奏基本合理。下月预算可参考 $suggestedText，重点优化最大分类即可。"
    }
}

private fun roundBudgetCents(value: Long): Long {
    val step = 10_000L
    return ((value + step / 2) / step * step).coerceAtLeast(step)
}

