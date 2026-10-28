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
    fun `prediction includes both start and duration in one complete possible band`() {
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
            false,
            band.contains(LocalDate.parse("2026-09-17")),
        )
        assertEquals(
            true,
            band.contains(LocalDate.parse("2026-09-18")),
        )
        assertEquals(
            true,
            band.contains(LocalDate.parse("2026-09-21")),
        )
        assertEquals(
            true,
            band.contains(LocalDate.parse("2026-09-22")),
        )
        assertEquals(
            true,
            band.contains(LocalDate.parse("2026-09-25")),
        )
        assertEquals(
            false,
            band.contains(LocalDate.parse("2026-09-26")),
        )
    }

    @Test
    fun `unified band crosses a year boundary and retains all original boundaries`() {
        val input = prediction(startFrom = "2026-12-29", startTo = "2027-01-02", endFrom = "2027-01-01", endTo = "2027-01-06")
        val band = requireNotNull(CalendarPredictionBand.from(input))
        assertEquals(LocalDate.parse(input.predictedStartFrom), band.startFrom)
        assertEquals(LocalDate.parse(input.predictedStartTo), band.startTo)
        assertEquals(LocalDate.parse(input.predictedEndFrom), band.endFrom)
        assertEquals(LocalDate.parse(input.predictedEndTo), band.endTo)
        assertEquals(false, band.contains(LocalDate.parse("2026-12-28")))
        for (offset in 0L..8L) {
            val date = band.startFrom.plusDays(offset)
            assertEquals(true, band.contains(date))
            assertEquals(CalendarDayKind.PREDICTED, CalendarDayKind.resolve(false, date in band, false))
        }
        assertEquals(false, band.contains(LocalDate.parse("2027-01-07")))
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
