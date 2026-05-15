package com.example.coin_nest.data

import android.content.ContentValues
import com.example.coin_nest.data.db.CategoryBudgetEntity
import com.example.coin_nest.data.db.CoinNestDbHelper
import com.example.coin_nest.data.db.MonthlyBudgetEntity
import com.example.coin_nest.data.db.SmartCategoryRuleEntity
import com.example.coin_nest.data.db.TransactionEntity
import com.example.coin_nest.data.model.BackupPayload
import com.example.coin_nest.data.model.CategoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class CoinNestBackupStore(
    private val dbHelper: CoinNestDbHelper,
    private val notifyChanged: () -> Unit
) {
    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val payload = BackupPayload(
            transactions = queryAllTransactions(),
            categories = queryBackupCategories(),
            budgets = queryAllBudgets(),
            categoryBudgets = queryAllCategoryBudgets(),
            smartCategoryRules = queryAllSmartCategoryRules()
        )
        BackupJsonCodec.toJson(payload)
    }

    suspend fun importBackupJson(json: String, replaceExisting: Boolean = false): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val parsed = BackupJsonCodec.fromJson(json)
        val db = dbHelper.writableDatabase
        var txCount = 0
        var catCount = 0
        db.beginTransaction()
        try {
            if (replaceExisting) {
                db.delete("transactions", null, null)
                db.delete("categories", null, null)
                db.delete("monthly_budget", null, null)
                db.delete("category_budget", null, null)
                db.delete("smart_category_rule", null, null)
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
            parsed.categoryBudgets.forEach { budget ->
                val values = ContentValues().apply {
                    put("month_key", budget.monthKey)
                    put("parent_category", budget.parentCategory)
                    put("child_category", budget.childCategory)
                    put("limit_cents", budget.limitCents)
                }
                db.insertWithOnConflict("category_budget", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
            }
            parsed.smartCategoryRules.forEach { rule ->
                val values = ContentValues().apply {
                    put("type", rule.type)
                    put("source", rule.source)
                    put("keyword", rule.keyword)
                    put("parent_category", rule.parentCategory)
                    put("child_category", rule.childCategory)
                    put("hit_count", rule.hitCount)
                    put("updated_at_epoch_ms", rule.updatedAtEpochMs)
                }
                db.insertWithOnConflict("smart_category_rule", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
        txCount to catCount
    }

    private fun queryBackupCategories(): List<CategoryItem> {
        val cursor = dbHelper.readableDatabase.query(
            "categories",
            arrayOf("parent", "child"),
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
                        CategoryItem(
                            parent = c.getString(c.getColumnIndexOrThrow("parent")),
                            child = c.getString(c.getColumnIndexOrThrow("child"))
                        )
                    )
                }
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

    private fun queryAllCategoryBudgets(): List<CategoryBudgetEntity> {
        val cursor = dbHelper.readableDatabase.query(
            "category_budget",
            arrayOf("month_key", "parent_category", "child_category", "limit_cents"),
            null,
            null,
            null,
            null,
            "month_key ASC, parent_category ASC, child_category ASC"
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

    private fun queryAllSmartCategoryRules(): List<SmartCategoryRuleEntity> {
        val cursor = dbHelper.readableDatabase.query(
            "smart_category_rule",
            null,
            null,
            null,
            null,
            null,
            "hit_count DESC, updated_at_epoch_ms DESC"
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
}
