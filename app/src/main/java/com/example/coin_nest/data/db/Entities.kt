package com.example.coin_nest.data.db

data class TransactionEntity(
    val id: Long = 0L,
    val amountCents: Long,
    val type: String,
    val parentCategory: String,
    val childCategory: String,
    val source: String,
    val note: String,
    val occurredAtEpochMs: Long,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class CategoryEntity(
    val id: Long = 0L,
    val parent: String,
    val child: String
)

data class MonthlyBudgetEntity(
    val monthKey: String,
    val limitCents: Long
)
