package de.lunara.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import de.lunara.app.ui.onboarding.OnboardingScreen
import de.lunara.app.ui.onboarding.OnboardingStage
import de.lunara.app.ui.onboarding.OnboardingUiState
import de.lunara.app.ui.theme.LunaraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingComposeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun introductionContinuesToServerStep() {
        var continued = 0
        compose.setContent {
            LunaraTheme {
                OnboardingScreen(
                    state = OnboardingUiState(),
                    onLanguage = {}, onContinue = { continued++ }, onBack = {}, onServerUrl = {},
                    onScannedServerUrl = {}, onAllowHttp = {}, onTestConnection = {},
                    onConfirmServerChange = {}, onCancelServerChange = {}, onAuthMode = {}, onUsername = {},
                    onPassword = {}, onDisplayName = {}, onHouseholdName = {}, onBootstrapToken = {},
                    onInvitationCode = {}, onSubmit = {},
                )
            }
        }

        compose.onNodeWithText("Weiter").performClick()
        assertEquals(1, continued)
    }

    @Test
    fun serverAddressCanBeEnteredAndTested() {
        var value = ""
        var tests = 0
        compose.setContent {
            LunaraTheme {
                OnboardingScreen(
                    state = OnboardingUiState(stage = OnboardingStage.SERVER, serverUrl = value),
                    onLanguage = {}, onContinue = {}, onBack = {}, onServerUrl = { value = it },
                    onScannedServerUrl = {}, onAllowHttp = {}, onTestConnection = { tests++ },
                    onConfirmServerChange = {}, onCancelServerChange = {}, onAuthMode = {}, onUsername = {},
                    onPassword = {}, onDisplayName = {}, onHouseholdName = {}, onBootstrapToken = {},
                    onInvitationCode = {}, onSubmit = {},
                )
            }
        }

        compose.onNodeWithText("Server-URL").performTextReplacement("https://lunara.example.de")
        compose.onNodeWithText("Verbindung testen").performClick()
        assertEquals("https://lunara.example.de", value)
        assertEquals(1, tests)
    }

    @Test
    fun serverChangeRequiresExplicitConfirmation() {
        var confirmed = 0
        var cancelled = 0
        compose.setContent {
            LunaraTheme {
                OnboardingScreen(
                    state = OnboardingUiState(
                        stage = OnboardingStage.SERVER,
                        serverChangeConfirmationRequired = true,
                    ),
                    onLanguage = {}, onContinue = {}, onBack = {}, onServerUrl = {},
                    onScannedServerUrl = {}, onAllowHttp = {}, onTestConnection = {},
                    onConfirmServerChange = { confirmed++ }, onCancelServerChange = { cancelled++ },
                    onAuthMode = {}, onUsername = {}, onPassword = {}, onDisplayName = {},
                    onHouseholdName = {}, onBootstrapToken = {}, onInvitationCode = {}, onSubmit = {},
                )
            }
        }

        compose.onNodeWithText("Server wechseln und Daten löschen").assertIsDisplayed().performClick()
        assertEquals(1, confirmed)
        assertEquals(0, cancelled)
    }
}
