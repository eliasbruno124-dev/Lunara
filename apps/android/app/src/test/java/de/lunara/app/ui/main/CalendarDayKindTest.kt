package de.lunara.app.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarDayKindTest {
    @Test fun `recorded periods win every overlap without becoming an estimate`() {
        listOf(false, true).forEach { prediction ->
            listOf(false, true).forEach { fertile ->
                val kind = CalendarDayKind.resolve(true, prediction, fertile)
                assertEquals(CalendarDayKind.RECORDED, kind)
                assertEquals("P", kind.marker)
            }
        }
    }

    @Test fun `all predicted days share one estimated category and win over fertility`() {
        listOf(false, true).forEach { fertile ->
            assertEquals(CalendarDayKind.PREDICTED, CalendarDayKind.resolve(false, true, fertile))
        }
        assertEquals("≈", CalendarDayKind.PREDICTED.marker)
        assertEquals(listOf("P", "≈", "F?"), CalendarDayKind.entries.mapNotNull { it.marker })
    }

    @Test fun `fertility remains explicitly uncertain and ordinary days have no marker`() {
        assertEquals(CalendarDayKind.FERTILE, CalendarDayKind.resolve(false, false, true))
        assertEquals("F?", CalendarDayKind.FERTILE.marker)
        assertEquals(CalendarDayKind.NONE, CalendarDayKind.resolve(false, false, false))
        assertEquals(null, CalendarDayKind.NONE.marker)
    }
}
