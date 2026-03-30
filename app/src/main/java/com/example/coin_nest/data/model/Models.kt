package com.example.coin_nest.data.model

enum class TransactionType {
    INCOME,
    EXPENSE
}

data class BalanceSummary(
    val incomeCents: Long = 0L,
    val expenseCents: Long = 0L
) {
    val balanceCents: Long
        get() = incomeCents - expenseCents
}

data class CategoryItem(
    val parent: String,
    val child: String
)

data class TransactionInput(
    val amountCents: Long,
    val type: TransactionType,
    val parentCategory: String,
    val childCategory: String,
    val source: String = "MANUAL",
    val note: String = "",
    val occurredAtEpochMs: Long
)

