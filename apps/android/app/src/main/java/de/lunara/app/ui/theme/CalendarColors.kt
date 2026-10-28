package de.lunara.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Calendar meaning is independent of the app's decorative brand colors. */
internal data class CalendarCategoryColors(val container: Color, val content: Color)

internal data class CalendarColors(
    val recorded: CalendarCategoryColors,
    val predicted: CalendarCategoryColors,
    val fertile: CalendarCategoryColors,
)

internal val LightCalendarColors = CalendarColors(
    recorded = CalendarCategoryColors(Color(0xFFFFE4EC), Color(0xFF881337)),
    predicted = CalendarCategoryColors(Color(0xFFFFF1C2), Color(0xFF713F12)),
    fertile = CalendarCategoryColors(Color(0xFFCCFBF1), Color(0xFF115E59)),
)

internal val DarkCalendarColors = CalendarColors(
    recorded = CalendarCategoryColors(Color(0xFF5B1635), Color(0xFFFFD8E5)),
    predicted = CalendarCategoryColors(Color(0xFF4C3415), Color(0xFFFFE7A0)),
    fertile = CalendarCategoryColors(Color(0xFF123F42), Color(0xFF99F6E4)),
)

internal val LocalCalendarColors = staticCompositionLocalOf { LightCalendarColors }
