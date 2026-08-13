package de.lunara.app.ui.main

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodEditValidationTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-13T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `accepts ISO date today or in the past`() {
        assertEquals(LocalDate.of(2026, 8, 13), PeriodEditValidation.parseAllowedStartDate("2026-08-13", clock))
        assertEquals(LocalDate.of(2026, 7, 31), PeriodEditValidation.parseAllowedStartDate("2026-07-31", clock))
    }

    @Test
    fun `rejects invalid and future dates`() {
        assertNull(PeriodEditValidation.parseAllowedStartDate("13.08.2026", clock))
        assertNull(PeriodEditValidation.parseAllowedStartDate("2026-02-30", clock))
        assertNull(PeriodEditValidation.parseAllowedStartDate("2026-08-14", clock))
    }

    @Test
    fun `sanitizes date field without accepting arbitrary text`() {
        assertEquals("2026-08-13", PeriodEditValidation.sanitizeDateInput("2026a-08-13extra"))
        assertEquals("2026-08-1", PeriodEditValidation.sanitizeDateInput("2026-08-1"))
    }

    @Test
    fun `accepts an optional end date in a realistic closed period range`() {
        val open = PeriodEditValidation.validateRange("2026-08-10", "", clock)
        val closed = PeriodEditValidation.validateRange("2026-08-10", "2026-08-13", clock)

        assertEquals(
            ValidPeriodDates(LocalDate.of(2026, 8, 10), null),
            (open as PeriodDateValidationResult.Valid).dates,
        )
        assertEquals(
            ValidPeriodDates(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 13)),
            (closed as PeriodDateValidationResult.Valid).dates,
        )
    }

    @Test
    fun `rejects future reversed and malformed end dates`() {
        assertEquals(
            PeriodDateValidationError.INVALID_END,
            (PeriodEditValidation.validateRange("2026-08-10", "2026-08-14", clock)
                as PeriodDateValidationResult.Invalid).error,
        )
        assertEquals(
            PeriodDateValidationError.END_BEFORE_START,
            (PeriodEditValidation.validateRange("2026-08-10", "2026-08-09", clock)
                as PeriodDateValidationResult.Invalid).error,
        )
        assertEquals(
            PeriodDateValidationError.INVALID_END,
            (PeriodEditValidation.validateRange("2026-06-01", "2026-02-30", clock)
                as PeriodDateValidationResult.Invalid).error,
        )
        assertTrue(
            PeriodEditValidation.validateRange("invalid", "", clock) is PeriodDateValidationResult.Invalid,
        )
    }
}
