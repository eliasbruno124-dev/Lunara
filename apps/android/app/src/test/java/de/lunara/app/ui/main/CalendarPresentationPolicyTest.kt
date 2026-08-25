package de.lunara.app.ui.main

import de.lunara.app.data.remote.dto.PredictionDto
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class CalendarPresentationPolicyTest {
    @Test
    fun `main calendar maps January 1900 through five future years`() {
        val current = YearMonth.of(2026, 8)
        val last = CalendarMonthPagingPolicy.mainCalendarLastMonth(current)

        assertEquals(YearMonth.of(2031, 8), last)
        assertEquals(YearMonth.of(1900, 1), CalendarMonthPagingPolicy.monthForPage(0, last))
        assertEquals(
            current,
            CalendarMonthPagingPolicy.monthForPage(
                CalendarMonthPagingPolicy.pageForMonth(current, last),
                last,
            ),
        )
        assertEquals(
            last,
            CalendarMonthPagingPolicy.monthForPage(
                CalendarMonthPagingPolicy.pageCount(last) - 1,
                last,
            ),
        )
    }

    @Test
    fun `prediction separates possible start from remaining possible duration`() {
        val band = CalendarPredictionBand.from(
            prediction(
                startFrom = "2026-09-18",
                startTo = "2026-09-21",
                endFrom = "2026-09-22",
                endTo = "2026-09-25",
            ),
        )

        assertNotNull(band)
        requireNotNull(band)
        assertEquals(
            CalendarPredictionDayRole.NONE,
            band.roleFor(LocalDate.parse("2026-09-17")),
        )
        assertEquals(
            CalendarPredictionDayRole.START_UNCERTAINTY,
            band.roleFor(LocalDate.parse("2026-09-18")),
        )
        assertEquals(
            CalendarPredictionDayRole.START_UNCERTAINTY,
            band.roleFor(LocalDate.parse("2026-09-21")),
        )
        assertEquals(
            CalendarPredictionDayRole.POSSIBLE_DURATION,
            band.roleFor(LocalDate.parse("2026-09-22")),
        )
        assertEquals(
            CalendarPredictionDayRole.POSSIBLE_DURATION,
            band.roleFor(LocalDate.parse("2026-09-25")),
        )
        assertEquals(
            CalendarPredictionDayRole.NONE,
            band.roleFor(LocalDate.parse("2026-09-26")),
        )
    }

    @Test
    fun `prediction band fails closed for unavailable incomplete malformed or reversed input`() {
        assertNull(CalendarPredictionBand.from(prediction(available = false)))
        assertNull(CalendarPredictionBand.from(prediction(endTo = null)))
        assertNull(CalendarPredictionBand.from(prediction(startFrom = "not-a-date")))
        assertNull(
            CalendarPredictionBand.from(
                prediction(startFrom = "2026-09-22", startTo = "2026-09-18"),
            ),
        )
        assertNull(
            CalendarPredictionBand.from(
                prediction(endFrom = "2026-09-27", endTo = "2026-09-25"),
            ),
        )
    }

    private fun prediction(
        available: Boolean = true,
        startFrom: String? = "2026-09-18",
        startTo: String? = "2026-09-21",
        endFrom: String? = "2026-09-22",
        endTo: String? = "2026-09-25",
    ) = PredictionDto(
        available = available,
        predictedStartFrom = startFrom,
        predictedStartTo = startTo,
        predictedEndFrom = endFrom,
        predictedEndTo = endTo,
        confidence = "MEDIUM",
    )
}
