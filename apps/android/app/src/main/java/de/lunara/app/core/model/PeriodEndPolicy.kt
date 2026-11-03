package de.lunara.app.core.model

import java.time.LocalDate

/** Derived bounds only: an assumed end must never replace a stored, unconfirmed null. */
object PeriodEndPolicy {
    const val ASSUMED_DAYS = 5L
    private val datePattern = Regex("\\d{4}-\\d{2}-\\d{2}")

    fun parse(raw: String?): LocalDate? = raw?.takeIf(datePattern::matches)
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun overlapEnd(start: LocalDate, end: LocalDate?): LocalDate =
        end ?: start.plusDays(ASSUMED_DAYS - 1)

    fun displayEnd(startRaw: String?, endRaw: String?, today: LocalDate): LocalDate? {
        val start = parse(startRaw) ?: return null
        if (start.isAfter(today)) return null
        val end = if (endRaw == null) minOf(today, overlapEnd(start, null)) else parse(endRaw) ?: return null
        return end.takeUnless { it.isBefore(start) || it.isAfter(today) }
    }

    fun isOngoing(startRaw: String?, endRaw: String?, today: LocalDate): Boolean {
        if (endRaw != null) return false
        val start = parse(startRaw) ?: return false
        return !today.isBefore(start) && !today.isAfter(overlapEnd(start, null))
    }

    fun isAssumedComplete(startRaw: String?, endRaw: String?, today: LocalDate): Boolean {
        if (endRaw != null) return false
        val start = parse(startRaw) ?: return false
        return today.isAfter(overlapEnd(start, null))
    }
}
