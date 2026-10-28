package de.lunara.app.ui.main

import de.lunara.app.data.remote.dto.PredictionDto
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Summary-only projection. Historical calendar and separately shared fertility remain intact. */
internal object PredictionFreshnessPolicy {
    fun referenceDate(now: Instant, knownTimeZone: String? = null, fallbackZone: ZoneId = ZoneId.systemDefault()): LocalDate =
        now.atZone(resolveZone(knownTimeZone, fallbackZone)).toLocalDate()

    fun millisecondsUntilNextDay(now: Instant, knownTimeZone: String? = null, fallbackZone: ZoneId = ZoneId.systemDefault()): Long {
        val zone = resolveZone(knownTimeZone, fallbackZone)
        val next = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        return (Duration.between(now, next).toMillis() + 25L).coerceAtLeast(25L)
    }

    fun unavailableReason(prediction: PredictionDto?, referenceDate: LocalDate, pendingChanges: Boolean = false): String? {
        if (prediction?.available != true) return prediction?.reason ?: "UNAVAILABLE"
        val from = runCatching { LocalDate.parse(prediction.predictedStartFrom) }.getOrNull() ?: return "UNAVAILABLE"
        val to = runCatching { LocalDate.parse(prediction.predictedStartTo) }.getOrNull() ?: return "UNAVAILABLE"
        return when {
            to.isBefore(from) -> "UNAVAILABLE"
            to.isBefore(referenceDate) -> "STALE_HISTORY"
            pendingChanges -> "PENDING_CHANGES"
            else -> null
        }
    }

    private fun resolveZone(knownTimeZone: String?, fallbackZone: ZoneId): ZoneId =
        knownTimeZone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: fallbackZone
}
