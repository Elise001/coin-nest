package com.example.coin_nest.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.example.coin_nest.data.db.CoinNestDbHelper

internal class BudgetStore(
    private val dbHelper: CoinNestDbHelper,
    private val queries: CoinNestQueryStore,
    private val onChanged: () -> Unit
) {
    fun upsertMonthBudget(monthKey: String, limitCents: Long) {
        val values = ContentValues().apply {
            put("month_key", monthKey)
            put("limit_cents", limitCents)
        }
        dbHelper.writableDatabase.insertWithOnConflict(
            "monthly_budget",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
        onChanged()
    }

    fun upsertCategoryBudget(
        monthKey: String,
        parentCategory: String,
        childCategory: String,
        limitCents: Long
    ) {
        if (parentCategory.isBlank() || childCategory.isBlank() || limitCents <= 0L) return
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
            SQLiteDatabase.CONFLICT_REPLACE
        )
        onChanged()
    }

    fun currentMonthBudgetUsage(
        monthKey: String,
        startInclusive: Long,
        endExclusive: Long
    ): Pair<Long, Long?> {
        val expense = queries.summary(startInclusive, endExclusive).expenseCents
        val budget = queries.monthBudget(monthKey)?.limitCents
        return expense to budget
    }
}
