package com.example.coin_nest.data

import com.example.coin_nest.data.db.CategoryBudgetEntity
import com.example.coin_nest.data.db.CategoryEntity
import com.example.coin_nest.data.db.CoinNestDbHelper
import com.example.coin_nest.data.db.MonthlyBudgetEntity
import com.example.coin_nest.data.db.STATUS_CONFIRMED
import com.example.coin_nest.data.db.STATUS_PENDING
import com.example.coin_nest.data.db.SmartCategoryRuleEntity
import com.example.coin_nest.data.db.TransactionEntity
import com.example.coin_nest.data.model.BalanceSummary

internal class CoinNestQueryStore(private val dbHelper: CoinNestDbHelper) {
    fun transactionById(id: Long): TransactionEntity? {
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

    fun pendingTransactions(limit: Int): List<TransactionEntity> {
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

    fun summary(startInclusive: Long, endExclusive: Long): BalanceSummary {
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

    fun transactionsInRange(
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

    fun confirmedTransactionCountInRange(startInclusive: Long, endExclusive: Long): Int {
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

    fun activeBookkeepingDayCount(): Int {
        val cursor = dbHelper.readableDatabase.rawQuery(
            """
            SELECT COUNT(DISTINCT date(occurred_at_epoch_ms / 1000, 'unixepoch', 'localtime')) AS cnt
            FROM transactions
            WHERE status = ?
            """.trimIndent(),
            arrayOf(STATUS_CONFIRMED)
        )
        return cursor.use { c ->
            if (c.moveToFirst()) c.getInt(c.getColumnIndexOrThrow("cnt")) else 0
        }
    }

    fun expenseByDayInRange(startInclusive: Long, endExclusive: Long): List<ExpenseBucketRow> {
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

    fun expenseByMonthInRange(startInclusive: Long, endExclusive: Long): List<ExpenseBucketRow> {
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

    fun categoryExpenseInRange(startInclusive: Long, endExclusive: Long): List<CategoryExpenseRow> {
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

    fun categories(): List<CategoryEntity> {
        val cursor = dbHelper.readableDatabase.rawQuery(
            """
            SELECT id, parent, child
            FROM categories
            WHERE NOT (parent = '收入' AND child IN ('转账', '其他'))
            ORDER BY
                CASE parent
                    WHEN '工作日' THEN 0
                    WHEN '休息日' THEN 1
                    WHEN '医疗' THEN 2
                    WHEN '网购' THEN 3
                    WHEN '社交' THEN 4
                    WHEN '收入' THEN 5
                    ELSE 20
                END ASC,
                parent ASC,
                child ASC
            """.trimIndent(),
            emptyArray()
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

    fun monthBudget(monthKey: String): MonthlyBudgetEntity? {
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

    fun categoryBudgets(monthKey: String): List<CategoryBudgetEntity> {
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

    fun smartCategoryRules(limit: Int? = null): List<SmartCategoryRuleEntity> {
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
                    add(c.toSmartCategoryRule())
                }
            }
        }
    }
}
