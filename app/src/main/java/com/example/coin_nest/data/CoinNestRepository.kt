package com.example.coin_nest.data

import android.content.Context
import android.content.SharedPreferences
import com.example.coin_nest.data.db.CategoryEntity
import com.example.coin_nest.data.db.MonthlyBudgetEntity
import com.example.coin_nest.data.db.TransactionEntity
import com.example.coin_nest.data.model.BalanceSummary
import com.example.coin_nest.data.model.CategoryItem
import com.example.coin_nest.data.model.TransactionInput
import com.example.coin_nest.data.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class CoinNestRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val transactionsState = MutableStateFlow(loadTransactions())
    private val categoriesState = MutableStateFlow(loadCategories())
    private val budgetsState = MutableStateFlow(loadBudgets())

    fun observeRecentTransactions(limit: Int = 50): Flow<List<TransactionEntity>> {
        return transactionsState.map { it.sortedByDescending { tx -> tx.occurredAtEpochMs }.take(limit) }
    }

    fun observeSummary(startInclusive: Long, endExclusive: Long): Flow<BalanceSummary> {
        return transactionsState.map { txs ->
            val filtered = txs.filter { it.occurredAtEpochMs in startInclusive until endExclusive }
            val income = filtered.filter { it.type == TransactionType.INCOME.name }.sumOf { it.amountCents }
            val expense = filtered.filter { it.type == TransactionType.EXPENSE.name }.sumOf { it.amountCents }
            BalanceSummary(incomeCents = income, expenseCents = expense)
        }
    }

    fun observeCategories(): Flow<List<CategoryItem>> {
        return categoriesState.map { list -> list.map { CategoryItem(parent = it.parent, child = it.child) } }
    }

    suspend fun ensureDefaultCategories() {
        if (categoriesState.value.isNotEmpty()) return
        val defaults = listOf(
            CategoryEntity(id = 1, parent = "工作", child = "通勤"),
            CategoryEntity(id = 2, parent = "工作", child = "学习"),
            CategoryEntity(id = 3, parent = "生活", child = "餐饮"),
            CategoryEntity(id = 4, parent = "生活", child = "日用品"),
            CategoryEntity(id = 5, parent = "生活", child = "娱乐"),
            CategoryEntity(id = 6, parent = "收入", child = "工资"),
            CategoryEntity(id = 7, parent = "收入", child = "其他")
        )
        categoriesState.value = defaults
        saveCategories(defaults)
    }

    suspend fun addCategory(parent: String, child: String) {
        val exists = categoriesState.value.any { it.parent == parent && it.child == child }
        if (exists) return
        val nextId = (categoriesState.value.maxOfOrNull { it.id } ?: 0L) + 1
        val next = categoriesState.value + CategoryEntity(id = nextId, parent = parent, child = child)
        categoriesState.value = next
        saveCategories(next)
    }

    suspend fun addTransaction(input: TransactionInput) {
        val nextId = (transactionsState.value.maxOfOrNull { it.id } ?: 0L) + 1
        val tx = TransactionEntity(
            id = nextId,
            amountCents = input.amountCents,
            type = input.type.name,
            parentCategory = input.parentCategory,
            childCategory = input.childCategory,
            source = input.source,
            note = input.note,
            occurredAtEpochMs = input.occurredAtEpochMs
        )
        val next = transactionsState.value + tx
        transactionsState.value = next
        saveTransactions(next)
    }

    suspend fun upsertMonthBudget(monthKey: String, limitCents: Long) {
        val next = budgetsState.value.toMutableMap().apply { put(monthKey, limitCents) }
        budgetsState.value = next
        saveBudgets(next)
    }

    fun observeMonthBudget(monthKey: String): Flow<MonthlyBudgetEntity?> {
        return budgetsState.map { map ->
            map[monthKey]?.let { MonthlyBudgetEntity(monthKey = monthKey, limitCents = it) }
        }
    }

    suspend fun currentMonthBudgetUsage(
        monthKey: String,
        startInclusive: Long,
        endExclusive: Long
    ): Pair<Long, Long?> {
        val expense = transactionsState.value
            .filter { it.type == TransactionType.EXPENSE.name }
            .filter { it.occurredAtEpochMs in startInclusive until endExclusive }
            .sumOf { it.amountCents }
        return expense to budgetsState.value[monthKey]
    }

    suspend fun addAutoExpense(
        amountCents: Long,
        source: String,
        note: String,
        parent: String = "待分类",
        child: String = "自动识别"
    ) {
        addTransaction(
            TransactionInput(
                amountCents = amountCents,
                type = TransactionType.EXPENSE,
                parentCategory = parent,
                childCategory = child,
                source = source,
                note = note,
                occurredAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    private fun saveTransactions(items: List<TransactionEntity>) {
        val arr = JSONArray()
        items.forEach { tx ->
            arr.put(
                JSONObject()
                    .put("id", tx.id)
                    .put("amountCents", tx.amountCents)
                    .put("type", tx.type)
                    .put("parentCategory", tx.parentCategory)
                    .put("childCategory", tx.childCategory)
                    .put("source", tx.source)
                    .put("note", tx.note)
                    .put("occurredAtEpochMs", tx.occurredAtEpochMs)
                    .put("createdAtEpochMs", tx.createdAtEpochMs)
            )
        }
        prefs.edit().putString(KEY_TXS, arr.toString()).apply()
    }

    private fun loadTransactions(): List<TransactionEntity> {
        val raw = prefs.getString(KEY_TXS, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    TransactionEntity(
                        id = obj.optLong("id"),
                        amountCents = obj.optLong("amountCents"),
                        type = obj.optString("type"),
                        parentCategory = obj.optString("parentCategory"),
                        childCategory = obj.optString("childCategory"),
                        source = obj.optString("source"),
                        note = obj.optString("note"),
                        occurredAtEpochMs = obj.optLong("occurredAtEpochMs"),
                        createdAtEpochMs = obj.optLong("createdAtEpochMs")
                    )
                )
            }
        }
    }

    private fun saveCategories(items: List<CategoryEntity>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().put("id", it.id).put("parent", it.parent).put("child", it.child))
        }
        prefs.edit().putString(KEY_CATEGORIES, arr.toString()).apply()
    }

    private fun loadCategories(): List<CategoryEntity> {
        val raw = prefs.getString(KEY_CATEGORIES, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    CategoryEntity(
                        id = obj.optLong("id"),
                        parent = obj.optString("parent"),
                        child = obj.optString("child")
                    )
                )
            }
        }
    }

    private fun saveBudgets(items: Map<String, Long>) {
        val obj = JSONObject()
        items.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY_BUDGETS, obj.toString()).apply()
    }

    private fun loadBudgets(): Map<String, Long> {
        val raw = prefs.getString(KEY_BUDGETS, null) ?: return emptyMap()
        val obj = JSONObject(raw)
        val keys = obj.keys()
        val map = mutableMapOf<String, Long>()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = obj.optLong(key)
        }
        return map
    }

    companion object {
        private const val PREF_NAME = "coin_nest_prefs"
        private const val KEY_TXS = "transactions_json"
        private const val KEY_CATEGORIES = "categories_json"
        private const val KEY_BUDGETS = "budgets_json"
    }
}

