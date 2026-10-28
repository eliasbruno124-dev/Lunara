package de.lunara.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarColorsTest {
    @Test fun `light palette follows the shared rose amber teal contract`() {
        assertEquals(CalendarCategoryColors(Color(0xFFFFE4EC), Color(0xFF881337)), LightCalendarColors.recorded)
        assertEquals(CalendarCategoryColors(Color(0xFFFFF1C2), Color(0xFF713F12)), LightCalendarColors.predicted)
        assertEquals(CalendarCategoryColors(Color(0xFFCCFBF1), Color(0xFF115E59)), LightCalendarColors.fertile)
    }

    @Test fun `dark palette follows the shared rose amber teal contract`() {
        assertEquals(CalendarCategoryColors(Color(0xFF5B1635), Color(0xFFFFD8E5)), DarkCalendarColors.recorded)
        assertEquals(CalendarCategoryColors(Color(0xFF4C3415), Color(0xFFFFE7A0)), DarkCalendarColors.predicted)
        assertEquals(CalendarCategoryColors(Color(0xFF123F42), Color(0xFF99F6E4)), DarkCalendarColors.fertile)
    }

    @Test fun `all day numerals markers and legend labels exceed AA normal text contrast`() {
        listOf(LightCalendarColors, DarkCalendarColors).forEach { palette ->
            val categories = listOf(palette.recorded, palette.predicted, palette.fertile)
            assertEquals(3, categories.map { it.container }.distinct().size)
            categories.forEach { colors ->
                assertEquals(1f, colors.container.alpha)
                assertEquals(1f, colors.content.alpha)
                assertTrue("Insufficient text contrast: $colors", readable(colors.content, colors.container))
                // Borders use the same opaque content color and therefore exceed the 3:1 graphic threshold too.
                assertTrue(contrast(colors.content, colors.container) >= 3f)
            }
        }
    }

    @Test fun `contrast oracle rejects indistinguishable colors and accepts black on white`() {
        assertFalse(readable(Color.White, Color.White))
        assertFalse(readable(Color(0xFFAAAAAA), Color.White))
        assertTrue(readable(Color.Black, Color.White))
    }

    private fun readable(content: Color, container: Color) = contrast(content, container) >= 4.5f
    private fun contrast(first: Color, second: Color): Float {
        val firstLuminance = first.luminance()
        val secondLuminance = second.luminance()
        return (maxOf(firstLuminance, secondLuminance) + 0.05f) / (minOf(firstLuminance, secondLuminance) + 0.05f)
    }
}
