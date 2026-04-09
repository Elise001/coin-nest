package com.example.coin_nest.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class CoinNestDbHelper(context: Context) : SQLiteOpenHelper(
    context,
    DB_NAME,
    null,
    DB_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                amount_cents INTEGER NOT NULL,
                type TEXT NOT NULL,
                parent_category TEXT NOT NULL,
                child_category TEXT NOT NULL,
                source TEXT NOT NULL,
                note TEXT NOT NULL,
                tag TEXT NOT NULL DEFAULT '',
                occurred_at_epoch_ms INTEGER NOT NULL,
                created_at_epoch_ms INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'CONFIRMED',
                fingerprint TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_transactions_occurred ON transactions(occurred_at_epoch_ms DESC)")
        db.execSQL("CREATE INDEX idx_transactions_status ON transactions(status)")
        db.execSQL("CREATE UNIQUE INDEX idx_transactions_fingerprint ON transactions(fingerprint)")

        db.execSQL(
            """
            CREATE TABLE categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                parent TEXT NOT NULL,
                child TEXT NOT NULL,
                UNIQUE(parent, child)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE monthly_budget (
                month_key TEXT PRIMARY KEY,
                limit_cents INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE category_budget (
                month_key TEXT NOT NULL,
                parent_category TEXT NOT NULL,
                child_category TEXT NOT NULL,
                limit_cents INTEGER NOT NULL,
                PRIMARY KEY (month_key, parent_category, child_category)
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN status TEXT NOT NULL DEFAULT 'CONFIRMED'")
            db.execSQL("ALTER TABLE transactions ADD COLUMN fingerprint TEXT")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_status ON transactions(status)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_transactions_fingerprint ON transactions(fingerprint)")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN tag TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 4) {
            // v4 keeps schema unchanged and is used to preserve forward-only DB versioning.
        }
        if (oldVersion < 5) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS category_budget (
                    month_key TEXT NOT NULL,
                    parent_category TEXT NOT NULL,
                    child_category TEXT NOT NULL,
                    limit_cents INTEGER NOT NULL,
                    PRIMARY KEY (month_key, parent_category, child_category)
                )
                """.trimIndent()
            )
        }
    }

    companion object {
        private const val DB_NAME = "coin_nest_sqlite.db"
        private const val DB_VERSION = 5
    }
}
