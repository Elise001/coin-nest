package com.example.coin_nest.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import com.example.coin_nest.data.db.CoinNestDbHelper

internal class DefaultCategoryStore(
    private val dbHelper: CoinNestDbHelper,
    private val onChanged: () -> Unit
) {
    fun ensureDefaultCategories() {
        val defaults = listOf(
            "工作日" to "通勤",
            "工作日" to "餐费",
            "工作日" to "日常",
            "休息日" to "餐饮",
            "休息日" to "出行",
            "休息日" to "日常",
            "医疗" to "门诊药品",
            "医疗" to "体检护理",
            "网购" to "日常网购",
            "网购" to "数码家电",
            "网购" to "服饰美妆",
            "社交" to "聚餐礼金",
            "社交" to "红包转账",
            "社交" to "租房",
            "社交" to "水电燃气",
            "收入" to "工资",
            "收入" to "奖金",
            "收入" to "退款"
        )
        val db = dbHelper.writableDatabase
        var inserted = 0
        var deleted = 0
        db.transaction {
            deleted += db.delete(
                "categories",
                "parent IN (?, ?, ?, ?, ?, ?, ?)",
                arrayOf("交通", "学习", "生活", "理财", "购物", "娱乐", "工作")
            )
            deleted += db.delete(
                "categories",
                "parent = ? AND child IN (?, ?)",
                arrayOf("收入", "转账", "其他")
            )
            defaults.forEach { (parent, child) ->
                val values = ContentValues().apply {
                    put("parent", parent)
                    put("child", child)
                }
                val id = db.insertWithOnConflict("categories", null, values, SQLiteDatabase.CONFLICT_IGNORE)
                if (id != -1L) inserted++
            }
        }
        if (inserted > 0 || deleted > 0) onChanged()
    }
}
