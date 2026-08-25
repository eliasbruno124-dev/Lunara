package de.lunara.app.ui.main

import de.lunara.app.data.remote.dto.PredictionDto
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Stable absolute bounds keep a restored pager page mapped to the same calendar month. */
internal object CalendarMonthPagingPolicy {
    val firstMonth: YearMonth = YearMonth.of(1900, 1)

    fun mainCalendarLastMonth(currentMonth: YearMonth): YearMonth = currentMonth.plusYears(5)

    fun pageCount(lastMonth: YearMonth): Int =
        (ChronoUnit.MONTHS.between(firstMonth, lastMonth).coerceAtLeast(0) + 1).toInt()

    fun pageForMonth(month: YearMonth, lastMonth: YearMonth): Int {
        val bounded = month.coerceIn(firstMonth, lastMonth)
        return ChronoUnit.MONTHS.between(firstMonth, bounded).toInt()
    }

    fun monthForPage(page: Int, lastMonth: YearMonth): YearMonth {
        val boundedPage = page.coerceIn(0, pageCount(lastMonth) - 1)
        return firstMonth.plusMonths(boundedPage.toLong())
    }
}

internal enum class CalendarPredictionDayRole {
    NONE,
    /** The period may begin on this day. */
    START_UNCERTAINTY,
    /** This day is within the remaining possible predicted period duration. */
    POSSIBLE_DURATION,
}

/**
 * A fail-closed calendar projection of the server prediction.
 *
 * The start interval is kept separate from the remaining possible duration so the calendar does
 * not present an uncertain start as an observed period. The complete possible band ends at the
 * latest predicted end. No partner data is inferred locally; callers pass only the server-projected
 * prediction that the current role is allowed to see.
 */
internal data class CalendarPredictionBand(
    val startFrom: LocalDate,
    val startTo: LocalDate,
    val endFrom: LocalDate,
    val endTo: LocalDate,
) {
    fun roleFor(date: LocalDate): CalendarPredictionDayRole = when {
        date.isBefore(startFrom) || date.isAfter(endTo) -> CalendarPredictionDayRole.NONE
        !date.isAfter(startTo) -> CalendarPredictionDayRole.START_UNCERTAINTY
        else -> CalendarPredictionDayRole.POSSIBLE_DURATION
    }

    companion object {
        fun from(prediction: PredictionDto?): CalendarPredictionBand? {
            if (prediction?.available != true) return null
            val startFrom = prediction.predictedStartFrom.toLocalDateOrNull() ?: return null
            val startTo = prediction.predictedStartTo.toLocalDateOrNull() ?: return null
            val endFrom = prediction.predictedEndFrom.toLocalDateOrNull() ?: return null
            val endTo = prediction.predictedEndTo.toLocalDateOrNull() ?: return null

            if (
                startTo.isBefore(startFrom) ||
                endFrom.isBefore(startFrom) ||
                endTo.isBefore(endFrom) ||
                endTo.isBefore(startTo)
            ) return null

            return CalendarPredictionBand(startFrom, startTo, endFrom, endTo)
        }
    }
}

private fun String?.toLocalDateOrNull(): LocalDate? =
    this?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }
