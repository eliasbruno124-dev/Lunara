package de.lunara.app.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import de.lunara.app.R
import de.lunara.app.data.remote.dto.PartnerDashboardDto
import de.lunara.app.data.remote.dto.PredictionDto
import de.lunara.app.data.repository.RelationshipState
import de.lunara.app.ui.components.LunaraWordmark
import de.lunara.app.ui.main.PartnerDashboard
import de.lunara.app.ui.main.RelationshipCard
import de.lunara.app.ui.main.SessionRecoveryDialog
import de.lunara.app.ui.main.SessionRecoveryNotice
import de.lunara.app.ui.main.SessionRecoveryStatus
import de.lunara.app.ui.main.TrackerDashboard
import de.lunara.app.ui.theme.LunaraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Synthetic fixtures only: no sign-in, network, account database, or real user content. */
class SessionPolishComposeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val prediction = PredictionDto(
        confidence = "MEDIUM",
        basedOnCycleCount = 5,
        predictedStartFrom = LocalDate.of(2026, 10, 28).toString(),
        predictedStartTo = LocalDate.of(2026, 11, 2).toString(),
    )

    @Test fun compactTrackerKeepsBothActionsReachableAndTouchSized() {
        var started = 0
        compose.setContent { CompactFrame { Tracker { started++ } } }
        compose.onNodeWithTag("prediction-hero").assertIsDisplayed()
        compose.onNodeWithTag("tracker-dashboard").performScrollToNode(hasTestTag("period-start"))
        compose.onNodeWithTag("period-start").assertIsDisplayed().assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, started)
        compose.onNodeWithTag("tracker-dashboard").performScrollToNode(hasTestTag("today-log"))
        compose.onNodeWithTag("today-log").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("tracker-dashboard").performScrollToIndex(0)
        capture("compact-tracker-light")
    }

    @Test fun compactDarkLargeTypeForecastKeepsExplanationReachable() {
        compose.setContent { CompactFrame(dark = true, fontScale = 1.6f) { Tracker {} } }
        compose.onNodeWithTag("tracker-dashboard").performScrollToNode(hasTestTag("prediction-details"))
        compose.onNodeWithTag("prediction-details").assertIsDisplayed().assertHasClickAction().performClick()
        compose.onNodeWithText(text(R.string.prediction_no_exact_day)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.close)).performClick()
        compose.onNodeWithTag("tracker-dashboard").performScrollToNode(hasTestTag("period-start"))
        compose.onNodeWithTag("period-start").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        capture("compact-tracker-dark-large-type")
    }

    @Test fun recoveryNoticeExplainsLocalSafetyAndOffersOneAccessibleAction() {
        var renewals = 0
        compose.setContent {
            CompactFrame(dark = true, fontScale = 1.3f) {
                SessionRecoveryNotice({ renewals++ }, Modifier.padding(16.dp))
            }
        }
        compose.onNodeWithTag("session-recovery-notice")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithText(text(R.string.session_recovery_short)).assertIsDisplayed()
        compose.onNodeWithTag("session-recovery-action").assertHeightIsAtLeast(48.dp).assertHasClickAction().performClick()
        assertEquals(1, renewals)
        capture("recovery-notice-dark")
    }

    @Test fun wrongAccountRecoveryKeepsBothMethodsAndSafeDismissalAtLargeType() {
        var dismissed = 0
        compose.setContent {
            CompactFrame(fontScale = 1.6f) {
                SessionRecoveryDialog(
                    displayName = "Alex",
                    status = SessionRecoveryStatus.WRONG_ACCOUNT,
                    showAuthentik = true,
                    showPassword = true,
                    onAuthentik = {},
                    onPassword = { _, _ -> },
                    onDismiss = { dismissed++ },
                )
            }
        }
        compose.onNodeWithText(text(R.string.session_recovery_authentik)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.session_recovery_password)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.session_recovery_wrong_account)).performScrollTo().assertIsDisplayed()
        captureWindow("recovery-wrong-account-large-type")
        compose.onNodeWithText(text(R.string.cancel)).assertIsDisplayed().performClick()
        assertEquals(1, dismissed)
    }

    @Test fun firstPartnerLoadDoesNotClaimDataIsUnsharedOrMissing() {
        compose.setContent {
            CompactFrame {
                PartnerDashboard("Sam", null, false, {}, refreshing = true)
            }
        }
        compose.onNodeWithTag("partner-loading-notice").assertIsDisplayed()
        compose.onAllNodesWithText(text(R.string.partner_dashboard_refreshing)).assertCountEquals(1)
        compose.onNodeWithTag("prediction-hero").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.journal_partner_prediction_empty)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.no_partner_tip_available)).assertDoesNotExist()
        capture("partner-loading-light")
    }

    @Test fun cachedPartnerOverviewRetainsForecastAndOffersRetryInDarkMode() {
        var retries = 0
        compose.setContent {
            CompactFrame(dark = true) {
                PartnerDashboard(
                    "Sam", PartnerDashboardDto(prediction = prediction), true, { retries++ },
                    relationship = RelationshipState(known = true, connected = true, peerName = "Alex", unavailable = true),
                    predictionReferenceDate = LocalDate.of(2026, 10, 28),
                )
            }
        }
        compose.onNodeWithTag("partner-offline-notice").assertIsDisplayed()
        compose.onNodeWithTag("partner-dashboard").performScrollToNode(hasText(text(R.string.journal_retry)))
        compose.onNodeWithText(text(R.string.journal_retry)).assertHasClickAction().performClick()
        assertEquals(1, retries)
        capture("partner-cached-dark")
        compose.onNodeWithTag("partner-dashboard").performScrollToNode(hasTestTag("prediction-hero"))
        compose.onNodeWithTag("prediction-hero").assertIsDisplayed()
    }

    @Test fun privatePartnerOverviewStillShowsOnlyTheSharingPlaceholder() {
        compose.setContent {
            CompactFrame {
                PartnerDashboard("Sam", PartnerDashboardDto(), false, {}, relationship = RelationshipState(known = true, connected = true))
            }
        }
        compose.onNodeWithText(text(R.string.journal_partner_prediction_empty)).assertIsDisplayed()
        compose.onNodeWithTag("prediction-hero").assertDoesNotExist()
        capture("partner-private-light")
    }

    @Test fun compactRelationshipSummaryExposesDetailsAndRefresh() {
        var refreshes = 0
        compose.setContent {
            CompactFrame(fontScale = 1.6f) {
                RelationshipCard(RelationshipState(known = true, connected = true, peerName = "Alex"), { refreshes++ }, compact = true)
            }
        }
        compose.onNodeWithTag("relationship-summary").assertHeightIsAtLeast(48.dp).assertHasClickAction().performClick()
        compose.onNodeWithText(text(R.string.relationship_sharing_independent)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.relationship_refresh)).performScrollTo().performClick()
        assertEquals(1, refreshes)
    }

    @Composable private fun Tracker(onStart: () -> Unit) = TrackerDashboard(
        displayName = "Alex", periods = emptyList(), dailyLogs = emptyList(), prediction = prediction,
        pendingMutations = 0, conflictMutations = 0, lastSyncAt = "2026-10-28T08:00:00Z",
        onPeriodStarted = onStart, onPeriodEnded = {}, onPeriodUpdated = { _, _, _ -> },
        onPeriodDeleted = {}, onDailyLog = {}, onSync = {}, onDiscardConflicts = {},
        predictionReferenceDate = LocalDate.of(2026, 10, 28),
    )

    @Composable private fun CompactFrame(dark: Boolean = false, fontScale: Float = 1f, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            LunaraTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.width(320.dp).fillMaxSize().testTag("session-polish-frame")) {
                            LunaraWordmark(Modifier.fillMaxWidth().padding(20.dp), compact = true)
                            Box(Modifier.weight(1f)) { content() }
                        }
                    }
                }
            }
        }
    }

    private fun text(resource: Int): String = compose.activity.getString(resource)

    private fun capture(name: String) {
        compose.waitForIdle()
        save(name, compose.onNodeWithTag("session-polish-frame").captureToImage().asAndroidBitmap())
    }

    private fun captureWindow(name: String) {
        compose.waitForIdle()
        save(name, InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: error("Screenshot unavailable"))
    }

    private fun save(name: String, bitmap: Bitmap) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val language = compose.activity.resources.configuration.locales[0].language
        val destination = File(context.getExternalFilesDir(null), "session130-$name-$language.png")
        destination.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
