package de.lunara.app.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarDayKindTest {
    @Test fun `recorded periods win every overlap without becoming an estimate`() {
        CalendarPredictionDayRole.entries.forEach { prediction ->
            listOf(false, true).forEach { fertile ->
                val kind = CalendarDayKind.resolve(true, prediction, fertile)
                assertEquals(CalendarDayKind.RECORDED, kind)
                assertEquals("P", kind.marker)
            }
        }
    }

    @Test fun `predictions keep their start distinction and win over fertility`() {
        listOf(false, true).forEach { fertile ->
            assertEquals(CalendarDayKind.PREDICTED_START, CalendarDayKind.resolve(false, CalendarPredictionDayRole.START_UNCERTAINTY, fertile))
            assertEquals(CalendarDayKind.PREDICTED_DURATION, CalendarDayKind.resolve(false, CalendarPredictionDayRole.POSSIBLE_DURATION, fertile))
        }
        assertEquals("≈", CalendarDayKind.PREDICTED_START.marker)
        assertEquals("≈", CalendarDayKind.PREDICTED_DURATION.marker)
    }

    @Test fun `fertility remains explicitly uncertain and ordinary days have no marker`() {
        assertEquals(CalendarDayKind.FERTILE, CalendarDayKind.resolve(false, CalendarPredictionDayRole.NONE, true))
        assertEquals("F?", CalendarDayKind.FERTILE.marker)
        assertEquals(CalendarDayKind.NONE, CalendarDayKind.resolve(false, CalendarPredictionDayRole.NONE, false))
        assertEquals(null, CalendarDayKind.NONE.marker)
    }
}
