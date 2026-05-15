package com.example.coin_nest.data

internal const val AUTO_CHANNEL_WINDOW_DUPLICATE_MS = 60_000L
internal const val AUTO_CROSS_SOURCE_WINDOW_DUPLICATE_MS = 90_000L
internal const val AUTO_SAME_SOURCE_WINDOW_DUPLICATE_MS = AUTO_CHANNEL_WINDOW_DUPLICATE_MS

internal fun isWithinAutoSameSourceWindow(existingOccurredAt: Long, incomingOccurredAt: Long): Boolean {
    return kotlin.math.abs(existingOccurredAt - incomingOccurredAt) <= AUTO_SAME_SOURCE_WINDOW_DUPLICATE_MS
}

internal fun shouldDedupeByAutoChannel(existingChannel: String, incomingChannel: String): Boolean {
    val e = existingChannel.uppercase()
    val i = incomingChannel.uppercase()
    if (e.isBlank() || i.isBlank()) return false
    return e in setOf("NOTIFY", "ACCESS") && i in setOf("NOTIFY", "ACCESS")
}
