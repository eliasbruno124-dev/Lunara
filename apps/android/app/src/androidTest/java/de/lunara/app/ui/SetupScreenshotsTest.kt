package de.lunara.app.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import de.lunara.app.R
import de.lunara.app.core.model.AppSession
import de.lunara.app.core.model.UserRole
import de.lunara.app.data.preferences.AppPreferencesState
import de.lunara.app.data.remote.dto.*
import de.lunara.app.data.repository.RelationshipState
import de.lunara.app.ui.main.*
import de.lunara.app.ui.onboarding.*
import de.lunara.app.ui.theme.LunaraTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Documentation captures use only synthetic state and never sign in or contact a server. */
class SetupScreenshotsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun modeSelection() {
        onboarding(OnboardingUiState())
        capture("01-mode-selection")
    }

    @Test fun serverConnection() {
        onboarding(OnboardingUiState(stage = OnboardingStage.SERVER, serverUrl = "https://lunara.example.com"))
        capture("02-server-connection")
    }

    @Test fun trackerSharing() {
        settings(UserRole.TRACKER)
        compose.onNodeWithText(text(R.string.sharing_settings)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.sharing_period_dates)).performScrollTo().assertIsDisplayed()
        capture("03-tracker-sharing")
        compose.onNodeWithText(text(R.string.sharing_fertility)).performScrollTo().assertIsDisplayed()
        capture("03b-tracker-fertility")
    }

    @Test fun partnerSharingStatus() {
        settings(UserRole.PARTNER)
        compose.onNodeWithText(text(R.string.partner_connect)).performScrollTo().performClick()
        compose.onNodeWithTag("settings-screen").performScrollToNode(hasTestTag("partner-period-access"))
        compose.onNodeWithTag("partner-period-access").assertIsDisplayed()
        capture("04-partner-sharing-status")
    }

    private fun onboarding(state: OnboardingUiState) {
        compose.setContent { LunaraTheme { Surface(Modifier.fillMaxSize()) {
            OnboardingScreen(state, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, onSubmit = {})
        } } }
    }

    private fun settings(role: UserRole) {
        val session = AppSession("synthetic-setup", "Alex", role, "Lunara", "1.3.1")
        compose.setContent { LunaraTheme { Surface(Modifier.fillMaxSize()) {
            SettingsScreen(
                session = session, preferences = AppPreferencesState(session = session, baseUrl = "https://lunara.example.com"),
                onPrivacyMode = {}, onUseSystemTheme = {}, onDarkMode = {}, onNotificationPreferences = {},
                onSync = {}, onRenewSession = {},
                sharing = SharingSettingsDto(sharePeriodDates = true, shareFertileWindow = true),
                cycleProfile = CycleProfileDto(fertilityPredictionEnabled = true), carePreferences = null, invitation = null,
                relationship = RelationshipState(known = true, connected = true, peerName = "Sam"), settingsBusy = false, settingsError = false,
                passwordChangeStatus = PasswordChangeStatus.IDLE, oidc = OidcLinkUiState(), backupStatus = LocalBackupStatus.IDLE,
                onCreateInvitation = {}, onAcceptInvitation = {}, onRefreshInvitation = {}, onRevokeInvitation = {}, onUpdateSharing = {},
                onUpdateCarePreferences = {}, onFertilityPrediction = {}, onChangePassword = { _, _, _ -> }, onClearPasswordChangeStatus = {},
                onBeginAuthentikLink = {}, onCompleteAuthentikLink = { _, _ -> }, onClearAuthentikError = {},
                onExportLocalBackup = { _, _ -> }, onImportLocalBackup = { _, _ -> }, onClearLocalBackupStatus = {}, onLogout = {},
                partnerCalendarStatus = CalendarStatusPolicy.partner(PartnerDashboardDto(calendarAccess = PartnerCalendarAccessDto(true, false)), false, false),
            )
        } } }
    }

    private fun text(id: Int) = compose.activity.getString(id)
    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
