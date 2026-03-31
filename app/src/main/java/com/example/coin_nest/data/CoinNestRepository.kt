package com.example.coin_nest.data

import android.content.ContentValues
import android.content.Context
import com.example.coin_nest.data.db.CategoryEntity
import com.example.coin_nest.data.db.CoinNestDbHelper
import com.example.coin_nest.data.db.MonthlyBudgetEntity
import com.example.coin_nest.data.db.STATUS_CONFIRMED
import com.example.coin_nest.data.db.STATUS_IGNORED
import com.example.coin_nest.data.db.STATUS_PENDING
import com.example.coin_nest.data.db.TransactionEntity
import com.example.coin_nest.data.model.BalanceSummary
import com.example.coin_nest.data.model.BackupPayload
import com.example.coin_nest.data.model.CategoryItem
import com.example.coin_nest.data.model.TransactionInput
import com.example.coin_nest.data.model.TransactionType
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CoinNestRepository(context: Context) {
    private val dbHelper = CoinNestDbHelper(context.applicationContext)
    private val changeTick = MutableStateFlow(0L)

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

    fun observeCategories(): Flow<List<CategoryItem>> {
        return changeTick.map {
            withContext(Dispatchers.IO) {
                queryCategories().map { row -> CategoryItem(row.parent, row.child) }
            }
        }
    }

    suspend fun ensureDefaultCategories() = withContext(Dispatchers.IO) {
        if (queryCategories().isNotEmpty()) return@withContext
        val defaults = listOf(
            "\u5de5\u4f5c" to "\u901a\u52e4",
            "\u5de5\u4f5c" to "\u5b66\u4e60",
            "\u751f\u6d3b" to "\u9910\u996e",
            "\u751f\u6d3b" to "\u65e5\u7528\u54c1",
            "\u751f\u6d3b" to "\u5a31\u4e50",
            "\u6536\u5165" to "\u5de5\u8d44",
            "\u6536\u5165" to "\u5176\u4ed6"
        )
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            defaults.forEach { (parent, child) ->
                val values = ContentValues().apply {
                    put("parent", parent)
                    put("child", child)
                }
                db.insertWithOnConflict("categories", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
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

    suspend fun updateTransactionCategory(
        id: Long,
        parentCategory: String,
        childCategory: String
    ) = withContext(Dispatchers.IO) {
        if (parentCategory.isBlank() || childCategory.isBlank()) return@withContext
        val values = ContentValues().apply {
            put("parent_category", parentCategory.trim())
            put("child_category", childCategory.trim())
        }
        dbHelper.writableDatabase.update("transactions", values, "id = ?", arrayOf(id.toString()))
        notifyChanged()
    }

    suspend fun deleteTransaction(id: Long) = withContext(Dispatchers.IO) {
        dbHelper.writableDatabase.delete("transactions", "id = ?", arrayOf(id.toString()))
        notifyChanged()
    }

    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val payload = BackupPayload(
            transactions = queryAllTransactions(),
            categories = queryCategories().map { CategoryItem(it.parent, it.child) },
            budgets = queryAllBudgets()
        )
        toJson(payload)
    }

    suspend fun importBackupJson(json: String, replaceExisting: Boolean = false): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val parsed = fromJson(json)
        val db = dbHelper.writableDatabase
        var txCount = 0
        var catCount = 0
        db.beginTransaction()
        try {
            if (replaceExisting) {
                db.delete("transactions", null, null)
                db.delete("categories", null, null)
                db.delete("monthly_budget", null, null)
            }

            parsed.categories.forEach { cat ->
                val values = ContentValues().apply {
                    put("parent", cat.parent)
                    put("child", cat.child)
                }
                val id = db.insertWithOnConflict("categories", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
                if (id != -1L) catCount++
            }

            parsed.transactions.forEach { tx ->
                val values = ContentValues().apply {
                    put("amount_cents", tx.amountCents)
                    put("type", tx.type)
                    put("parent_category", tx.parentCategory)
                    put("child_category", tx.childCategory)
                    put("source", tx.source)
                    put("note", tx.note)
                    put("occurred_at_epoch_ms", tx.occurredAtEpochMs)
                    put("created_at_epoch_ms", tx.createdAtEpochMs)
                    put("status", tx.status)
                    put("fingerprint", tx.fingerprint)
                }
                val id = db.insertWithOnConflict("transactions", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
                if (id != -1L) txCount++
            }

            parsed.budgets.forEach { budget ->
                val values = ContentValues().apply {
                    put("month_key", budget.monthKey)
                    put("limit_cents", budget.limitCents)
                }
                db.insertWithOnConflict("monthly_budget", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
        txCount to catCount
    }

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

    suspend fun currentMonthBudgetUsage(
        monthKey: String,
        startInclusive: Long,
        endExclusive: Long
    ): Pair<Long, Long?> = withContext(Dispatchers.IO) {
        val expense = querySummary(startInclusive, endExclusive).expenseCents
        val budget = queryMonthBudget(monthKey)?.limitCents
        expense to budget
    }

    suspend fun addAutoExpense(
        amountCents: Long,
        source: String,
        note: String,
        fingerprint: String,
        parent: String = "\u5f85\u5206\u7c7b",
        child: String = "\u81ea\u52a8\u8bc6\u522b"
    ): Long? = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("amount_cents", amountCents)
            put("type", TransactionType.EXPENSE.name)
            put("parent_category", parent)
            put("child_category", child)
            put("source", source)
            put("note", note)
            put("occurred_at_epoch_ms", System.currentTimeMillis())
            put("created_at_epoch_ms", System.currentTimeMillis())
            put("status", STATUS_PENDING)
            put("fingerprint", fingerprint)
        }
        val insertedId = dbHelper.writableDatabase.insertWithOnConflict(
            "transactions",
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
        )
        if (insertedId != -1L) {
            notifyChanged()
            insertedId
        } else {
            null
        }
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

    private fun queryAllBudgets(): List<MonthlyBudgetEntity> {
        val cursor = dbHelper.readableDatabase.query(
            "monthly_budget",
            arrayOf("month_key", "limit_cents"),
            null,
            null,
            null,
            null,
            "month_key ASC"
        )
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        MonthlyBudgetEntity(
                            monthKey = c.getString(c.getColumnIndexOrThrow("month_key")),
                            limitCents = c.getLong(c.getColumnIndexOrThrow("limit_cents"))
                        )
                    )
                }
            }
        }
    }

    private fun queryAllTransactions(): List<TransactionEntity> {
        val cursor = dbHelper.readableDatabase.query(
            "transactions",
            null,
            null,
            null,
            null,
            null,
            "occurred_at_epoch_ms DESC"
        )
        return cursor.use { c -> buildTransactions(c) }
    }

    private fun toJson(payload: BackupPayload): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exported_at", System.currentTimeMillis())
        val txArray = JSONArray()
        payload.transactions.forEach { tx ->
            val obj = JSONObject()
            obj.put("amount_cents", tx.amountCents)
            obj.put("type", tx.type)
            obj.put("parent_category", tx.parentCategory)
            obj.put("child_category", tx.childCategory)
            obj.put("source", tx.source)
            obj.put("note", tx.note)
            obj.put("occurred_at_epoch_ms", tx.occurredAtEpochMs)
            obj.put("created_at_epoch_ms", tx.createdAtEpochMs)
            obj.put("status", tx.status)
            obj.put("fingerprint", tx.fingerprint ?: JSONObject.NULL)
            txArray.put(obj)
        }
        root.put("transactions", txArray)

        val categoryArray = JSONArray()
        payload.categories.forEach { cat ->
            val obj = JSONObject()
            obj.put("parent", cat.parent)
            obj.put("child", cat.child)
            categoryArray.put(obj)
        }
        root.put("categories", categoryArray)

        val budgetArray = JSONArray()
        payload.budgets.forEach { budget ->
            val obj = JSONObject()
            obj.put("month_key", budget.monthKey)
            obj.put("limit_cents", budget.limitCents)
            budgetArray.put(obj)
        }
        root.put("budgets", budgetArray)
        return root.toString()
    }

    private fun fromJson(json: String): BackupPayload {
        val root = JSONObject(json)
        val txList = mutableListOf<TransactionEntity>()
        val txArray = root.optJSONArray("transactions") ?: JSONArray()
        for (i in 0 until txArray.length()) {
            val obj = txArray.getJSONObject(i)
            txList += TransactionEntity(
                amountCents = obj.optLong("amount_cents", 0L),
                type = obj.optString("type", "EXPENSE"),
                parentCategory = obj.optString("parent_category", "待分类"),
                childCategory = obj.optString("child_category", "自动识别"),
                source = obj.optString("source", "IMPORTED"),
                note = obj.optString("note", ""),
                occurredAtEpochMs = obj.optLong("occurred_at_epoch_ms", System.currentTimeMillis()),
                createdAtEpochMs = obj.optLong("created_at_epoch_ms", System.currentTimeMillis()),
                status = obj.optString("status", STATUS_CONFIRMED),
                fingerprint = if (obj.has("fingerprint") && !obj.isNull("fingerprint")) obj.optString("fingerprint") else null
            )
        }

        val categories = mutableListOf<CategoryItem>()
        val categoryArray = root.optJSONArray("categories") ?: JSONArray()
        for (i in 0 until categoryArray.length()) {
            val obj = categoryArray.getJSONObject(i)
            categories += CategoryItem(
                parent = obj.optString("parent", ""),
                child = obj.optString("child", "")
            )
        }

        val budgets = mutableListOf<MonthlyBudgetEntity>()
        val budgetArray = root.optJSONArray("budgets") ?: JSONArray()
        for (i in 0 until budgetArray.length()) {
            val obj = budgetArray.getJSONObject(i)
            budgets += MonthlyBudgetEntity(
                monthKey = obj.optString("month_key", ""),
                limitCents = obj.optLong("limit_cents", 0L)
            )
        }
        return BackupPayload(transactions = txList, categories = categories, budgets = budgets)
    }

    private fun buildTransactions(cursor: android.database.Cursor): List<TransactionEntity> {
        return buildList {
            while (cursor.moveToNext()) {
                add(
                    TransactionEntity(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        amountCents = cursor.getLong(cursor.getColumnIndexOrThrow("amount_cents")),
                        type = cursor.getString(cursor.getColumnIndexOrThrow("type")),
                        parentCategory = cursor.getString(cursor.getColumnIndexOrThrow("parent_category")),
                        childCategory = cursor.getString(cursor.getColumnIndexOrThrow("child_category")),
                        source = cursor.getString(cursor.getColumnIndexOrThrow("source")),
                        note = cursor.getString(cursor.getColumnIndexOrThrow("note")),
                        occurredAtEpochMs = cursor.getLong(cursor.getColumnIndexOrThrow("occurred_at_epoch_ms")),
                        createdAtEpochMs = cursor.getLong(cursor.getColumnIndexOrThrow("created_at_epoch_ms")),
                        status = cursor.getString(cursor.getColumnIndexOrThrow("status")),
                        fingerprint = cursor.getString(cursor.getColumnIndexOrThrow("fingerprint"))
                    )
                )
            }
        }
    }
}
