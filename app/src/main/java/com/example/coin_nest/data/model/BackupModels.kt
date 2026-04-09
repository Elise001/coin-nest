package com.example.coin_nest.data.model

import com.example.coin_nest.data.db.CategoryBudgetEntity
import com.example.coin_nest.data.db.MonthlyBudgetEntity
import com.example.coin_nest.data.db.SmartCategoryRuleEntity
import com.example.coin_nest.data.db.TransactionEntity

data class BackupPayload(
    val transactions: List<TransactionEntity>,
    val categories: List<CategoryItem>,
    val budgets: List<MonthlyBudgetEntity>,
    val categoryBudgets: List<CategoryBudgetEntity> = emptyList(),
    val smartCategoryRules: List<SmartCategoryRuleEntity> = emptyList()
)
