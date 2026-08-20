package de.lunara.app.ui.main

import de.lunara.app.data.local.PeriodEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarPeriodSelectionTest {
    @Test
    fun `selects every day inside a completed period`() {
        val period = period("closed", "2026-07-10", "2026-07-14")

        assertEquals(period, periodForDate(listOf(period), LocalDate.parse("2026-07-10")))
        assertEquals(period, periodForDate(listOf(period), LocalDate.parse("2026-07-12")))
        assertEquals(period, periodForDate(listOf(period), LocalDate.parse("2026-07-14")))
        assertNull(periodForDate(listOf(period), LocalDate.parse("2026-07-15")))
    }

    @Test
    fun `an open period remains selectable through today but not in the future`() {
        val period = period("open", "2026-08-10", null)
        val today = LocalDate.parse("2026-08-16")

        assertEquals(period, periodForDate(listOf(period), LocalDate.parse("2026-08-10"), today))
        assertEquals(period, periodForDate(listOf(period), LocalDate.parse("2026-08-15"), today))
        assertNull(periodForDate(listOf(period), LocalDate.parse("2026-08-17"), today))
    }

    @Test
    fun `malformed records are ignored and newest overlap wins deterministically`() {
        val older = period("older", "2026-07-10", "2026-07-20")
        val newer = period("newer", "2026-07-15", "2026-07-18")
        val malformed = period("bad", "not-a-date", null)

        assertEquals(
            newer,
            periodForDate(listOf(malformed, older, newer), LocalDate.parse("2026-07-16")),
        )
    }

    @Test
    fun `calendar range checks an ancient open period without materializing every day`() {
        val today = LocalDate.parse("2026-08-16")

        assertEquals(
            true,
            calendarRangeContains(
                date = LocalDate.parse("2026-08-01"),
                startDate = "0001-01-01",
                endDate = null,
                today = today,
            ),
        )
        assertEquals(
            false,
            calendarRangeContains(
                date = LocalDate.parse("2026-08-17"),
                startDate = "0001-01-01",
                endDate = null,
                today = today,
            ),
        )
    }

    @Test
    fun `calendar range rejects malformed reversed and incomplete fixed ranges`() {
        val date = LocalDate.parse("2026-08-12")

        assertEquals(false, calendarRangeContains(date, "bad", "2026-08-13"))
        assertEquals(false, calendarRangeContains(date, "2026-08-13", "2026-08-10"))
        assertEquals(
            false,
            calendarRangeContains(
                date = date,
                startDate = "2026-08-10",
                endDate = null,
                extendOpenRange = false,
            ),
        )
    }

    @Test
    fun `two tap range exposes start middle end and single day markers`() {
        val range = PeriodRangeSelection(
            startDate = LocalDate.parse("2026-08-18"),
            endDate = LocalDate.parse("2026-08-22"),
        )

        assertEquals(PeriodRangeDayRole.START, periodRangeDayRole(range, LocalDate.parse("2026-08-18")))
        assertEquals(PeriodRangeDayRole.MIDDLE, periodRangeDayRole(range, LocalDate.parse("2026-08-20")))
        assertEquals(PeriodRangeDayRole.END, periodRangeDayRole(range, LocalDate.parse("2026-08-22")))
        assertEquals(PeriodRangeDayRole.NONE, periodRangeDayRole(range, LocalDate.parse("2026-08-23")))
        assertEquals(
            PeriodRangeDayRole.SINGLE,
            periodRangeDayRole(
                PeriodRangeSelection(LocalDate.parse("2026-08-18"), LocalDate.parse("2026-08-18")),
                LocalDate.parse("2026-08-18"),
            ),
        )
    }

    @Test
    fun `range overlap catches enclosed periods and excludes the edited record`() {
        val existing = period("existing", "2026-08-20", "2026-08-21")
        val selection = PeriodRangeSelection(
            startDate = LocalDate.parse("2026-08-18"),
            endDate = LocalDate.parse("2026-08-22"),
        )

        assertEquals(true, periodRangeOverlapsExisting(selection, listOf(existing)))
        assertEquals(
            false,
            periodRangeOverlapsExisting(selection, listOf(existing), excludedPeriodId = "existing"),
        )
    }

    @Test
    fun `open selection checks through today and ignores deleted records`() {
        val today = LocalDate.parse("2026-08-20")
        val active = period("active", "2026-08-19", "2026-08-19")
        val deleted = active.copy(id = "deleted", deletedAt = "2026-08-20T12:00:00Z")
        val selection = PeriodRangeSelection(startDate = LocalDate.parse("2026-08-18"))

        assertEquals(true, periodRangeOverlapsExisting(selection, listOf(active), today))
        assertEquals(false, periodRangeOverlapsExisting(selection, listOf(deleted), today))
    }

    private fun period(id: String, startDate: String, endDate: String?) = PeriodEntity(
        id = id,
        userId = "tracker",
        startDate = startDate,
        endDate = endDate,
        createdAt = "",
        updatedAt = "",
        syncState = "SYNCED",
    )
}
