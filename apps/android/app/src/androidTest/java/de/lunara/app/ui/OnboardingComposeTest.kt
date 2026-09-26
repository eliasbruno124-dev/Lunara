package de.lunara.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import de.lunara.app.ui.onboarding.OnboardingScreen
import de.lunara.app.ui.onboarding.AuthMode
import de.lunara.app.ui.onboarding.OnboardingStage
import de.lunara.app.ui.onboarding.OnboardingUiState
import de.lunara.app.data.remote.dto.MetaDto
import de.lunara.app.data.remote.dto.OidcEnrollmentDto
import de.lunara.app.data.remote.dto.OidcMetaDto
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
                    onPassword = {}, onDisplayName = {}, onBootstrapToken = {},
                    onInvitationCode = {}, onSubmit = {},
                )
            }
        }

        compose.onNodeWithText("Server oder Partner verbinden").performScrollTo().performClick()
        assertEquals(1, continued)
    }

    @Test
    fun introductionOffersAnExplicitServerlessLocalMode() {
        var localSelected = 0
        compose.setContent {
            LunaraTheme {
                OnboardingScreen(
                    state = OnboardingUiState(),
                    onLanguage = {}, onContinue = {}, onBack = {}, onServerUrl = {},
                    onScannedServerUrl = {}, onAllowHttp = {}, onTestConnection = {},
                    onConfirmServerChange = {}, onCancelServerChange = {}, onAuthMode = {}, onUsername = {},
                    onPassword = {}, onDisplayName = {}, onBootstrapToken = {},
                    onInvitationCode = {}, onSubmit = {}, onLocalMode = { localSelected++ },
                )
            }
        }

        compose.onNodeWithText("Nur auf diesem Gerät").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, localSelected)
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
                    onPassword = {}, onDisplayName = {}, onBootstrapToken = {},
                    onInvitationCode = {}, onSubmit = {},
                )
            }
        }

        compose.onNodeWithText("Server-URL").performTextReplacement("https://lunara.example.de")
        compose.onNodeWithText("Verbindung testen").performScrollTo().performClick()
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
                    onBootstrapToken = {}, onInvitationCode = {}, onSubmit = {},
                )
            }
        }

        compose.onNodeWithText("Server wechseln und Daten löschen").assertIsDisplayed().performClick()
        assertEquals(1, confirmed)
        assertEquals(0, cancelled)
    }

    @Test
    fun registrationIsShownOnlyWhenTheServerEnablesIt() {
        var selected: AuthMode? = null
        compose.setContent {
            LunaraTheme {
                OnboardingScreen(
                    state = OnboardingUiState(
                        stage = OnboardingStage.AUTH,
                        meta = MetaDto("Lunara", "1.0.3", "v1", false, true, "now"),
                    ),
                    onLanguage = {}, onContinue = {}, onBack = {}, onServerUrl = {},
                    onScannedServerUrl = {}, onAllowHttp = {}, onTestConnection = {},
                    onConfirmServerChange = {}, onCancelServerChange = {}, onAuthMode = { selected = it },
                    onUsername = {}, onPassword = {}, onDisplayName = {}, onBootstrapToken = {},
                    onInvitationCode = {}, onSubmit = {},
                )
            }
        }

        compose.onNodeWithText("Neue Beziehung starten").assertIsDisplayed().performClick()
        assertEquals(AuthMode.REGISTER, selected)
    }

    @Test
    fun authentikPrimaryLoginHidesEverySecondIdentityField() {
        compose.setContent {
            LunaraTheme {
                OnboardingScreen(
                    state = OnboardingUiState(
                        stage = OnboardingStage.AUTH,
                        authMode = AuthMode.LOGIN,
                        meta = authentikPrimaryMeta(),
                    ),
                    onLanguage = {}, onContinue = {}, onBack = {}, onServerUrl = {},
                    onScannedServerUrl = {}, onAllowHttp = {}, onTestConnection = {},
                    onConfirmServerChange = {}, onCancelServerChange = {}, onAuthMode = {},
                    onUsername = {}, onPassword = {}, onDisplayName = {}, onBootstrapToken = {},
                    onInvitationCode = {}, onAuthentikLogin = {}, onSubmit = {},
                )
            }
        }

        compose.onNodeWithText("Mit Authentik anmelden").assertIsDisplayed()
        compose.onNodeWithText("Benutzername").assertDoesNotExist()
        compose.onNodeWithText("Passwort").assertDoesNotExist()
        compose.onNodeWithText("Anzeigename").assertDoesNotExist()
        compose.onNodeWithText("Neue Beziehung starten").assertDoesNotExist()
    }

    @Test
    fun authentikPrimaryJoinOffersOnlyInvitationAndProvider() {
        compose.setContent {
            LunaraTheme {
                OnboardingScreen(
                    state = OnboardingUiState(
                        stage = OnboardingStage.AUTH,
                        authMode = AuthMode.JOIN,
                        invitationCode = "ABCDEFGH2345",
                        meta = authentikPrimaryMeta(),
                    ),
                    onLanguage = {}, onContinue = {}, onBack = {}, onServerUrl = {},
                    onScannedServerUrl = {}, onAllowHttp = {}, onTestConnection = {},
                    onConfirmServerChange = {}, onCancelServerChange = {}, onAuthMode = {},
                    onUsername = {}, onPassword = {}, onDisplayName = {}, onBootstrapToken = {},
                    onInvitationCode = {}, onAuthentikLogin = {}, onSubmit = {},
                )
            }
        }

        compose.onNodeWithText("Einladungscode").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Einladung mit Authentik einlösen").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Benutzername").assertDoesNotExist()
        compose.onNodeWithText("Passwort").assertDoesNotExist()
        compose.onNodeWithText("Anzeigename").assertDoesNotExist()
    }

    @Test
    fun setupAndRegistrationNeverAskForAHouseholdName() {
        compose.setContent {
            LunaraTheme {
                OnboardingScreen(
                    state = OnboardingUiState(
                        stage = OnboardingStage.AUTH,
                        authMode = AuthMode.REGISTER,
                        meta = MetaDto("Lunara", "1.0.3", "v1", false, true, "now"),
                    ),
                    onLanguage = {}, onContinue = {}, onBack = {}, onServerUrl = {},
                    onScannedServerUrl = {}, onAllowHttp = {}, onTestConnection = {},
                    onConfirmServerChange = {}, onCancelServerChange = {}, onAuthMode = {},
                    onUsername = {}, onPassword = {}, onDisplayName = {}, onBootstrapToken = {},
                    onInvitationCode = {}, onSubmit = {},
                )
            }
        }

        compose.onNodeWithText("Beziehung starten").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Name des Haushalts").assertDoesNotExist()
    }

    private fun authentikPrimaryMeta() = MetaDto(
        name = "Lunara",
        version = "1.1.2",
        apiVersion = "v1",
        setupRequired = false,
        registrationEnabled = true,
        serverTime = "now",
        gatewayMode = "authentik",
        authenticationMethods = listOf("OIDC"),
        primaryAuthenticationMethod = "OIDC",
        oidc = OidcMetaDto(
            providerName = "Authentik",
            issuer = "https://auth.example/application/o/lunara/",
            enrollment = OidcEnrollmentDto(
                registration = true,
                invitation = true,
                localPasswordRequired = false,
                createIfMissingRequired = true,
            ),
        ),
    )
}
