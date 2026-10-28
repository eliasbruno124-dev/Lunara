package de.lunara.app.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import de.lunara.app.R
import de.lunara.app.core.model.UserRole
import de.lunara.app.data.local.PeriodEntity
import de.lunara.app.data.remote.dto.FertilityEstimateDto
import de.lunara.app.data.remote.dto.PredictionDto
import de.lunara.app.ui.main.CalendarLegend
import de.lunara.app.ui.main.CalendarPredictionDayRole
import de.lunara.app.ui.main.CalendarScreen
import de.lunara.app.ui.main.DayCell
import de.lunara.app.ui.theme.DarkCalendarColors
import de.lunara.app.ui.theme.LightCalendarColors
import de.lunara.app.ui.theme.LunaraTheme
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Fully synthetic rendering fixtures: no account, network, or production health records. */
class CalendarColorsComposeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var capturePrefix = "calendar-colors"

    @Test fun lightCategoriesTodayAndLegendKeepTheirMeaning() = samples(dark = false)
    @Test fun darkCategoriesTodayAndLegendKeepTheirMeaning() = samples(dark = true)
    @Test fun narrowLargeTextLightRetainsEveryMarker() = samples(dark = false, fontScale = 1.6f)
    @Test fun narrowLargeTextDarkRetainsEveryMarker() = samples(dark = true, fontScale = 1.6f)
    @Test fun fullSyntheticCalendarLight() = calendar(dark = false)
    @Test fun fullSyntheticCalendarDark() = calendar(dark = true)

    private fun samples(dark: Boolean, fontScale: Float = 1f) {
        val palette = if (dark) DarkCalendarColors else LightCalendarColors
        val theme = if (dark) "dark" else "light"
        capturePrefix = "calendar-colors-$theme-${if (fontScale > 1f) "large" else "normal"}"
        val date = LocalDate.of(2026, 8, 28)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                LunaraTheme(darkTheme = dark) {
                    Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            Column(
                                Modifier.width(320.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                // Seven narrow cells reproduce the main calendar column width; today is present
                                // in every category, including both recorded/forecast and forecast/fertile overlap.
                                Row(Modifier.fillMaxWidth()) {
                                    DayCell(date, true, true, CalendarPredictionDayRole.START_UNCERTAINTY, true, Modifier.weight(1f).testTag("sample-recorded"))
                                    DayCell(date, true, false, CalendarPredictionDayRole.START_UNCERTAINTY, true, Modifier.weight(1f).testTag("sample-predicted-start"))
                                    DayCell(date, true, false, CalendarPredictionDayRole.POSSIBLE_DURATION, true, Modifier.weight(1f).testTag("sample-predicted-duration"))
                                    DayCell(date, true, false, CalendarPredictionDayRole.NONE, true, Modifier.weight(1f).testTag("sample-fertile"))
                                    repeat(3) { DayCell(date, false, false, CalendarPredictionDayRole.NONE, false, Modifier.weight(1f)) }
                                }
                                CalendarLegend(showFertility = true)
                            }
                        }
                    }
                }
            }
        }
        listOf(
            Triple("recorded", "P", palette.recorded.container),
            Triple("predicted-start", "≈", palette.predicted.container),
            Triple("predicted-duration", "≈", palette.predicted.container),
            Triple("fertile", "F?", palette.fertile.container),
        ).forEach { (category, marker, color) ->
            compose.onNodeWithTag("sample-$category")
                .performScrollTo()
                .assertIsDisplayed()
                .assertHasNoClickAction()
                .assert(hasAnyDescendant(hasText(marker)))
                .assertContentDescriptionContains(compose.activity.getString(R.string.calendar_day_today_status), substring = true)
            assertTextFits("sample-$category", marker)
            assertTextFits("sample-$category", "28")
            assertContainsFill("sample-$category", color)
            compose.onNodeWithTag("calendar-legend-$category").performScrollTo().assertIsDisplayed()
            assertContainsFill("calendar-legend-$category", color)
        }
        compose.onNodeWithTag("sample-recorded").performScrollTo()
        capture("calendar-colors-$theme-${if (fontScale > 1f) "large" else "normal"}")
    }

    private fun calendar(dark: Boolean) {
        val month = YearMonth.now()
        val theme = if (dark) "dark" else "light"
        val prediction = PredictionDto(
            available = true,
            predictedStartFrom = month.atDay(22).toString(), predictedStartTo = month.atDay(24).toString(),
            predictedEndFrom = month.atDay(26).toString(), predictedEndTo = month.atDay(28).toString(),
            fertileWindowFrom = month.atDay(12).toString(), fertileWindowTo = month.atDay(16).toString(),
            fertilityEstimate = FertilityEstimateDto(true, true, true, "AVAILABLE", 4, true, true),
            confidence = "MEDIUM",
        )
        compose.setContent {
            LunaraTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    CalendarScreen(
                        periods = listOf(PeriodEntity("synthetic-colors", "synthetic-tracker", month.atDay(3).toString(), month.atDay(7).toString(), createdAt = "", updatedAt = "")),
                        role = UserRole.TRACKER, prediction = prediction, sharedPeriodDates = emptyList(),
                        onPeriodAdded = { _, _ -> }, onPeriodUpdated = { _, _, _ -> }, onPeriodDeleted = {},
                    )
                }
            }
        }
        compose.onNodeWithTag("calendar-screen").performScrollToNode(hasTestTag("calendar-month-pager"))
        compose.onNodeWithTag("calendar-day-${month.atDay(3)}", useUnmergedTree = true).assert(hasAnyDescendant(hasText("P")))
        compose.onNodeWithTag("calendar-day-${month.atDay(22)}", useUnmergedTree = true).assert(hasAnyDescendant(hasText("≈")))
        compose.onNodeWithTag("calendar-day-${month.atDay(12)}", useUnmergedTree = true).assert(hasAnyDescendant(hasText("F?")))
        capture("calendar-full-$theme")
        compose.onNodeWithTag("calendar-screen").performScrollToNode(hasTestTag("calendar-legend"))
        capture("calendar-legend-$theme")
    }

    private fun assertContainsFill(tag: String, expected: Color) {
        val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        var matches = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            if (pixels[x, y].toArgb() == expected.toArgb()) matches++
        }
        assertTrue("$tag must visibly use its semantic fill", matches > pixels.width * pixels.height / 5)
    }

    private fun assertTextFits(parentTag: String, text: String) {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(parentTag)), useUnmergedTree = true)
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                assertTrue(action(results))
            }
        assertTrue("$text must have a measured layout in $parentTag", results.isNotEmpty())
        val fits = results.all { !it.hasVisualOverflow && it.lineCount == 1 }
        if (!fits) capture("$capturePrefix-overflow-$parentTag")
        val measurements = results.joinToString { result ->
            "size=${result.size}; paragraph=${result.multiParagraph.width}x${result.multiParagraph.height}; " +
                "widthOverflow=${result.didOverflowWidth}; heightOverflow=${result.didOverflowHeight}; " +
                "lines=${result.lineCount}; constraints=${result.layoutInput.constraints}; " +
                "density=${result.layoutInput.density}; fontSize=${result.layoutInput.style.fontSize}; " +
                "lineHeight=${result.layoutInput.style.lineHeight}"
        }
        assertTrue("$text must not wrap or clip in $parentTag: $measurements", fits)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        File(compose.activity.getExternalFilesDir(null), "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}
