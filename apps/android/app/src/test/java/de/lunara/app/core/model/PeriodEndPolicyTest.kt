package de.lunara.app.core.model

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class PeriodEndPolicyTest {
    @Test fun fiveInclusiveCalendarDaysAcrossMonthYearLeapAndDstBoundaries() {
        for ((raw, expectedEnd) in listOf(
            "2026-11-01" to "2026-11-05", "2026-12-29" to "2027-01-02",
            "2024-02-27" to "2024-03-02", "2026-03-27" to "2026-03-31",
            "2026-10-23" to "2026-10-27",
        )) {
            val start = LocalDate.parse(raw)
            assertEquals(LocalDate.parse(expectedEnd), PeriodEndPolicy.overlapEnd(start, null))
            for (day in 0L..4L) {
                val today = start.plusDays(day)
                assertTrue(PeriodEndPolicy.isOngoing(raw, null, today))
                assertFalse(PeriodEndPolicy.isAssumedComplete(raw, null, today))
                assertEquals(today, PeriodEndPolicy.displayEnd(raw, null, today))
            }
            val daySix = start.plusDays(5)
            assertFalse(PeriodEndPolicy.isOngoing(raw, null, daySix))
            assertTrue(PeriodEndPolicy.isAssumedComplete(raw, null, daySix))
            assertEquals(LocalDate.parse(expectedEnd), PeriodEndPolicy.displayEnd(raw, null, daySix))
        }
    }

    @Test fun explicitShortAndLongEndsRemainAuthoritative() {
        val start = LocalDate.parse("2026-11-01")
        val today = start.plusDays(30)
        for (duration in listOf(1L, 5L, 8L, 16L)) {
            val end = start.plusDays(duration - 1)
            assertEquals(end, PeriodEndPolicy.overlapEnd(start, end))
            assertEquals(end, PeriodEndPolicy.displayEnd(start.toString(), end.toString(), today))
            assertFalse(PeriodEndPolicy.isAssumedComplete(start.toString(), end.toString(), today))
        }
    }

    @Test fun invalidFutureReversedAndUnobservedDatesNeverBecomeActualBleeding() {
        val today = LocalDate.parse("2026-11-01")
        for ((start, end) in listOf("bad" to null, "2026-11-02" to null, "2026-11-01" to "bad",
            "2026-11-01" to "2026-10-31", "2026-11-01" to "2026-11-02", "2026-1-01" to null)) {
            assertNull(PeriodEndPolicy.displayEnd(start, end, today))
            assertFalse(PeriodEndPolicy.isOngoing(start, end, today))
        }
        assertNull(PeriodEndPolicy.parse("2026-02-29"))
    }
}
