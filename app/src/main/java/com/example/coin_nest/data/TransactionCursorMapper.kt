package com.example.coin_nest.data

import android.database.Cursor
import com.example.coin_nest.data.db.TransactionEntity

internal fun buildTransactions(cursor: Cursor): List<TransactionEntity> {
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
