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
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val status: String = STATUS_CONFIRMED,
    val fingerprint: String? = null
)

const val STATUS_CONFIRMED = "CONFIRMED"
const val STATUS_PENDING = "PENDING"
const val STATUS_IGNORED = "IGNORED"
const val STATUS_LINKED_DUPLICATE = "LINKED_DUPLICATE"

data class CategoryEntity(
    val id: Long = 0L,
    val parent: String,
    val child: String
)

data class MonthlyBudgetEntity(
    val monthKey: String,
    val limitCents: Long
)

data class CategoryBudgetEntity(
    val monthKey: String,
    val parentCategory: String,
    val childCategory: String,
    val limitCents: Long
)

data class SmartCategoryRuleEntity(
    val id: Long = 0L,
    val type: String,
    val source: String,
    val keyword: String,
    val parentCategory: String,
    val childCategory: String,
    val hitCount: Int,
    val updatedAtEpochMs: Long
)
