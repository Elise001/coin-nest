package com.example.coin_nest.data

import com.example.coin_nest.data.db.CoinNestDbHelper
import com.example.coin_nest.data.db.STATUS_CONFIRMED
import com.example.coin_nest.data.db.STATUS_IGNORED
import com.example.coin_nest.data.db.STATUS_LINKED_DUPLICATE
import com.example.coin_nest.data.db.STATUS_PENDING

internal data class LinkedAnchor(
    val id: Long,
    val reason: String,
    val confidence: Int
)

internal class AutoBookDuplicateStore(private val dbHelper: CoinNestDbHelper) {
    fun existsByFingerprint(fingerprint: String): Boolean {
        val cursor = dbHelper.readableDatabase.rawQuery(
            """
            SELECT id
            FROM transactions
            WHERE fingerprint = ?
              AND status != ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(fingerprint, STATUS_IGNORED)
        )
        return cursor.use { it.moveToFirst() }
    }

    fun findCrossSourceAnchor(
        amountCents: Long,
        type: String,
        source: String,
        channel: String,
        occurredAtEpochMs: Long
    ): LinkedAnchor? {
        val cursor = dbHelper.readableDatabase.rawQuery(
            """
            SELECT id, source, tag, occurred_at_epoch_ms
            FROM transactions
            WHERE amount_cents = ?
              AND type = ?
              AND status IN (?, ?)
              AND ABS(occurred_at_epoch_ms - ?) <= ?
            ORDER BY occurred_at_epoch_ms DESC
            LIMIT 30
            """.trimIndent(),
            arrayOf(
                amountCents.toString(),
                type,
                STATUS_PENDING,
                STATUS_CONFIRMED,
                occurredAtEpochMs.toString(),
                AUTO_CROSS_SOURCE_WINDOW_DUPLICATE_MS.toString()
            )
        )
        return cursor.use { c ->
            while (c.moveToNext()) {
                val candidateSource = c.getString(c.getColumnIndexOrThrow("source")).orEmpty().uppercase()
                val candidateTag = c.getString(c.getColumnIndexOrThrow("tag")).orEmpty()
                val candidateChannel = candidateTag.removePrefix("AUTO_CH_").uppercase()
                val candidateOccurredAt = c.getLong(c.getColumnIndexOrThrow("occurred_at_epoch_ms"))
                val decision = AutoBookMergeScorer.decide(
                    existingSource = candidateSource,
                    incomingSource = source,
                    existingChannel = candidateChannel,
                    incomingChannel = channel,
                    timeDiffMs = candidateOccurredAt - occurredAtEpochMs
                )
                if (decision.action == AutoBookMergeAction.LINK_RELATED) {
                    return@use LinkedAnchor(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        reason = decision.reason,
                        confidence = decision.confidence
                    )
                }
            }
            null
        }
    }

    fun existsRecentSameSourceWindowDuplicate(
        amountCents: Long,
        type: String,
        source: String,
        occurredAtEpochMs: Long,
        channel: String
    ): Boolean {
        val normalizedChannel = channel.uppercase()
        val cursor = dbHelper.readableDatabase.rawQuery(
            """
            SELECT tag, occurred_at_epoch_ms
            FROM transactions
            WHERE amount_cents = ?
              AND type = ?
              AND source = ?
              AND status IN (?, ?, ?)
              AND ABS(occurred_at_epoch_ms - ?) <= ?
              AND tag LIKE 'AUTO_CH_%'
            ORDER BY occurred_at_epoch_ms DESC
            LIMIT 8
            """.trimIndent(),
            arrayOf(
                amountCents.toString(),
                type,
                source,
                STATUS_PENDING,
                STATUS_CONFIRMED,
                STATUS_LINKED_DUPLICATE,
                occurredAtEpochMs.toString(),
                AUTO_CHANNEL_WINDOW_DUPLICATE_MS.toString()
            )
        )
        return cursor.use { c ->
            while (c.moveToNext()) {
                val tag = c.getString(c.getColumnIndexOrThrow("tag")).orEmpty()
                val existingChannel = tag.removePrefix("AUTO_CH_").uppercase()
                val existingOccurredAt = c.getLong(c.getColumnIndexOrThrow("occurred_at_epoch_ms"))
                val decision = AutoBookMergeScorer.decide(
                    existingSource = source,
                    incomingSource = source,
                    existingChannel = existingChannel,
                    incomingChannel = normalizedChannel,
                    timeDiffMs = existingOccurredAt - occurredAtEpochMs
                )
                if (decision.action == AutoBookMergeAction.DROP_DUPLICATE) {
                    return@use true
                }
            }
            false
        }
    }
}

