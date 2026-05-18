package com.example.coin_nest.data

import android.content.ContentValues
import android.content.Context
import com.example.coin_nest.data.db.CategoryBudgetEntity
import com.example.coin_nest.data.db.CategoryEntity
import com.example.coin_nest.data.db.CoinNestDbHelper
import com.example.coin_nest.data.db.MonthlyBudgetEntity
import com.example.coin_nest.data.db.STATUS_CONFIRMED
import com.example.coin_nest.data.db.STATUS_IGNORED
import com.example.coin_nest.data.db.STATUS_LINKED_DUPLICATE
import com.example.coin_nest.data.db.STATUS_PENDING
import com.example.coin_nest.data.db.SmartCategoryRuleEntity
import com.example.coin_nest.data.db.TransactionEntity
import com.example.coin_nest.data.model.BalanceSummary
import com.example.coin_nest.data.model.CategoryItem
import com.example.coin_nest.data.model.TransactionInput
import com.example.coin_nest.data.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class AutoTransactionInsertResult(
    val insertedId: Long?,
    val reason: String,
    val shouldNotify: Boolean
)

data class ExpenseBucketRow(
    val bucket: Int,
    val amountCents: Long
)

data class CategoryExpenseRow(
    val category: String,
    val amountCents: Long
)

private data class SmartCategorySuggestion(
    val parentCategory: String,
    val childCategory: String,
    val keyword: String,
    val hitCount: Int,
    val confidence: Int = 0
)

class CoinNestRepository(context: Context) {
    private val dbHelper = CoinNestDbHelper(context.applicationContext)
    private val changeTick = MutableStateFlow(0L)
    private val backupStore by lazy { CoinNestBackupStore(dbHelper) { notifyChanged() } }

    fun observeRecentTransactions(limit: Int = 50): Flow<List<TransactionEntity>> {
        return changeTick.map {
            withContext(Dispatchers.IO) {
                queryRecentTransactions(limit, includePending = false)
            }
        }
    }

    fun observeTransactionsInRange(
        startInclusive: Long,
        endExclusive: Long,
        limit: Int
    ): Flow<List<TransactionEntity>> {
        return changeTick.map {
            withContext(Dispatchers.IO) {
                queryTransactionsInRange(startInclusive, endExclusive, limit)
            }
        }
    }

    fun observePendingAutoTransactions(limit: Int = 50): Flow<List<TransactionEntity>> {
        return changeTick.map {
            withContext(Dispatchers.IO) {
                queryPendingTransactions(limit)
            }
        }
    }

    fun observeSummary(startInclusive: Long, endExclusive: Long): Flow<BalanceSummary> {
        return changeTick.map {
            withContext(Dispatchers.IO) {
                querySummary(startInclusive = startInclusive, endExclusive = endExclusive)
            }
        }
    }

    fun observeConfirmedTransactionCountInRange(
        startInclusive: Long,
        endExclusive: Long
    ): Flow<Int> {
        return changeTick.map {
            withContext(Dispatchers.IO) {
                queryConfirmedTransactionCountInRange(startInclusive, endExclusive)
            }
        }
    }

    fun observeExpenseByDayInRange(
        startInclusive: Long,
        endExclusive: Long
    ): Flow<List<ExpenseBucketRow>> {
        return changeTick.map {
            withContext(Dispatchers.IO) {
                queryExpenseByDayInRange(startInclusive, endExclusive)
            }
        }
    }

    fun observeExpenseByMonthInRange(
        startInclusive: Long,
        endExclusive: Long
    ): Flow<List<ExpenseBucketRow>> {
        return changeTick.map {
            withContext(Dispatchers.IO) {
                queryExpenseByMonthInRange(startInclusive, endExclusive)
            }
        }
    }

    fun observeCategoryExpenseInRange(
        startInclusive: Long,
        endExclusive: Long
    ): Flow<List<CategoryExpenseRow>> {
        return changeTick.map {
            withContext(Dispatchers.IO) {
                queryCategoryExpenseInRange(startInclusive, endExclusive)
            }
        }
    }

    fun observeCategories(): Flow<List<CategoryItem>> {
        return changeTick.map {
            withContext(Dispatchers.IO) {
                queryCategories().map { row -> CategoryItem(row.parent, row.child) }
            }
        }
    }

    fun observeSmartCategoryRules(limit: Int = 200): Flow<List<SmartCategoryRuleEntity>> {
        return changeTick.map {
            withContext(Dispatchers.IO) {
                queryAllSmartCategoryRules(limit = limit)
            }
        }
    }

    suspend fun ensureDefaultCategories() = withContext(Dispatchers.IO) {
        val defaults = listOf(
            "工作日" to "通勤",
            "工作日" to "餐费",
            "工作日" to "日常",
            "休息日" to "餐饮",
            "休息日" to "出行",
            "休息日" to "日常",
            "医疗" to "门诊药品",
            "医疗" to "体检护理",
            "网购" to "日常网购",
            "网购" to "数码家电",
            "网购" to "服饰美妆",
            "社交" to "聚餐礼金",
            "社交" to "红包转账",
            "社交" to "租房",
            "社交" to "水电燃气",
            "收入" to "工资",
            "收入" to "奖金",
            "收入" to "转账",
            "收入" to "退款",
            "收入" to "其他"
        )
        val db = dbHelper.writableDatabase
        var inserted = 0
        var deleted = 0
        db.beginTransaction()
        try {
            deleted += db.delete(
                "categories",
                "parent IN (?, ?, ?, ?, ?, ?, ?)",
                arrayOf("交通", "学习", "生活", "理财", "购物", "娱乐", "工作")
            )
            defaults.forEach { (parent, child) ->
                val values = ContentValues().apply {
                    put("parent", parent)
                    put("child", child)
                }
                val id = db.insertWithOnConflict("categories", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
                if (id != -1L) inserted++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (inserted > 0 || deleted > 0) notifyChanged()
    }

    suspend fun addCategory(parent: String, child: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("parent", parent)
            put("child", child)
        }
        dbHelper.writableDatabase.insertWithOnConflict(
            "categories",
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
        )
        notifyChanged()
    }

    suspend fun addTransaction(input: TransactionInput) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("amount_cents", input.amountCents)
            put("type", input.type.name)
            put("parent_category", input.parentCategory)
            put("child_category", input.childCategory)
            put("source", input.source)
            put("note", input.note)
            put("occurred_at_epoch_ms", input.occurredAtEpochMs)
            put("created_at_epoch_ms", System.currentTimeMillis())
            put("status", STATUS_CONFIRMED)
        }
        dbHelper.writableDatabase.insert("transactions", null, values)
        notifyChanged()
    }

    suspend fun confirmPendingTransaction(
        id: Long,
        parentCategory: String,
        childCategory: String
    ) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("status", STATUS_CONFIRMED)
            put("parent_category", parentCategory)
            put("child_category", childCategory)
        }
        dbHelper.writableDatabase.update("transactions", values, "id = ?", arrayOf(id.toString()))
        notifyChanged()
    }

    suspend fun confirmPendingTransaction(id: Long) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put("status", STATUS_CONFIRMED) }
        dbHelper.writableDatabase.update("transactions", values, "id = ?", arrayOf(id.toString()))
        notifyChanged()
    }

    suspend fun ignorePendingTransaction(id: Long) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put("status", STATUS_IGNORED) }
        dbHelper.writableDatabase.update("transactions", values, "id = ?", arrayOf(id.toString()))
        notifyChanged()
    }

    suspend fun updateTransactionDetails(
        id: Long,
        parentCategory: String,
        childCategory: String,
        note: String
    ) = withContext(Dispatchers.IO) {
        if (parentCategory.isBlank() || childCategory.isBlank()) return@withContext
        val txBeforeUpdate = queryTransactionById(id)
        val trimmedParent = parentCategory.trim()
        val trimmedChild = childCategory.trim()
        val values = ContentValues().apply {
            put("parent_category", trimmedParent)
            put("child_category", trimmedChild)
            put("note", note.trim())
        }
        dbHelper.writableDatabase.update("transactions", values, "id = ?", arrayOf(id.toString()))
        txBeforeUpdate?.let { tx ->
            if (tx.parentCategory != trimmedParent || tx.childCategory != trimmedChild) {
                learnSmartCategoryRuleFromUserCorrection(
                    tx = tx,
                    targetParent = trimmedParent,
                    targetChild = trimmedChild
                )
            }
        }
        notifyChanged()
    }

    suspend fun deleteTransaction(id: Long) = withContext(Dispatchers.IO) {
        dbHelper.writableDatabase.delete("transactions", "id = ?", arrayOf(id.toString()))
        notifyChanged()
    }

    suspend fun exportBackupJson(): String = backupStore.exportBackupJson()

    suspend fun importBackupJson(json: String, replaceExisting: Boolean = false): Pair<Int, Int> =
        backupStore.importBackupJson(json, replaceExisting)

    suspend fun upsertMonthBudget(monthKey: String, limitCents: Long) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("month_key", monthKey)
            put("limit_cents", limitCents)
        }
        dbHelper.writableDatabase.insertWithOnConflict(
            "monthly_budget",
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
        notifyChanged()
    }

    fun observeMonthBudget(monthKey: String): Flow<MonthlyBudgetEntity?> {
        return changeTick.map {
            withContext(Dispatchers.IO) {
                queryMonthBudget(monthKey)
            }
        }
    }

    fun observeCategoryBudgets(monthKey: String): Flow<List<CategoryBudgetEntity>> {
        return changeTick.map {
            withContext(Dispatchers.IO) {
                queryCategoryBudgets(monthKey)
            }
        }
    }

    suspend fun upsertCategoryBudget(
        monthKey: String,
        parentCategory: String,
        childCategory: String,
        limitCents: Long
    ) = withContext(Dispatchers.IO) {
        if (parentCategory.isBlank() || childCategory.isBlank() || limitCents <= 0L) return@withContext
        val values = ContentValues().apply {
            put("month_key", monthKey)
            put("parent_category", parentCategory.trim())
            put("child_category", childCategory.trim())
            put("limit_cents", limitCents)
        }
        dbHelper.writableDatabase.insertWithOnConflict(
            "category_budget",
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
        notifyChanged()
    }

    suspend fun clearSmartCategoryRules() = withContext(Dispatchers.IO) {
        dbHelper.writableDatabase.delete("smart_category_rule", null, null)
        notifyChanged()
    }

    suspend fun currentMonthBudgetUsage(
        monthKey: String,
        startInclusive: Long,
        endExclusive: Long
    ): Pair<Long, Long?> = withContext(Dispatchers.IO) {
        val expense = querySummary(startInclusive, endExclusive).expenseCents
        val budget = queryMonthBudget(monthKey)?.limitCents
        expense to budget
    }

    suspend fun addAutoTransaction(
        amountCents: Long,
        type: TransactionType,
        source: String,
        note: String,
        fingerprint: String?,
        occurredAtEpochMs: Long,
        channel: String = "NOTIFY",
        parent: String = "\u5f85\u5206\u7c7b",
        child: String = "\u81ea\u52a8\u8bc6\u522b"
    ): AutoTransactionInsertResult = withContext(Dispatchers.IO) {
        val safeParent = if (parent.isBlank()) "\u5f85\u5206\u7c7b" else parent.trim()
        val safeChild = if (child.isBlank()) "\u81ea\u52a8\u8bc6\u522b" else child.trim()
        val autoType = type.name
        val occurredAt = occurredAtEpochMs.takeIf { it > 0L } ?: System.currentTimeMillis()
        val receivedAt = System.currentTimeMillis()
        val db = dbHelper.writableDatabase

        // Same-source dedupe: only when transaction reference exists (fingerprint != null).
        if (!fingerprint.isNullOrBlank() && existsByFingerprint(fingerprint)) {
            return@withContext AutoTransactionInsertResult(
                insertedId = null,
                reason = "SAME_SOURCE_DUPLICATE_BY_TXN_REF",
                shouldNotify = false
            )
        }

        if (existsRecentSameSourceWindowDuplicate(
                amountCents = amountCents,
                type = autoType,
                source = source,
                occurredAtEpochMs = occurredAt,
                channel = channel
            )
        ) {
            return@withContext AutoTransactionInsertResult(
                insertedId = null,
                reason = "AUTO_DUPLICATE_BY_WINDOW",
                shouldNotify = false
            )
        }

        // Cross-source dedupe: payment apps and bank/card notices often describe the same payment.
        val linkedAnchor = findCrossSourceAnchor(
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
        val smartSuggestion = suggestSmartCategory(
            type = autoType,
            source = source,
            note = finalNote,
            amountCents = amountCents,
            occurredAtEpochMs = occurredAt
        )
        val shouldApplySmartCategory = smartSuggestion != null && (
            safeParent == "\u5f85\u5206\u7c7b" ||
                safeChild == "\u81ea\u52a8\u8bc6\u522b" ||
                isReplaceableAutoCategory(type = autoType, parent = safeParent) ||
                smartSuggestion.hitCount >= 2 ||
                smartSuggestion.confidence >= 70
            )
        val finalParentCategory = if (shouldApplySmartCategory) smartSuggestion!!.parentCategory else safeParent
        val finalChildCategory = if (shouldApplySmartCategory) smartSuggestion!!.childCategory else safeChild
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
            android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
        )
        if (insertedId != -1L) {
            notifyChanged()
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

    private fun existsByFingerprint(fingerprint: String): Boolean {
        val cursor = dbHelper.readableDatabase.rawQuery(
            """
            SELECT id
            FROM transactions
            WHERE fingerprint = ?
              AND status != ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(fingerprint, STATUS_IGNORED)
        )
        return cursor.use { it.moveToFirst() }
    }

    private data class LinkedAnchor(
        val id: Long,
        val reason: String,
        val confidence: Int
    )

    private fun findCrossSourceAnchor(
        amountCents: Long,
        type: String,
        source: String,
        channel: String,
        occurredAtEpochMs: Long
    ): LinkedAnchor? {
        val windowMs = AUTO_CROSS_SOURCE_WINDOW_DUPLICATE_MS
        val cursor = dbHelper.readableDatabase.rawQuery(
            """
            SELECT id, source, tag, occurred_at_epoch_ms
            FROM transactions
            WHERE amount_cents = ?
              AND type = ?
              AND status IN (?, ?)
              AND ABS(occurred_at_epoch_ms - ?) <= ?
            ORDER BY occurred_at_epoch_ms DESC
            LIMIT 30
            """.trimIndent(),
            arrayOf(
                amountCents.toString(),
                type,
                STATUS_PENDING,
                STATUS_CONFIRMED,
                occurredAtEpochMs.toString(),
                windowMs.toString()
            )
        )
        return cursor.use { c ->
            while (c.moveToNext()) {
                val candidateSource = c.getString(c.getColumnIndexOrThrow("source")).orEmpty().uppercase()
                val candidateTag = c.getString(c.getColumnIndexOrThrow("tag")).orEmpty()
                val candidateChannel = candidateTag.removePrefix("AUTO_CH_").uppercase()
                val candidateOccurredAt = c.getLong(c.getColumnIndexOrThrow("occurred_at_epoch_ms"))
                val decision = AutoBookMergeScorer.decide(
                    existingSource = candidateSource,
                    incomingSource = source,
                    existingChannel = candidateChannel,
                    incomingChannel = channel,
                    timeDiffMs = candidateOccurredAt - occurredAtEpochMs
                )
                if (decision.action == AutoBookMergeAction.LINK_RELATED) {
                    return@use LinkedAnchor(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        reason = decision.reason,
                        confidence = decision.confidence
                    )
                }
            }
            null
        }
    }

    private fun existsRecentSameSourceWindowDuplicate(
        amountCents: Long,
        type: String,
        source: String,
        occurredAtEpochMs: Long,
        channel: String
    ): Boolean {
        val normalizedChannel = channel.uppercase()
        val cursor = dbHelper.readableDatabase.rawQuery(
            """
            SELECT tag, occurred_at_epoch_ms
            FROM transactions
            WHERE amount_cents = ?
              AND type = ?
              AND source = ?
              AND status IN (?, ?, ?)
              AND ABS(occurred_at_epoch_ms - ?) <= ?
              AND tag LIKE 'AUTO_CH_%'
            ORDER BY occurred_at_epoch_ms DESC
            LIMIT 8
            """.trimIndent(),
            arrayOf(
                amountCents.toString(),
                type,
                source,
                STATUS_PENDING,
                STATUS_CONFIRMED,
                STATUS_LINKED_DUPLICATE,
                occurredAtEpochMs.toString(),
                AUTO_CHANNEL_WINDOW_DUPLICATE_MS.toString()
            )
        )
        return cursor.use { c ->
            while (c.moveToNext()) {
                val tag = c.getString(c.getColumnIndexOrThrow("tag")).orEmpty()
                val existingChannel = tag.removePrefix("AUTO_CH_").uppercase()
                val existingOccurredAt = c.getLong(c.getColumnIndexOrThrow("occurred_at_epoch_ms"))
                val decision = AutoBookMergeScorer.decide(
                    existingSource = source,
                    incomingSource = source,
                    existingChannel = existingChannel,
                    incomingChannel = normalizedChannel,
                    timeDiffMs = existingOccurredAt - occurredAtEpochMs
                )
                if (decision.action == AutoBookMergeAction.DROP_DUPLICATE) {
                    return@use true
                }
            }
            false
        }
    }

    suspend fun addAutoExpense(
        amountCents: Long,
        source: String,
        note: String,
        fingerprint: String?,
        parent: String = "\u5f85\u5206\u7c7b",
        child: String = "\u81ea\u52a8\u8bc6\u522b"
    ): AutoTransactionInsertResult {
        return addAutoTransaction(
            amountCents = amountCents,
            type = TransactionType.EXPENSE,
            source = source,
            note = note,
            fingerprint = fingerprint,
            occurredAtEpochMs = System.currentTimeMillis(),
            parent = parent,
            child = child
        )
    }

    private fun isLearnableAutoSource(source: String): Boolean {
        return source.uppercase() in setOf(
            "ALIPAY", "WECHAT", "BANK_CARD", "CREDIT_CARD", "UNIONPAY", "AUTO_NOTIFY"
        )
    }

    private fun queryTransactionById(id: Long): TransactionEntity? {
        val cursor = dbHelper.readableDatabase.query(
            "transactions",
            null,
            "id = ?",
            arrayOf(id.toString()),
            null,
            null,
            null,
            "1"
        )
        return cursor.use { c ->
            if (c.moveToFirst()) {
                TransactionEntity(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    amountCents = c.getLong(c.getColumnIndexOrThrow("amount_cents")),
                    type = c.getString(c.getColumnIndexOrThrow("type")),
                    parentCategory = c.getString(c.getColumnIndexOrThrow("parent_category")),
                    childCategory = c.getString(c.getColumnIndexOrThrow("child_category")),
                    source = c.getString(c.getColumnIndexOrThrow("source")),
                    note = c.getString(c.getColumnIndexOrThrow("note")),
                    occurredAtEpochMs = c.getLong(c.getColumnIndexOrThrow("occurred_at_epoch_ms")),
                    createdAtEpochMs = c.getLong(c.getColumnIndexOrThrow("created_at_epoch_ms")),
                    status = c.getString(c.getColumnIndexOrThrow("status")),
                    fingerprint = c.getString(c.getColumnIndexOrThrow("fingerprint"))
                )
            } else {
                null
            }
        }
    }

    private fun isReplaceableAutoCategory(type: String, parent: String): Boolean {
        if (type != TransactionType.EXPENSE.name) return false
        return parent !in setOf("医疗", "网购", "社交")
    }

    private fun learnSmartCategoryRuleFromUserCorrection(
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

    private fun suggestSmartCategory(
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
            .filter { rule -> normalizedNote.contains(rule.keyword) || behaviorKeywords.contains(rule.keyword) }
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
                while (c.moveToNext()) {
                    add(
                        SmartCategoryRuleEntity(
                            id = c.getLong(c.getColumnIndexOrThrow("id")),
                            type = c.getString(c.getColumnIndexOrThrow("type")),
                            source = c.getString(c.getColumnIndexOrThrow("source")),
                            keyword = c.getString(c.getColumnIndexOrThrow("keyword")),
                            parentCategory = c.getString(c.getColumnIndexOrThrow("parent_category")),
                            childCategory = c.getString(c.getColumnIndexOrThrow("child_category")),
                            hitCount = c.getInt(c.getColumnIndexOrThrow("hit_count")),
                            updatedAtEpochMs = c.getLong(c.getColumnIndexOrThrow("updated_at_epoch_ms"))
                        )
                    )
                }
            }
        }
    }

    private fun queryAllSmartCategoryRules(limit: Int? = null): List<SmartCategoryRuleEntity> {
        val cursor = dbHelper.readableDatabase.query(
            "smart_category_rule",
            arrayOf("id", "type", "source", "keyword", "parent_category", "child_category", "hit_count", "updated_at_epoch_ms"),
            null,
            null,
            null,
            null,
            "updated_at_epoch_ms DESC",
            if (limit != null && limit > 0) limit.toString() else null
        )
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        SmartCategoryRuleEntity(
                            id = c.getLong(c.getColumnIndexOrThrow("id")),
                            type = c.getString(c.getColumnIndexOrThrow("type")),
                            source = c.getString(c.getColumnIndexOrThrow("source")),
                            keyword = c.getString(c.getColumnIndexOrThrow("keyword")),
                            parentCategory = c.getString(c.getColumnIndexOrThrow("parent_category")),
                            childCategory = c.getString(c.getColumnIndexOrThrow("child_category")),
                            hitCount = c.getInt(c.getColumnIndexOrThrow("hit_count")),
                            updatedAtEpochMs = c.getLong(c.getColumnIndexOrThrow("updated_at_epoch_ms"))
                        )
                    )
                }
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
        val short = normalized.split(" ").firstOrNull { it.length in 2..10 } ?: return listOf(source.lowercase())
        return listOf(short)
    }

    private fun normalizeNote(note: String): String {
        return note.lowercase()
            .replace(Regex("\\[smart:[^\\]]+\\]"), " ")
            .replace(Regex("[^\\p{L}\\p{N}\\u4e00-\\u9fa5]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun appendSmartHitTag(
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

    private fun notifyChanged() {
        changeTick.value = System.currentTimeMillis()
    }

    private fun queryRecentTransactions(limit: Int, includePending: Boolean): List<TransactionEntity> {
        val where = if (includePending) "status != ?" else "status = ?"
        val args = if (includePending) arrayOf(STATUS_IGNORED) else arrayOf(STATUS_CONFIRMED)
        val cursor = dbHelper.readableDatabase.query(
            "transactions",
            null,
            where,
            args,
            null,
            null,
            "occurred_at_epoch_ms DESC",
            limit.toString()
        )
        return cursor.use { c -> buildTransactions(c) }
    }

    private fun queryPendingTransactions(limit: Int): List<TransactionEntity> {
        val cursor = dbHelper.readableDatabase.query(
            "transactions",
            null,
            "status = ?",
            arrayOf(STATUS_PENDING),
            null,
            null,
            "occurred_at_epoch_ms DESC",
            limit.toString()
        )
        return cursor.use { c -> buildTransactions(c) }
    }

    private fun querySummary(startInclusive: Long, endExclusive: Long): BalanceSummary {
        val sql = """
            SELECT 
                COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount_cents ELSE 0 END), 0) AS income_sum,
                COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount_cents ELSE 0 END), 0) AS expense_sum
            FROM transactions
            WHERE occurred_at_epoch_ms >= ? AND occurred_at_epoch_ms < ? AND status = ?
        """.trimIndent()
        val cursor = dbHelper.readableDatabase.rawQuery(
            sql,
            arrayOf(startInclusive.toString(), endExclusive.toString(), STATUS_CONFIRMED)
        )
        return cursor.use { c ->
            if (c.moveToFirst()) {
                BalanceSummary(
                    incomeCents = c.getLong(c.getColumnIndexOrThrow("income_sum")),
                    expenseCents = c.getLong(c.getColumnIndexOrThrow("expense_sum"))
                )
            } else {
                BalanceSummary()
            }
        }
    }

    private fun queryTransactionsInRange(
        startInclusive: Long,
        endExclusive: Long,
        limit: Int
    ): List<TransactionEntity> {
        val cursor = dbHelper.readableDatabase.query(
            "transactions",
            null,
            "occurred_at_epoch_ms >= ? AND occurred_at_epoch_ms < ? AND status = ?",
            arrayOf(startInclusive.toString(), endExclusive.toString(), STATUS_CONFIRMED),
            null,
            null,
            "occurred_at_epoch_ms DESC",
            limit.toString()
        )
        return cursor.use { c -> buildTransactions(c) }
    }

    private fun queryConfirmedTransactionCountInRange(
        startInclusive: Long,
        endExclusive: Long
    ): Int {
        val cursor = dbHelper.readableDatabase.rawQuery(
            """
            SELECT COUNT(1) AS cnt
            FROM transactions
            WHERE occurred_at_epoch_ms >= ?
              AND occurred_at_epoch_ms < ?
              AND status = ?
            """.trimIndent(),
            arrayOf(startInclusive.toString(), endExclusive.toString(), STATUS_CONFIRMED)
        )
        return cursor.use { c ->
            if (c.moveToFirst()) c.getInt(c.getColumnIndexOrThrow("cnt")) else 0
        }
    }

    private fun queryExpenseByDayInRange(
        startInclusive: Long,
        endExclusive: Long
    ): List<ExpenseBucketRow> {
        val cursor = dbHelper.readableDatabase.rawQuery(
            """
            SELECT CAST(strftime('%d', occurred_at_epoch_ms / 1000, 'unixepoch', 'localtime') AS INTEGER) AS day_bucket,
                   SUM(amount_cents) AS expense_sum
            FROM transactions
            WHERE occurred_at_epoch_ms >= ?
              AND occurred_at_epoch_ms < ?
              AND status = ?
              AND type = 'EXPENSE'
            GROUP BY day_bucket
            ORDER BY day_bucket ASC
            """.trimIndent(),
            arrayOf(startInclusive.toString(), endExclusive.toString(), STATUS_CONFIRMED)
        )
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        ExpenseBucketRow(
                            bucket = c.getInt(c.getColumnIndexOrThrow("day_bucket")),
                            amountCents = c.getLong(c.getColumnIndexOrThrow("expense_sum"))
                        )
                    )
                }
            }
        }
    }

    private fun queryExpenseByMonthInRange(
        startInclusive: Long,
        endExclusive: Long
    ): List<ExpenseBucketRow> {
        val cursor = dbHelper.readableDatabase.rawQuery(
            """
            SELECT CAST(strftime('%m', occurred_at_epoch_ms / 1000, 'unixepoch', 'localtime') AS INTEGER) AS month_bucket,
                   SUM(amount_cents) AS expense_sum
            FROM transactions
            WHERE occurred_at_epoch_ms >= ?
              AND occurred_at_epoch_ms < ?
              AND status = ?
              AND type = 'EXPENSE'
            GROUP BY month_bucket
            ORDER BY month_bucket ASC
            """.trimIndent(),
            arrayOf(startInclusive.toString(), endExclusive.toString(), STATUS_CONFIRMED)
        )
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        ExpenseBucketRow(
                            bucket = c.getInt(c.getColumnIndexOrThrow("month_bucket")),
                            amountCents = c.getLong(c.getColumnIndexOrThrow("expense_sum"))
                        )
                    )
                }
            }
        }
    }

    private fun queryCategoryExpenseInRange(
        startInclusive: Long,
        endExclusive: Long
    ): List<CategoryExpenseRow> {
        val cursor = dbHelper.readableDatabase.rawQuery(
            """
            SELECT parent_category,
                   SUM(amount_cents) AS expense_sum
            FROM transactions
            WHERE occurred_at_epoch_ms >= ?
              AND occurred_at_epoch_ms < ?
              AND status = ?
              AND type = 'EXPENSE'
            GROUP BY parent_category
            ORDER BY expense_sum DESC
            """.trimIndent(),
            arrayOf(startInclusive.toString(), endExclusive.toString(), STATUS_CONFIRMED)
        )
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        CategoryExpenseRow(
                            category = c.getString(c.getColumnIndexOrThrow("parent_category")),
                            amountCents = c.getLong(c.getColumnIndexOrThrow("expense_sum"))
                        )
                    )
                }
            }
        }
    }

    private fun queryCategories(): List<CategoryEntity> {
        val cursor = dbHelper.readableDatabase.query(
            "categories",
            arrayOf("id", "parent", "child"),
            null,
            null,
            null,
            null,
            "parent ASC, child ASC"
        )
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        CategoryEntity(
                            id = c.getLong(c.getColumnIndexOrThrow("id")),
                            parent = c.getString(c.getColumnIndexOrThrow("parent")),
                            child = c.getString(c.getColumnIndexOrThrow("child"))
                        )
                    )
                }
            }
        }
    }

    private fun queryMonthBudget(monthKey: String): MonthlyBudgetEntity? {
        val cursor = dbHelper.readableDatabase.query(
            "monthly_budget",
            arrayOf("month_key", "limit_cents"),
            "month_key = ?",
            arrayOf(monthKey),
            null,
            null,
            null,
            "1"
        )
        return cursor.use { c ->
            if (c.moveToFirst()) {
                MonthlyBudgetEntity(
                    monthKey = c.getString(c.getColumnIndexOrThrow("month_key")),
                    limitCents = c.getLong(c.getColumnIndexOrThrow("limit_cents"))
                )
            } else {
                null
            }
        }
    }

    private fun queryCategoryBudgets(monthKey: String): List<CategoryBudgetEntity> {
        val cursor = dbHelper.readableDatabase.query(
            "category_budget",
            arrayOf("month_key", "parent_category", "child_category", "limit_cents"),
            "month_key = ?",
            arrayOf(monthKey),
            null,
            null,
            "parent_category ASC, child_category ASC"
        )
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        CategoryBudgetEntity(
                            monthKey = c.getString(c.getColumnIndexOrThrow("month_key")),
                            parentCategory = c.getString(c.getColumnIndexOrThrow("parent_category")),
                            childCategory = c.getString(c.getColumnIndexOrThrow("child_category")),
                            limitCents = c.getLong(c.getColumnIndexOrThrow("limit_cents"))
                        )
                    )
                }
            }
        }
    }

}
