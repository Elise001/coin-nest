package com.example.coin_nest.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.example.coin_nest.data.db.CoinNestDbHelper
import com.example.coin_nest.data.db.STATUS_CONFIRMED
import com.example.coin_nest.data.db.STATUS_IGNORED
import com.example.coin_nest.data.model.TransactionInput

internal class TransactionWriteStore(
    private val dbHelper: CoinNestDbHelper,
    private val queries: CoinNestQueryStore,
    private val smartCategoryRules: SmartCategoryRuleStore,
    private val onChanged: () -> Unit
) {
    fun addCategory(parent: String, child: String) {
        val values = ContentValues().apply {
            put("parent", parent)
            put("child", child)
        }
        dbHelper.writableDatabase.insertWithOnConflict(
            "categories",
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        )
        onChanged()
    }

    fun addTransaction(input: TransactionInput) {
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
        onChanged()
    }

    fun confirmPendingTransaction(id: Long) {
        val values = ContentValues().apply { put("status", STATUS_CONFIRMED) }
        dbHelper.writableDatabase.update("transactions", values, "id = ?", arrayOf(id.toString()))
        onChanged()
    }

    fun ignorePendingTransaction(id: Long) {
        val values = ContentValues().apply { put("status", STATUS_IGNORED) }
        dbHelper.writableDatabase.update("transactions", values, "id = ?", arrayOf(id.toString()))
        onChanged()
    }

    fun updateTransactionDetails(
        id: Long,
        parentCategory: String,
        childCategory: String,
        note: String
    ) {
        if (parentCategory.isBlank() || childCategory.isBlank()) return
        val txBeforeUpdate = queries.transactionById(id)
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
                smartCategoryRules.learnFromUserCorrection(
                    tx = tx,
                    targetParent = trimmedParent,
                    targetChild = trimmedChild
                )
            }
        }
        onChanged()
    }

    fun deleteTransaction(id: Long) {
        dbHelper.writableDatabase.delete("transactions", "id = ?", arrayOf(id.toString()))
        onChanged()
    }
}
