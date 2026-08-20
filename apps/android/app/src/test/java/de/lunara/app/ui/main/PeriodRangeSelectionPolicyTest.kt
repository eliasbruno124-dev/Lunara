package de.lunara.app.ui.main

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodRangeSelectionPolicyTest {
    @Test
    fun `first tap selects only the start date`() {
        val selection = tap(PeriodRangeSelection(), "2026-08-18")

        assertEquals(date("2026-08-18"), selection.startDate)
        assertNull(selection.endDate)
        assertFalse(selection.isComplete)
    }

    @Test
    fun `second tap after start completes the inclusive range`() {
        val start = tap(PeriodRangeSelection(), "2026-08-18")

        val selection = tap(start, "2026-08-22")

        assertEquals(date("2026-08-18"), selection.startDate)
        assertEquals(date("2026-08-22"), selection.endDate)
        assertTrue(selection.isComplete)
    }

    @Test
    fun `second tap completes a range across a month boundary`() {
        val start = tap(PeriodRangeSelection(), "2026-08-30")

        val selection = tap(start, "2026-09-03")

        assertEquals(date("2026-08-30"), selection.startDate)
        assertEquals(date("2026-09-03"), selection.endDate)
        assertTrue(selection.isComplete)
    }

    @Test
    fun `second tap on start creates a one day range`() {
        val start = tap(PeriodRangeSelection(), "2026-08-18")

        val selection = tap(start, "2026-08-18")

        assertEquals(date("2026-08-18"), selection.startDate)
        assertEquals(date("2026-08-18"), selection.endDate)
        assertTrue(selection.isComplete)
    }

    @Test
    fun `second tap before start replaces the start and keeps range open`() {
        val start = tap(PeriodRangeSelection(), "2026-08-18")

        val selection = tap(start, "2026-08-15")

        assertEquals(date("2026-08-15"), selection.startDate)
        assertNull(selection.endDate)
        assertFalse(selection.isComplete)
    }

    @Test
    fun `tap after completed range starts a fresh range`() {
        val completed = PeriodRangeSelection(date("2026-08-18"), date("2026-08-22"))

        val selection = tap(completed, "2026-09-03")

        assertEquals(date("2026-09-03"), selection.startDate)
        assertNull(selection.endDate)
        assertFalse(selection.isComplete)
    }

    @Test
    fun `selection round trips through rotation safe ISO strings`() {
        val original = PeriodRangeSelection(date("2026-08-18"), date("2026-08-22"))

        val restored = PeriodRangeSelectionPolicy.restore(
            original.savedStartDate,
            original.savedEndDate,
        )

        assertEquals(original, restored)
    }

    @Test
    fun `restore keeps open range and rejects malformed or reversed state`() {
        assertEquals(
            PeriodRangeSelection(startDate = date("2026-08-18")),
            PeriodRangeSelectionPolicy.restore("2026-08-18", null),
        )
        assertEquals(
            PeriodRangeSelection(),
            PeriodRangeSelectionPolicy.restore("not-a-date", "2026-08-22"),
        )
        assertEquals(
            PeriodRangeSelection(),
            PeriodRangeSelectionPolicy.restore("2026-08-22", "2026-08-18"),
        )
    }

    private fun tap(current: PeriodRangeSelection, value: String): PeriodRangeSelection =
        PeriodRangeSelectionPolicy.select(current, date(value))

    private fun date(value: String): LocalDate = LocalDate.parse(value)
}
