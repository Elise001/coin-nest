package com.example.coin_nest.data.model

import com.example.coin_nest.data.db.MonthlyBudgetEntity
import com.example.coin_nest.data.db.TransactionEntity

data class BackupPayload(
    val transactions: List<TransactionEntity>,
    val categories: List<CategoryItem>,
    val budgets: List<MonthlyBudgetEntity>
)
