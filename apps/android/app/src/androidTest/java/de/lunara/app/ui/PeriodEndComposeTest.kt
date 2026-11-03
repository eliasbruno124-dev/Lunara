package de.lunara.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import de.lunara.app.R
import de.lunara.app.core.model.UserRole
import de.lunara.app.data.local.PeriodEntity
import de.lunara.app.data.remote.dto.FertilityEstimateDto
import de.lunara.app.data.remote.dto.PartnerDashboardDto
import de.lunara.app.data.remote.dto.PredictionDto
import de.lunara.app.ui.main.CalendarScreen
import de.lunara.app.ui.main.PartnerDashboard
import de.lunara.app.ui.main.TrackerDashboard
import de.lunara.app.ui.theme.LunaraTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Synthetic explicit days exercise foreground rollover without changing the device clock. */
class PeriodEndComposeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val raw = PeriodEntity("assumed", "synthetic", "2026-11-01", createdAt = "", updatedAt = "")

    @Test fun daySixChangesPrimaryActionWithoutARefreshOrSyntheticEndMutation() {
        val day = mutableStateOf(LocalDate.parse("2026-11-05"))
        compose.setContent {
            LunaraTheme {
                TrackerDashboard("Demo", listOf(raw), emptyList(), null, 0, 0, null,
                    {}, {}, { _, _, _ -> }, {}, {}, {}, {}, predictionReferenceDate = day.value)
            }
        }
        compose.onNodeWithTag("tracker-dashboard").performScrollToNode(hasTestTag("period-end"))
        compose.onNodeWithTag("period-end").assertIsDisplayed()
        compose.runOnIdle { day.value = day.value.plusDays(1) }
        compose.onNodeWithTag("period-start").assertIsDisplayed().assertIsEnabled()
        assertEquals(null, raw.endDate)
        compose.onNodeWithTag("tracker-dashboard").performScrollToNode(hasTestTag("period-manage-assumed"))
        compose.onNodeWithTag("period-manage-assumed").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.period_assumed_correction)).assertIsDisplayed()
    }

    @Test fun manualLongerEndRemainsSelectedAcrossMidnightAndCanBeSaved() {
        val day = mutableStateOf(LocalDate.parse("2026-11-08"))
        var saved: Pair<LocalDate, LocalDate?>? = null
        compose.setContent {
            LunaraTheme {
                CalendarScreen(listOf(raw), UserRole.TRACKER, null, emptyList(), { _, _ -> },
                    { _, start, end -> saved = start to end }, {}, today = day.value)
            }
        }
        compose.onNodeWithTag("calendar-day-2026-11-03").performClick()
        compose.onNodeWithTag("period-picker-day-2026-11-08").performClick()
        compose.runOnIdle { day.value = day.value.plusDays(1) }
        compose.onNodeWithTag("period-update-confirm").assertIsEnabled().performClick()
        assertEquals(LocalDate.parse("2026-11-01") to LocalDate.parse("2026-11-08"), saved)
        assertEquals(null, raw.endDate)
    }

    @Test fun independentPartnerFertilityExpiresOnItsOwnEndWithoutNewPayload() {
        val day = mutableStateOf(LocalDate.parse("2026-11-19"))
        val shared = PartnerDashboardDto(prediction = PredictionDto(available = false, confidence = "UNKNOWN",
            fertileWindowFrom = "2026-11-01", fertileWindowTo = "2026-11-19",
            fertilityEstimate = FertilityEstimateDto(true, true, true, "AVAILABLE", 3, true, true)))
        compose.setContent { LunaraTheme { PartnerDashboard("Demo", shared, false, {}, predictionReferenceDate = day.value) } }
        val label = compose.activity.getString(R.string.estimated_fertile_window)
        compose.onNodeWithTag("partner-dashboard").performScrollToNode(hasText(label))
        compose.onNodeWithText(label).assertIsDisplayed()
        compose.runOnIdle { day.value = day.value.plusDays(1) }
        compose.onNodeWithText(label).assertDoesNotExist()
    }
}
