package com.example.coin_nest.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.example.coin_nest.data.db.CoinNestDbHelper
import com.example.coin_nest.data.db.STATUS_LINKED_DUPLICATE
import com.example.coin_nest.data.db.STATUS_PENDING
import com.example.coin_nest.data.model.TransactionType

internal class AutoTransactionStore(
    private val dbHelper: CoinNestDbHelper,
    private val smartCategoryRules: SmartCategoryRuleStore,
    private val autoBookDuplicates: AutoBookDuplicateStore,
    private val onChanged: () -> Unit
) {
    fun addAutoTransaction(
        amountCents: Long,
        type: TransactionType,
        source: String,
        note: String,
        fingerprint: String?,
        occurredAtEpochMs: Long,
        channel: String = "NOTIFY",
        parent: String = "待分类",
        child: String = "自动识别"
    ): AutoTransactionInsertResult {
        val safeParent = if (parent.isBlank()) "待分类" else parent.trim()
        val safeChild = if (child.isBlank()) "自动识别" else child.trim()
        val autoType = type.name
        val occurredAt = occurredAtEpochMs.takeIf { it > 0L } ?: System.currentTimeMillis()
        val receivedAt = System.currentTimeMillis()
        val db = dbHelper.writableDatabase

        if (!fingerprint.isNullOrBlank() && autoBookDuplicates.existsByFingerprint(fingerprint)) {
            return AutoTransactionInsertResult(
                insertedId = null,
                reason = "SAME_SOURCE_DUPLICATE_BY_TXN_REF",
                shouldNotify = false
            )
        }

        if (autoBookDuplicates.existsRecentSameSourceWindowDuplicate(
                amountCents = amountCents,
                type = autoType,
                source = source,
                occurredAtEpochMs = occurredAt,
                channel = channel
            )
        ) {
            return AutoTransactionInsertResult(
                insertedId = null,
                reason = "AUTO_DUPLICATE_BY_WINDOW",
                shouldNotify = false
            )
        }

        val linkedAnchor = autoBookDuplicates.findCrossSourceAnchor(
            amountCents = amountCents,
            type = autoType,
            source = source,
            channel = channel,
            occurredAtEpochMs = occurredAt
        )
        val finalStatus = if (linkedAnchor != null) STATUS_LINKED_DUPLICATE else STATUS_PENDING
        val finalNote = if (linkedAnchor != null) {
            "$note [AI关联->#${linkedAnchor.id}:${linkedAnchor.reason}:${linkedAnchor.confidence}]"
        } else {
            note
        }
        val smartSuggestion = smartCategoryRules.suggest(
            type = autoType,
            source = source,
            note = finalNote,
            amountCents = amountCents,
            occurredAtEpochMs = occurredAt
        )
        val shouldApplySmartCategory = smartSuggestion != null && (
            safeParent == "待分类" ||
                safeChild == "自动识别" ||
                isReplaceableAutoCategory(type = autoType, parent = safeParent) ||
                smartSuggestion.hitCount >= 2 ||
                smartSuggestion.confidence >= 70
            )
        val finalParentCategory = if (shouldApplySmartCategory) smartSuggestion.parentCategory else safeParent
        val finalChildCategory = if (shouldApplySmartCategory) smartSuggestion.childCategory else safeChild
        val noteWithSmartTag = appendSmartHitTag(
            originalNote = finalNote,
            suggestion = smartSuggestion,
            applied = shouldApplySmartCategory
        )
        val values = ContentValues().apply {
            put("amount_cents", amountCents)
            put("type", autoType)
            put("parent_category", finalParentCategory)
            put("child_category", finalChildCategory)
            put("source", source)
            put("note", noteWithSmartTag)
            put("occurred_at_epoch_ms", occurredAt)
            put("created_at_epoch_ms", receivedAt)
            put("status", finalStatus)
            put("fingerprint", fingerprint)
            put("tag", "AUTO_CH_${channel.uppercase()}")
        }
        val insertedId = db.insertWithOnConflict(
            "transactions",
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        )
        return if (insertedId != -1L) {
            onChanged()
            AutoTransactionInsertResult(
                insertedId = insertedId,
                reason = if (linkedAnchor != null) "AI_RELATED_LINKED" else "INSERTED",
                shouldNotify = linkedAnchor == null
            )
        } else {
            AutoTransactionInsertResult(
                insertedId = null,
                reason = "DUPLICATE_OR_CONFLICT",
                shouldNotify = false
            )
        }
    }

    private fun isReplaceableAutoCategory(type: String, parent: String): Boolean {
        if (type != TransactionType.EXPENSE.name) return false
        return parent !in setOf("医疗", "网购", "社交")
    }
}
