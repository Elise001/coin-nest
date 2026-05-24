package com.example.coin_nest.autobook

import android.content.Context
import com.example.coin_nest.data.AutoTransactionInsertResult
import com.example.coin_nest.data.CoinNestRepository

enum class AutoBookChannel(val telemetryLabel: String) {
    NOTIFY("NOTIFY"),
    ACCESS("ACCESS")
}

data class AutoBookRawEvent(
    val packageName: String,
    val title: String?,
    val text: String,
    val occurredAtEpochMs: Long,
    val channel: AutoBookChannel
)

sealed class AutoBookProcessResult {
    data class Rejected(val reason: String) : AutoBookProcessResult()
    data class Dropped(val reason: String) : AutoBookProcessResult()
    data class Inserted(
        val payment: ParsedPayment,
        val insertResult: AutoTransactionInsertResult
    ) : AutoBookProcessResult()
}

object AutoBookProcessor {
    suspend fun process(
        context: Context,
        repository: CoinNestRepository,
        event: AutoBookRawEvent,
        shouldDropBeforeParse: () -> String? = { null },
        shouldDropBeforeInsert: (ParsedPayment) -> String? = { null }
    ): AutoBookProcessResult {
        val decisionText = event.mergedText()
        val preview = decisionText.take(800)
        val decision = AutoBookAiDecisionLayer.assess(event.packageName, decisionText)
        AutoBookTelemetry.track(
            context = context,
            event = if (decision.accepted) "ai_decision_accept" else "ai_decision_reject",
            packageName = event.packageName,
            reason = "${event.channel.telemetryLabel} ${decision.kind} ${decision.confidence} ${decision.reason} | raw=${decisionText.take(720)}"
        )
        if (!decision.accepted) {
            if (event.channel == AutoBookChannel.ACCESS) {
                AutoBookTelemetry.track(
                    context = context,
                    event = "accessibility_drop",
                    packageName = event.packageName,
                    reason = "AI_${decision.kind}_${decision.confidence} | raw=${decisionText.take(720)}"
                )
            }
            return AutoBookProcessResult.Rejected(decision.reason)
        }

        shouldDropBeforeParse()?.let { reason ->
            trackAccessibilityDropIfNeeded(context, event, reason, decisionText)
            return AutoBookProcessResult.Dropped(reason)
        }

        if (event.channel == AutoBookChannel.ACCESS) {
            AutoBookTelemetry.track(
                context = context,
                event = "accessibility_parse_start",
                packageName = event.packageName,
                reason = event.title.orEmpty()
            )
        }

        val parsedResult = PaymentNotificationParser.parseWithDebug(
            packageName = event.packageName,
            title = event.title,
            text = event.text,
            postTime = event.occurredAtEpochMs,
            aiDecisionOverride = decision
        )
        val parsed = parsedResult.payment
        if (parsed == null) {
            AutoBookTelemetry.track(
                context = context,
                event = if (event.channel == AutoBookChannel.ACCESS) {
                    "accessibility_parse_failed"
                } else {
                    "parse_failed"
                },
                packageName = event.packageName,
                reason = "${parsedResult.reason} | raw=$preview"
            )
            return AutoBookProcessResult.Rejected(parsedResult.reason)
        }

        AutoBookTelemetry.trackRecognizedPayment(
            context = context,
            packageName = event.packageName,
            channel = event.channel.telemetryLabel,
            payment = parsed
        )

        shouldDropBeforeInsert(parsed)?.let { reason ->
            trackAccessibilityDropIfNeeded(context, event, reason, decisionText)
            return AutoBookProcessResult.Dropped(reason)
        }

        val insertResult = repository.addAutoTransaction(
            amountCents = parsed.amountCents,
            type = parsed.type,
            source = parsed.source,
            note = parsed.note,
            fingerprint = parsed.fingerprint,
            occurredAtEpochMs = parsed.occurredAtEpochMs,
            channel = event.channel.telemetryLabel,
            parent = parsed.parentCategory,
            child = parsed.childCategory
        )
        trackInsertResult(context, event, insertResult)
        return AutoBookProcessResult.Inserted(
            payment = parsed,
            insertResult = insertResult
        )
    }

    private fun AutoBookRawEvent.mergedText(): String {
        return listOfNotNull(title, text)
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun trackAccessibilityDropIfNeeded(
        context: Context,
        event: AutoBookRawEvent,
        reason: String,
        rawText: String
    ) {
        if (event.channel != AutoBookChannel.ACCESS) return
        AutoBookTelemetry.track(
            context = context,
            event = "accessibility_drop",
            packageName = event.packageName,
            reason = "$reason | raw=${rawText.take(720)}"
        )
    }

    private fun trackInsertResult(
        context: Context,
        event: AutoBookRawEvent,
        insertResult: AutoTransactionInsertResult
    ) {
        val telemetryEvent = when (event.channel) {
            AutoBookChannel.NOTIFY -> when {
                insertResult.insertedId != null && insertResult.shouldNotify -> "insert_success"
                insertResult.insertedId != null -> "insert_linked"
                else -> "insert_drop"
            }
            AutoBookChannel.ACCESS -> if (insertResult.insertedId != null && insertResult.shouldNotify) {
                "accessibility_insert_success"
            } else {
                "accessibility_insert_drop"
            }
        }
        AutoBookTelemetry.track(
            context = context,
            event = telemetryEvent,
            packageName = event.packageName,
            reason = insertResult.reason
        )
    }
}
