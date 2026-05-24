package com.example.coin_nest.data

import android.content.ContentValues
import com.example.coin_nest.data.db.CoinNestDbHelper
import com.example.coin_nest.data.db.SmartCategoryRuleEntity
import com.example.coin_nest.data.db.TransactionEntity
import com.example.coin_nest.data.model.TransactionType

internal data class SmartCategorySuggestion(
    val parentCategory: String,
    val childCategory: String,
    val keyword: String,
    val hitCount: Int,
    val confidence: Int = 0
)

internal class SmartCategoryRuleStore(private val dbHelper: CoinNestDbHelper) {
    fun clear() {
        dbHelper.writableDatabase.delete("smart_category_rule", null, null)
    }

    fun learnFromUserCorrection(
        tx: TransactionEntity,
        targetParent: String,
        targetChild: String
    ) {
        if (!isLearnableAutoSource(tx.source)) return
        if (targetParent.isBlank() || targetChild.isBlank()) return
        val keywords = extractSmartKeywords(
            note = tx.note,
            source = tx.source,
            amountCents = tx.amountCents,
            occurredAtEpochMs = tx.occurredAtEpochMs
        )
        if (keywords.isEmpty()) return
        val db = dbHelper.writableDatabase
        val now = System.currentTimeMillis()
        val sourceKey = tx.source.uppercase()
        keywords.forEach { keyword ->
            val existingHitCount = querySmartRuleHitCount(
                type = tx.type,
                source = sourceKey,
                keyword = keyword
            )
            val values = ContentValues().apply {
                put("type", tx.type)
                put("source", sourceKey)
                put("keyword", keyword)
                put("parent_category", targetParent)
                put("child_category", targetChild)
                put("hit_count", existingHitCount + 1)
                put("updated_at_epoch_ms", now)
            }
            db.insertWithOnConflict(
                "smart_category_rule",
                null,
                values,
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }

    fun suggest(
        type: String,
        source: String,
        note: String,
        amountCents: Long,
        occurredAtEpochMs: Long
    ): SmartCategorySuggestion? {
        val sourceKey = source.uppercase()
        val rules = querySmartCategoryRules(type = type, source = sourceKey)
        val normalizedNote = normalizeNote(note)
        val behaviorKeywords = extractSmartKeywords(
            note = note,
            source = source,
            amountCents = amountCents,
            occurredAtEpochMs = occurredAtEpochMs
        ).toSet()
        val learnedSuggestion = rules
            .filter { rule ->
                !rule.keyword.isGenericAutoLearningToken() &&
                    (normalizedNote.contains(rule.keyword) || behaviorKeywords.contains(rule.keyword))
            }
            .maxWithOrNull(
                compareByDescending<SmartCategoryRuleEntity> { it.hitCount }
                    .thenByDescending { it.keyword.length }
                    .thenByDescending { it.updatedAtEpochMs }
            )?.let {
                SmartCategorySuggestion(
                    parentCategory = it.parentCategory,
                    childCategory = it.childCategory,
                    keyword = it.keyword,
                    hitCount = it.hitCount,
                    confidence = (70 + it.hitCount * 8).coerceAtMost(95)
                )
            }
        if (learnedSuggestion != null) return learnedSuggestion

        val txType = runCatching { TransactionType.valueOf(type) }.getOrNull() ?: return null
        return LocalCategoryAi.decide(
            type = txType,
            source = source,
            note = note,
            amountCents = amountCents,
            occurredAtEpochMs = occurredAtEpochMs
        )?.let { decision ->
            SmartCategorySuggestion(
                parentCategory = decision.parentCategory,
                childCategory = decision.childCategory,
                keyword = decision.reason,
                hitCount = 0,
                confidence = decision.confidence
            )
        }
    }

    private fun querySmartRuleHitCount(type: String, source: String, keyword: String): Int {
        val cursor = dbHelper.readableDatabase.rawQuery(
            """
            SELECT hit_count
            FROM smart_category_rule
            WHERE type = ? AND source = ? AND keyword = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(type, source, keyword)
        )
        return cursor.use { c ->
            if (c.moveToFirst()) c.getInt(c.getColumnIndexOrThrow("hit_count")) else 0
        }
    }

    private fun querySmartCategoryRules(type: String, source: String): List<SmartCategoryRuleEntity> {
        val cursor = dbHelper.readableDatabase.query(
            "smart_category_rule",
            arrayOf("id", "type", "source", "keyword", "parent_category", "child_category", "hit_count", "updated_at_epoch_ms"),
            "type = ? AND source = ?",
            arrayOf(type, source),
            null,
            null,
            "hit_count DESC, updated_at_epoch_ms DESC",
            "200"
        )
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) add(c.toSmartCategoryRule())
            }
        }
    }

    private fun extractSmartKeywords(
        note: String,
        source: String,
        amountCents: Long,
        occurredAtEpochMs: Long
    ): List<String> {
        return (
            extractSmartKeywords(note, source) +
                LocalCategoryAi.learningTokens(source, amountCents, occurredAtEpochMs)
            ).distinct()
    }

    private fun extractSmartKeywords(note: String, source: String): List<String> {
        val normalized = normalizeNote(note)
        if (normalized.isBlank()) return emptyList()
        val seedKeywords = listOf(
            "美团", "外卖", "饿了么", "淘宝", "天猫", "京东", "拼多多",
            "滴滴", "地铁", "公交", "打车", "酒店", "机票", "火车票",
            "星巴克", "瑞幸", "麦当劳", "肯德基", "便利店", "超市",
            "医院", "药店", "话费", "充值", "电费", "水费"
        )
        val matched = seedKeywords.filter { normalized.contains(it.lowercase()) }
        if (matched.isNotEmpty()) return matched.take(3).map { it.lowercase() }
        val short = normalized.split(" ").firstOrNull { token ->
            token.length in 2..10 && !token.isGenericAutoLearningToken()
        } ?: return listOf(source.lowercase()).filterNot { it.isGenericAutoLearningToken() }
        return listOf(short)
    }

    private fun isLearnableAutoSource(source: String): Boolean {
        return source.uppercase() in setOf(
            "ALIPAY", "WECHAT", "BANK_CARD", "CREDIT_CARD", "UNIONPAY", "AUTO_NOTIFY"
        )
    }
}

internal fun appendSmartHitTag(
    originalNote: String,
    suggestion: SmartCategorySuggestion?,
    applied: Boolean
): String {
    if (!applied || suggestion == null) return originalNote
    val safeKeyword = suggestion.keyword.replace("]", "")
    val tag = "[SMART:$safeKeyword#${suggestion.hitCount}]"
    val base = originalNote.trim()
    if (base.isBlank()) return tag
    if (base.contains("[SMART:")) return base
    return "$base $tag"
}

private fun String.isGenericAutoLearningToken(): Boolean {
    val token = lowercase()
    return token in setOf(
        "android",
        "widget",
        "layout",
        "framelayout",
        "linearlayout",
        "relativelayout",
        "textview",
        "button",
        "view",
        "alipay",
        "wechat",
        "auto_notify"
    )
}

private fun normalizeNote(note: String): String {
    return note.lowercase()
        .replace(Regex("""\[smart:[^]]+]"""), " ")
        .replace(Regex("""[^\p{L}\p{N}\u4e00-\u9fa5]+"""), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
