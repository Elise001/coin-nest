package com.example.coin_nest.data

import android.database.Cursor
import com.example.coin_nest.data.db.SmartCategoryRuleEntity

internal fun Cursor.toSmartCategoryRule(): SmartCategoryRuleEntity {
    return SmartCategoryRuleEntity(
        id = getLong(getColumnIndexOrThrow("id")),
        type = getString(getColumnIndexOrThrow("type")),
        source = getString(getColumnIndexOrThrow("source")),
        keyword = getString(getColumnIndexOrThrow("keyword")),
        parentCategory = getString(getColumnIndexOrThrow("parent_category")),
        childCategory = getString(getColumnIndexOrThrow("child_category")),
        hitCount = getInt(getColumnIndexOrThrow("hit_count")),
        updatedAtEpochMs = getLong(getColumnIndexOrThrow("updated_at_epoch_ms"))
    )
}

