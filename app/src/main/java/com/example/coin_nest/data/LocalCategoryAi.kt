package com.example.coin_nest.data

import com.example.coin_nest.data.model.TransactionType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

data class LocalCategoryDecision(
    val parentCategory: String,
    val childCategory: String,
    val confidence: Int,
    val reason: String
)

object LocalCategoryAi {
    private val zone: ZoneId = ZoneId.systemDefault()

    fun decide(
        type: TransactionType,
        source: String,
        note: String,
        amountCents: Long,
        occurredAtEpochMs: Long
    ): LocalCategoryDecision? {
        if (type == TransactionType.INCOME) return decideIncome(note)

        val text = "${source.lowercase()} ${note.lowercase()}"
        specialCategory(text)?.let { return it }

        val localDateTime = Instant.ofEpochMilli(occurredAtEpochMs).atZone(zone)
        val time = localDateTime.toLocalTime()
        val isRestDay = localDateTime.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

        if (!isRestDay) {
            if (source.looksLikePaymentApp() && amountCents in 300L..800L && time in COMMUTE_WINDOW) {
                return LocalCategoryDecision("工作日", "通勤", 82, "AI:工作日早高峰小额")
            }
            if (amountCents in 800L..6_000L && time in LUNCH_WINDOW) {
                return LocalCategoryDecision("工作日", "工作餐", 66, "AI:工作日午间")
            }
            return LocalCategoryDecision("工作日", "日常", 54, "AI:工作日默认")
        }

        if (amountCents in 1_000L..20_000L && time in REST_OUTING_WINDOW) {
            return LocalCategoryDecision("休息日", "休闲餐饮", 58, "AI:休息日外出")
        }
        return LocalCategoryDecision("休息日", "日常", 54, "AI:休息日默认")
    }

    fun learningTokens(source: String, amountCents: Long, occurredAtEpochMs: Long): List<String> {
        val localDateTime = Instant.ofEpochMilli(occurredAtEpochMs).atZone(zone)
        val dayToken = if (localDateTime.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
            "DAY_REST"
        } else {
            "DAY_WORK"
        }
        return listOf(
            "SRC_${source.uppercase()}",
            "AMT_${amountBucket(amountCents)}",
            "HOUR_${localDateTime.hour}",
            dayToken
        )
    }

    private fun decideIncome(note: String): LocalCategoryDecision {
        val text = note.lowercase()
        return when {
            text.containsAny("退款", "退回", "返现") -> LocalCategoryDecision("收入", "退款", 80, "AI:退款收入")
            text.containsAny("工资", "薪资") -> LocalCategoryDecision("收入", "工资", 80, "AI:工资收入")
            text.containsAny("奖金", "绩效") -> LocalCategoryDecision("收入", "奖金", 76, "AI:奖金收入")
            text.containsAny("转账", "收款") -> LocalCategoryDecision("收入", "转账", 70, "AI:转账收入")
            else -> LocalCategoryDecision("收入", "其他", 50, "AI:收入默认")
        }
    }

    private fun specialCategory(text: String): LocalCategoryDecision? {
        return when {
            text.containsAny("医院", "门诊", "药店", "医保", "体检", "挂号", "诊所") ->
                LocalCategoryDecision("医疗", "门诊药品", 88, "AI:医疗关键词")
            text.containsAny("京东", "淘宝", "天猫", "拼多多", "jd", "taobao", "tmall", "pdd") ->
                LocalCategoryDecision("网购", "日常网购", 86, "AI:网购平台")
            text.containsAny("数码", "手机", "电脑", "家电", "ninebot", "电动") ->
                LocalCategoryDecision("网购", "数码家电", 74, "AI:数码家电")
            text.containsAny("红包", "礼金", "份子钱", "聚餐", "请客", "aa收款", "aa付款") ->
                LocalCategoryDecision("社交", "聚餐礼金", 82, "AI:社交关键词")
            else -> null
        }
    }

    private fun amountBucket(amountCents: Long): String {
        return when {
            amountCents <= 500L -> "0_5"
            amountCents <= 1_000L -> "5_10"
            amountCents <= 3_000L -> "10_30"
            amountCents <= 6_000L -> "30_60"
            amountCents <= 10_000L -> "60_100"
            amountCents <= 30_000L -> "100_300"
            else -> "300_PLUS"
        }
    }

    private fun String.looksLikePaymentApp(): Boolean {
        return uppercase() in setOf("ALIPAY", "WECHAT", "AUTO_NOTIFY")
    }

    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { contains(it, ignoreCase = true) }
    }

    private operator fun ClosedRange<LocalTime>.contains(time: LocalTime): Boolean {
        return !time.isBefore(start) && !time.isAfter(endInclusive)
    }

    private val COMMUTE_WINDOW = LocalTime.of(6, 30)..LocalTime.of(9, 45)
    private val LUNCH_WINDOW = LocalTime.of(10, 30)..LocalTime.of(14, 0)
    private val REST_OUTING_WINDOW = LocalTime.of(10, 0)..LocalTime.of(22, 30)
}
