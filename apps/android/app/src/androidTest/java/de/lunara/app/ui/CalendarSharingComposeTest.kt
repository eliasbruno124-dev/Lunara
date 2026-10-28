package de.lunara.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import de.lunara.app.R
import de.lunara.app.core.model.UserRole
import de.lunara.app.data.remote.dto.PartnerCalendarAccessDto
import de.lunara.app.data.remote.dto.PartnerDashboardDto
import de.lunara.app.ui.main.CalendarScreen
import de.lunara.app.ui.main.CalendarStatusPolicy
import de.lunara.app.ui.main.PartnerCalendarAccessSettings
import de.lunara.app.ui.main.PartnerDashboard
import de.lunara.app.ui.theme.LunaraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

/** Synthetic server-shaped JSON only; no sign-in, production state, or network requests. */
class CalendarSharingComposeTest {
    @get:Rule val compose = createComposeRule()
    private val month = YearMonth.now()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun serverDateOnlyRangesShowInclusivePeriodAndSafeFertilityMarkersReadOnly() {
        calendar(sharedFixture())
        showGrid()
        for (day in 3..5) compose.onNodeWithTag("calendar-day-${month.atDay(day)}")
            .assert(hasAnyDescendant(hasText("P"))).assertHasNoClickAction()
        compose.onNodeWithTag("calendar-day-${month.atDay(15)}").assert(hasAnyDescendant(hasText("F?"))).assertHasNoClickAction()
        compose.onNodeWithTag("calendar-screen").performScrollToNode(hasTestTag("calendar-fertility-safety"))
        compose.onNodeWithText(text(R.string.fertility_safety_notice)).assertIsDisplayed()
    }

    @Test fun deniedPermissionsExplainOmissionsAndNeverRenderAccidentallyPresentFields() {
        calendar(sharedFixture().copy(calendarAccess = PartnerCalendarAccessDto(false, false)))
        compose.onNodeWithText(text(R.string.calendar_periods_not_shared), substring = true).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.calendar_fertility_not_shared), substring = true).assertIsDisplayed()
        compose.onNodeWithTag("calendar-sharing-notice")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        showGrid()
        compose.onAllNodesWithText("P").assertCountEquals(0)
        compose.onAllNodesWithText("F?").assertCountEquals(0)
        compose.onNodeWithTag("calendar-day-${month.atDay(3)}").assertHasNoClickAction()
    }

    @Test fun initialLoadingWinsOverDefaultUnavailableAndDoesNotClaimDeniedPermission() {
        calendar(null, unavailable = true, refreshing = true)
        compose.onNodeWithTag("calendar-loading-notice").assertIsDisplayed()
        compose.onNodeWithTag("calendar-unavailable-notice").assertDoesNotExist()
        compose.onNodeWithTag("calendar-sharing-notice").assertDoesNotExist()
        compose.onNodeWithTag("calendar-retry").assertDoesNotExist()
    }

    @Test fun unavailableSnapshotOffersAccessibleRetryWithoutInventingSharingState() {
        var retries = 0
        calendar(null, unavailable = true, onRefresh = { retries++ })
        compose.onNodeWithTag("calendar-unavailable-notice").assertIsDisplayed()
        compose.onNodeWithTag("calendar-sharing-notice").assertDoesNotExist()
        compose.onNodeWithTag("calendar-retry").assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun cachedNetworkFailureKeepsAllowedMarkersWithStaleWarning() {
        calendar(sharedFixture(), unavailable = true)
        compose.onNodeWithText(text(R.string.calendar_shared_cached)).assertIsDisplayed()
        showGrid()
        compose.onNodeWithTag("calendar-day-${month.atDay(3)}").assert(hasAnyDescendant(hasText("P")))
        compose.onNodeWithTag("calendar-day-${month.atDay(15)}").assert(hasAnyDescendant(hasText("F?")))
    }

    @Test fun pausedSnapshotHidesEvenPreviouslyAllowedMarkers() {
        calendar(sharedFixture().copy(sharingPaused = true))
        compose.onNodeWithText(text(R.string.calendar_sharing_paused)).assertIsDisplayed()
        showGrid()
        compose.onAllNodesWithText("P").assertCountEquals(0)
        compose.onAllNodesWithText("F?").assertCountEquals(0)
    }

    @Test fun authoritativeRevocationRemovesAlreadyRenderedMarkersOnRefresh() {
        val allowed = sharedFixture()
        val dashboard = mutableStateOf(allowed)
        compose.setContent {
            LunaraTheme {
                val current = dashboard.value
                CalendarScreen(
                    periods = emptyList(), role = UserRole.PARTNER,
                    prediction = current.prediction, sharedPeriodDates = current.periodDates,
                    onPeriodAdded = { _, _ -> error("Partner cannot add periods") },
                    onPeriodUpdated = { _, _, _ -> error("Partner cannot edit periods") },
                    onPeriodDeleted = { error("Partner cannot delete periods") },
                    partnerStatus = CalendarStatusPolicy.partner(current, false, false),
                )
            }
        }
        for (revoked in listOf(
            allowed.copy(calendarAccess = PartnerCalendarAccessDto(false, false)),
            allowed.copy(sharingPaused = true),
        )) {
            compose.runOnIdle { dashboard.value = allowed }
            showGrid()
            compose.onNodeWithTag("calendar-day-${month.atDay(3)}").assert(hasAnyDescendant(hasText("P")))
            compose.onNodeWithTag("calendar-day-${month.atDay(15)}").assert(hasAnyDescendant(hasText("F?")))
            compose.runOnIdle { dashboard.value = revoked }
            showGrid()
            compose.onAllNodesWithText("P").assertCountEquals(0)
            compose.onAllNodesWithText("F?").assertCountEquals(0)
        }
    }

    @Test fun oldServerUsesUnknownWordingRatherThanClaimingDeniedPermission() {
        calendar(Gson().fromJson("{\"periodDates\":[]}", PartnerDashboardDto::class.java))
        compose.onNodeWithText(text(R.string.calendar_periods_unknown), substring = true).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.calendar_fertility_unavailable), substring = true).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.calendar_periods_not_shared), substring = true).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.calendar_fertility_disabled), substring = true).assertDoesNotExist()
    }

    @Test fun partnerSettingsRowsAreReadOnlyAndDistinguishTrueFalseAndUnknown() {
        val dashboard = mutableStateOf<PartnerDashboardDto?>(PartnerDashboardDto(calendarAccess = PartnerCalendarAccessDto(true, false)))
        compose.setContent {
            LunaraTheme {
                PartnerCalendarAccessSettings(CalendarStatusPolicy.partner(dashboard.value, false, false), {})
            }
        }
        compose.onNodeWithTag("partner-period-access").assertTextContains(text(R.string.partner_calendar_access_shared)).assertHasNoClickAction()
        compose.onNodeWithTag("partner-fertility-access").assertTextContains(text(R.string.partner_calendar_access_denied)).assertHasNoClickAction()
        compose.runOnIdle { dashboard.value = PartnerDashboardDto() }
        compose.onNodeWithTag("partner-period-access").assertTextContains(text(R.string.partner_calendar_access_unknown))
        compose.onNodeWithTag("partner-fertility-access").assertTextContains(text(R.string.partner_calendar_access_unknown))
        compose.runOnIdle { dashboard.value = sharedFixture().copy(sharingPaused = true) }
        compose.onNodeWithTag("partner-period-access").assertTextContains(text(R.string.partner_calendar_access_paused))
        compose.onNodeWithTag("partner-fertility-access").assertTextContains(text(R.string.partner_calendar_access_paused))
    }

    @Test fun partnerHomeAlsoPrioritizesFirstLoadOverDefaultUnavailable() {
        compose.setContent { LunaraTheme { PartnerDashboard("Sam", null, true, {}, refreshing = true) } }
        compose.onNodeWithTag("partner-dashboard").performScrollToNode(hasTestTag("partner-loading-notice"))
        compose.onNodeWithTag("partner-loading-notice").assertIsDisplayed()
        compose.onNodeWithTag("partner-offline-notice").assertDoesNotExist()
    }

    private fun calendar(dashboard: PartnerDashboardDto?, unavailable: Boolean = false, refreshing: Boolean = false, onRefresh: () -> Unit = {}) {
        compose.setContent {
            LunaraTheme {
                CalendarScreen(
                    periods = emptyList(), role = UserRole.PARTNER,
                    prediction = dashboard?.prediction, sharedPeriodDates = dashboard?.periodDates.orEmpty(),
                    onPeriodAdded = { _, _ -> error("Partner cannot add periods") },
                    onPeriodUpdated = { _, _, _ -> error("Partner cannot edit periods") },
                    onPeriodDeleted = { error("Partner cannot delete periods") },
                    partnerStatus = CalendarStatusPolicy.partner(dashboard, unavailable, refreshing),
                    onPartnerRefresh = onRefresh,
                )
            }
        }
    }

    private fun showGrid() = compose.onNodeWithTag("calendar-screen")
        .performScrollToNode(hasTestTag("calendar-month-pager"))

    private fun sharedFixture(): PartnerDashboardDto = Gson().fromJson("""
        {"sharingPaused":false,"calendarAccess":{"periodDates":true,"fertileWindow":true},
         "periodDates":[{"startDate":"${month.atDay(3)}","endDate":"${month.atDay(5)}"}],
         "prediction":{"confidence":"MEDIUM","fertileWindowFrom":"${month.atDay(15)}","fertileWindowTo":"${month.atDay(17)}",
          "fertilityEstimate":{"enabled":true,"available":true,"estimated":true,"reason":"AVAILABLE",
           "basedOnCycleCount":3,"notContraception":true,"doesNotConfirmOvulation":true}}}
    """, PartnerDashboardDto::class.java)
}
