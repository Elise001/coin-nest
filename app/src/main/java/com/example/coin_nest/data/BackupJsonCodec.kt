package com.example.coin_nest.data

import com.example.coin_nest.data.db.CategoryBudgetEntity
import com.example.coin_nest.data.db.MonthlyBudgetEntity
import com.example.coin_nest.data.db.STATUS_CONFIRMED
import com.example.coin_nest.data.db.SmartCategoryRuleEntity
import com.example.coin_nest.data.db.TransactionEntity
import com.example.coin_nest.data.model.BackupPayload
import com.example.coin_nest.data.model.CategoryItem
import org.json.JSONArray
import org.json.JSONObject

internal object BackupJsonCodec {
    fun toJson(payload: BackupPayload): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exported_at", System.currentTimeMillis())
        val txArray = JSONArray()
        payload.transactions.forEach { tx ->
            val obj = JSONObject()
            obj.put("amount_cents", tx.amountCents)
            obj.put("type", tx.type)
            obj.put("parent_category", tx.parentCategory)
            obj.put("child_category", tx.childCategory)
            obj.put("source", tx.source)
            obj.put("note", tx.note)
            obj.put("occurred_at_epoch_ms", tx.occurredAtEpochMs)
            obj.put("created_at_epoch_ms", tx.createdAtEpochMs)
            obj.put("status", tx.status)
            obj.put("fingerprint", tx.fingerprint ?: JSONObject.NULL)
            txArray.put(obj)
        }
        root.put("transactions", txArray)

        val categoryArray = JSONArray()
        payload.categories.forEach { cat ->
            val obj = JSONObject()
            obj.put("parent", cat.parent)
            obj.put("child", cat.child)
            categoryArray.put(obj)
        }
        root.put("categories", categoryArray)

        val budgetArray = JSONArray()
        payload.budgets.forEach { budget ->
            val obj = JSONObject()
            obj.put("month_key", budget.monthKey)
            obj.put("limit_cents", budget.limitCents)
            budgetArray.put(obj)
        }
        root.put("budgets", budgetArray)

        val categoryBudgetArray = JSONArray()
        payload.categoryBudgets.forEach { budget ->
            val obj = JSONObject()
            obj.put("month_key", budget.monthKey)
            obj.put("parent_category", budget.parentCategory)
            obj.put("child_category", budget.childCategory)
            obj.put("limit_cents", budget.limitCents)
            categoryBudgetArray.put(obj)
        }
        root.put("category_budgets", categoryBudgetArray)

        val smartRuleArray = JSONArray()
        payload.smartCategoryRules.forEach { rule ->
            val obj = JSONObject()
            obj.put("type", rule.type)
            obj.put("source", rule.source)
            obj.put("keyword", rule.keyword)
            obj.put("parent_category", rule.parentCategory)
            obj.put("child_category", rule.childCategory)
            obj.put("hit_count", rule.hitCount)
            obj.put("updated_at_epoch_ms", rule.updatedAtEpochMs)
            smartRuleArray.put(obj)
        }
        root.put("smart_category_rules", smartRuleArray)

        return root.toString()
    }

    fun fromJson(json: String): BackupPayload {
        val root = JSONObject(json)
        val txList = mutableListOf<TransactionEntity>()
        val txArray = root.optJSONArray("transactions") ?: JSONArray()
        for (i in 0 until txArray.length()) {
            val obj = txArray.getJSONObject(i)
            txList += TransactionEntity(
                amountCents = obj.optLong("amount_cents", 0L),
                type = obj.optString("type", "EXPENSE"),
                parentCategory = obj.optString("parent_category", "待分类"),
                childCategory = obj.optString("child_category", "自动识别"),
                source = obj.optString("source", "IMPORTED"),
                note = obj.optString("note", ""),
                occurredAtEpochMs = obj.optLong("occurred_at_epoch_ms", System.currentTimeMillis()),
                createdAtEpochMs = obj.optLong("created_at_epoch_ms", System.currentTimeMillis()),
                status = obj.optString("status", STATUS_CONFIRMED),
                fingerprint = if (obj.has("fingerprint") && !obj.isNull("fingerprint")) obj.optString("fingerprint") else null
            )
        }

        val categories = mutableListOf<CategoryItem>()
        val categoryArray = root.optJSONArray("categories") ?: JSONArray()
        for (i in 0 until categoryArray.length()) {
            val obj = categoryArray.getJSONObject(i)
            categories += CategoryItem(
                parent = obj.optString("parent", ""),
                child = obj.optString("child", "")
            )
        }

        val budgets = mutableListOf<MonthlyBudgetEntity>()
        val budgetArray = root.optJSONArray("budgets") ?: JSONArray()
        for (i in 0 until budgetArray.length()) {
            val obj = budgetArray.getJSONObject(i)
            budgets += MonthlyBudgetEntity(
                monthKey = obj.optString("month_key", ""),
                limitCents = obj.optLong("limit_cents", 0L)
            )
        }
        val categoryBudgets = mutableListOf<CategoryBudgetEntity>()
        val categoryBudgetArray = root.optJSONArray("category_budgets") ?: JSONArray()
        for (i in 0 until categoryBudgetArray.length()) {
            val obj = categoryBudgetArray.getJSONObject(i)
            categoryBudgets += CategoryBudgetEntity(
                monthKey = obj.optString("month_key", ""),
                parentCategory = obj.optString("parent_category", ""),
                childCategory = obj.optString("child_category", ""),
                limitCents = obj.optLong("limit_cents", 0L)
            )
        }
        val smartRules = mutableListOf<SmartCategoryRuleEntity>()
        val smartRuleArray = root.optJSONArray("smart_category_rules") ?: JSONArray()
        for (i in 0 until smartRuleArray.length()) {
            val obj = smartRuleArray.getJSONObject(i)
            smartRules += SmartCategoryRuleEntity(
                type = obj.optString("type", "EXPENSE"),
                source = obj.optString("source", "AUTO_NOTIFY"),
                keyword = obj.optString("keyword", ""),
                parentCategory = obj.optString("parent_category", "待分类"),
                childCategory = obj.optString("child_category", "自动识别"),
                hitCount = obj.optInt("hit_count", 1),
                updatedAtEpochMs = obj.optLong("updated_at_epoch_ms", System.currentTimeMillis())
            )
        }
        return BackupPayload(
            transactions = txList,
            categories = categories,
            budgets = budgets,
            categoryBudgets = categoryBudgets,
            smartCategoryRules = smartRules
        )
    }
}
