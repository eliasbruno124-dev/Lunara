package de.lunara.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import de.lunara.app.data.preferences.AppPreferencesState
import de.lunara.app.data.remote.dto.PartnerDashboardDto
import de.lunara.app.data.remote.dto.PartnerTipDto
import de.lunara.app.data.remote.dto.PredictionDto
import de.lunara.app.data.repository.RelationshipState
import de.lunara.app.ui.main.DonationButton
import de.lunara.app.ui.main.MainUiState
import de.lunara.app.ui.main.PartnerDashboard
import de.lunara.app.ui.main.PredictionExplanation
import de.lunara.app.ui.main.RelationshipCard
import de.lunara.app.ui.main.PersonalTipsEditor
import de.lunara.app.ui.main.PersonalTipsState
import de.lunara.app.ui.main.SyncStatusAction
import de.lunara.app.ui.theme.LunaraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class ConnectionQualityComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unknownIsNotPresentedAsDisconnected() {
        compose.setContent { LunaraTheme { Surface { RelationshipCard(RelationshipState(unavailable = true), {}) } } }
        compose.onNodeWithText("Der Verbindungsstatus konnte noch nicht geladen werden. Das bedeutet nicht, dass ihr getrennt seid.").assertIsDisplayed()
        compose.onNodeWithText("Noch kein Partnerkonto verbunden").assertDoesNotExist()
    }

    @Test fun personalWishIsExplicitAndPreservesOwnWords() {
        var saved: de.lunara.app.data.remote.dto.CreatePersonalTipRequest? = null
        compose.setContent { LunaraTheme { Surface { PersonalTipsEditor(PersonalTipsState(userId = "fixture"), { saved = it }, {}, {}) } } }
        compose.onNodeWithText("Eigenen Wunsch hinzufügen").performClick()
        compose.onNodeWithText("Kurzer Titel").performTextInput("Mein Tee")
        compose.onNodeWithText("Wann oder warum hilft es mir?").performTextInput("Wenn ich Ruhe brauche.")
        compose.onNodeWithText("Was kann mein Partner konkret tun?").performTextInput("Bitte bring mir meinen Lieblingstee.")
        compose.onNodeWithText("Für Partner freigeben").performClick()
        assertEquals("Bitte bring mir meinen Lieblingstee.", saved?.callToActionDe)
        assertEquals(saved?.callToActionDe, saved?.callToActionEn)
    }

    @Test fun failedRefreshKeepsPreviouslyConfirmedPartnerVisible() {
        compose.setContent { LunaraTheme { Surface { RelationshipCard(RelationshipState(known = true, connected = true, peerName = "Alex", checkedAt = "2026-09-21T12:00:00Z", unavailable = true), {}) } } }
        compose.onNodeWithText("Mit Alex verbunden").assertIsDisplayed()
        compose.onNodeWithText("Aktuell nicht erneut geprüft.", substring = true).assertIsDisplayed()
        screenshot("relationship-cached")
    }

    @Test fun syncChipDescribesModeAndLastSuccessfulRequest() {
        compose.setContent { LunaraTheme { Surface { SyncStatusAction(AppPreferencesState(), MainUiState(lastSyncAt = "2026-09-21T12:00:00Z"), {}, {}) } } }
        compose.onNodeWithText("Server-Sync").performClick()
        compose.onNodeWithText("nicht eine bestätigte Internetverbindung", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Jetzt synchronisieren").assertIsDisplayed()
        screenshot("sync-details")
    }

    @Test fun donationRequiresAnExplicitExternalNavigationChoice() {
        compose.setContent { LunaraTheme { Surface { DonationButton() } } }
        compose.onNodeWithText("PayPal öffnen").assertDoesNotExist()
        compose.onNodeWithText("Lunara über PayPal unterstützen").performClick()
        compose.onNodeWithText("https://paypal.me/eliasbruno123", substring = true).assertIsDisplayed()
        compose.onNodeWithText("PayPal öffnen").assertIsDisplayed()
        compose.onNodeWithText("Abbrechen").performClick()
        compose.onNodeWithText("PayPal öffnen").assertDoesNotExist()
    }

    @Test fun eightDayStartWindowIsClearlyNotBleedingDuration() {
        compose.setContent { LunaraTheme { Surface { PredictionExplanation(PredictionDto(confidence = "LOW", basedOnCycleCount = 2, predictedStartFrom = "2026-09-09", predictedStartTo = "2026-09-16")) } } }
        compose.onNodeWithText("Möglicher Beginn innerhalb von 8 Tagen", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Nur 2 vollständige Zyklen", substring = true).assertIsDisplayed()
    }

    @Test fun partnerCanBrowseSuggestionsWithoutUnlinkingOrDismissing() {
        var next = 0
        compose.setContent {
            LunaraTheme {
                Surface {
                    Column {
                        SyncStatusAction(AppPreferencesState(), MainUiState(), {}, {})
                        PartnerDashboard("Sam", PartnerDashboardDto(
                            prediction = PredictionDto(confidence = "LOW", basedOnCycleCount = 2, predictedStartFrom = "2026-10-09", predictedStartTo = "2026-10-17"),
                            tip = PartnerTipDto("fixture", "Eine Frage, zwei Möglichkeiten", "Ein konkretes Angebot lässt sich leichter beantworten als „Sag einfach Bescheid“.", "„Soll ich das Essen übernehmen, oder möchtest du lieber etwas Zeit für dich?“", "COMMUNICATION"),
                        ), false, {}, relationship = RelationshipState(known = true, connected = true, peerName = "Alex"), onNextTip = { next++ })
                    }
                }
            }
        }
        compose.onNodeWithText("Mit Alex verbunden").assertIsDisplayed()
        screenshot("partner-top")
        compose.onNodeWithTag("partner-dashboard").performScrollToNode(hasText("Anderer Vorschlag"))
        compose.onNodeWithText("Anderer Vorschlag").assertIsDisplayed().performClick()
        assertEquals(1, next)
        screenshot("partner-tip")
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val destination = File(instrumentation.targetContext.getExternalFilesDir(null), "quality116-$name.png")
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        destination.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
