package de.lunara.app.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import de.lunara.app.R
import de.lunara.app.core.model.AppSession
import de.lunara.app.core.model.UserRole
import de.lunara.app.data.local.PeriodEntity
import de.lunara.app.data.preferences.AppPreferencesState
import de.lunara.app.data.remote.dto.PartnerDashboardDto
import de.lunara.app.data.remote.dto.PartnerTipDto
import de.lunara.app.data.remote.dto.PredictionDto
import de.lunara.app.data.remote.dto.TipSourceDto
import de.lunara.app.data.repository.RelationshipState
import de.lunara.app.ui.components.LunaraWordmark
import de.lunara.app.ui.main.*
import de.lunara.app.ui.onboarding.OnboardingScreen
import de.lunara.app.ui.onboarding.OnboardingUiState
import de.lunara.app.ui.theme.LunaraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Synthetic fixtures only. Screenshots never use a real account or server. */
class RedesignComposeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val today = LocalDate.now()
    private val periods = (1..6).map { index ->
        PeriodEntity(id = "visual-$index", userId = "visual-tracker", startDate = today.minusDays(index * 29L).toString(), endDate = today.minusDays(index * 29L - 4).toString(), createdAt = "", updatedAt = "")
    }
    private val prediction = PredictionDto(confidence = "MEDIUM", basedOnCycleCount = 5, predictedStartFrom = today.plusDays(2).toString(), predictedStartTo = today.plusDays(6).toString())
    private val tip get() = if (compose.activity.resources.configuration.locales[0].language == "en") {
        PartnerTipDto("visual-tip", "Day one starts a new cycle", "The first day of bleeding counts as cycle day one. The calendar starts again; your familiar small routines can carry on as usual.", "Pick up the period products you have already agreed on during your next shop. Then that supply is topped up.", "PERIOD_DAYS", selectionReason = "SHARED_CONTEXT", source = TipSourceDto("NHS · Menstrual cycle", "https://www.nhs.uk/conditions/periods/fertility-in-the-menstrual-cycle/"))
    } else {
        PartnerTipDto("visual-tip", "Tag eins heißt: neuer Zyklus", "Der erste Blutungstag zählt als erster Zyklustag. Der Kalender beginnt neu; eure vertrauten kleinen Routinen können ganz normal weitergehen.", "Nimm beim nächsten Einkauf die bereits besprochenen Periodenprodukte mit. Dann ist dieser Vorrat wieder aufgefüllt.", "PERIOD_DAYS", selectionReason = "SHARED_CONTEXT", source = TipSourceDto("NHS · Menstrual cycle", "https://www.nhs.uk/conditions/periods/fertility-in-the-menstrual-cycle/"))
    }

    @Test fun trackerLightHasOneReachablePrimaryAction() {
        var started = 0
        compose.setContent { Frame { Tracker { started++ } } }
        compose.onNodeWithTag("tracker-dashboard").performScrollToNode(hasTestTag("period-start"))
        compose.onNodeWithTag("period-start").assertIsDisplayed().performClick()
        assertEquals(1, started)
        compose.onNodeWithTag("tracker-dashboard").performScrollToIndex(0)
        capture("tracker-light")
    }

    @Test fun trackerDarkRetainsVisibleForecast() {
        compose.setContent { Frame(dark = true) { Tracker {} } }
        compose.onNodeWithTag("prediction-hero").assertIsDisplayed()
        capture("tracker-dark")
    }

    @Test fun partnerForecastComesFirstAndSuggestionRemainsReachable() {
        compose.setContent { Frame(partner = true) { PartnerDashboard("Sam", PartnerDashboardDto(prediction = prediction, tip = tip), false, {}, relationship = RelationshipState(known = true, connected = true, peerName = "Alex")) } }
        compose.onNodeWithText(compose.activity.getString(R.string.relationship_with, "Alex")).assertIsDisplayed()
        // The old tip-first layout put this forecast below the fold. Assert the
        // real initial viewport, then verify the suggestion is still reachable.
        compose.onNodeWithTag("prediction-hero").assertIsDisplayed()
        capture("partner-light")
        compose.onNodeWithTag("partner-dashboard").performScrollToNode(hasText(tip.title))
        compose.onNodeWithText(tip.title).assertIsDisplayed()
        capture("partner-tip")
    }

    @Test fun unsharedPredictionKeepsPrivatePlaceholderBeforeSuggestion() {
        compose.setContent { Frame(partner = true) { PartnerDashboard("Sam", PartnerDashboardDto(prediction = null, tip = tip), false, {}, relationship = RelationshipState(known = true, connected = true, peerName = "Alex")) } }
        compose.onNodeWithTag("prediction-hero").assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.journal_partner_prediction_empty)).assertIsDisplayed()
        capture("partner-private")
        compose.onNodeWithTag("partner-dashboard").performScrollToNode(hasText(tip.title))
        compose.onNodeWithText(tip.title).assertIsDisplayed()
    }

    @Test fun calendarRemainsSwipeableInNewTheme() {
        compose.setContent { Frame(selected = 1) { CalendarScreen(periods, UserRole.TRACKER, prediction, emptyList(), { _, _ -> }, { _, _, _ -> }, {}) } }
        compose.onNodeWithTag("calendar-month-pager").performTouchInput { swipeLeft() }
        capture("calendar-light")
    }

    @Test fun historyUsesRecordedValues() {
        compose.setContent { Frame(selected = 2) { HistoryScreen(periods, prediction, { _, _, _ -> }, {}) } }
        compose.onAllNodesWithText(compose.activity.getString(R.string.days_value, 29)).onFirst().assertIsDisplayed()
        capture("history-light")
    }

    @Test fun settingsOpenOneCategoryAndReturn() {
        compose.setContent { Frame(selected = 3) { SettingsFixture() } }
        compose.onNodeWithTag("settings-section-appearance").assertIsDisplayed()
        capture("settings-light")
        compose.onNodeWithTag("settings-section-appearance").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.system_theme)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.journal_settings_back)).performClick()
        compose.onNodeWithTag("settings-section-appearance").assertIsDisplayed()
    }

    @Test fun largeTypeStillReachesPeriodAction() {
        var started = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                Frame(dark = true) { Tracker { started++ } }
            }
        }
        compose.onNodeWithTag("tracker-dashboard").performScrollToNode(hasTestTag("period-start"))
        compose.onNodeWithTag("period-start").assertIsDisplayed().performClick()
        assertEquals(1, started)
        capture("large-type")
    }

    @Test fun largeTypePartnerCanReadWhyATipWasChosen() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                Frame(dark = true, partner = true) {
                    PartnerDashboard("Sam", PartnerDashboardDto(prediction = prediction, tip = tip), false, {}, relationship = RelationshipState(known = true, connected = true, peerName = "Alex"))
                }
            }
        }
        compose.onNodeWithTag("partner-dashboard").performScrollToNode(hasText(compose.activity.getString(R.string.journal_why_this_tip)))
        compose.onNodeWithText(compose.activity.getString(R.string.journal_why_this_tip)).performClick()
        compose.onNodeWithTag("partner-dashboard").performScrollToNode(hasText(compose.activity.getString(R.string.tip_reason_context)))
        compose.onNodeWithText(compose.activity.getString(R.string.tip_reason_context)).assertIsDisplayed()
        capture("partner-large-type")
        val reference = compose.activity.getString(R.string.tip_source_background, "NHS")
        compose.onNodeWithTag("partner-dashboard").performScrollToNode(hasText(reference))
        compose.onNodeWithText(reference).assertIsDisplayed()
    }

    @Test fun onboardingShowsLocalAndServerChoices() {
        compose.setContent {
            LunaraTheme { Surface(Modifier.fillMaxSize().testTag("design-frame")) {
                OnboardingScreen(state = OnboardingUiState(), onLanguage = {}, onContinue = {}, onBack = {}, onServerUrl = {}, onScannedServerUrl = {}, onAllowHttp = {}, onTestConnection = {}, onConfirmServerChange = {}, onCancelServerChange = {}, onAuthMode = {}, onUsername = {}, onPassword = {}, onDisplayName = {}, onBootstrapToken = {}, onInvitationCode = {}, onSubmit = {})
            } }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.use_on_this_device)).performScrollTo().assertIsDisplayed()
        capture("onboarding-light")
    }

    @Composable private fun Tracker(onStart: () -> Unit) = TrackerDashboard("Alex", periods, emptyList(), prediction, 0, 0, "2026-09-26T09:00:00Z", onStart, {}, { _, _, _ -> }, {}, {}, {}, {})

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun Frame(dark: Boolean = false, selected: Int = 0, partner: Boolean = false, content: @Composable () -> Unit) {
        LunaraTheme(darkTheme = dark) {
            Scaffold(Modifier.fillMaxSize().testTag("design-frame"),
                topBar = { TopAppBar(title = { LunaraWordmark(compact = true) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) },
                bottomBar = { NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = androidx.compose.ui.unit.Dp(0f)) {
                    listOf(R.string.today to Icons.Rounded.Home, R.string.calendar to Icons.Rounded.DateRange, (if (partner) R.string.support else R.string.history) to Icons.Rounded.FavoriteBorder, R.string.settings to Icons.Rounded.Settings).forEachIndexed { index, (label, icon) ->
                        NavigationBarItem(selected = index == selected, onClick = {}, icon = { Icon(icon, null) }, label = { Text(stringResource(label)) })
                    }
                } },
            ) { insets -> Surface(Modifier.fillMaxSize().padding(insets), color = MaterialTheme.colorScheme.background) { content() } }
        }
    }

    @Composable private fun SettingsFixture() = SettingsScreen(
        session = AppSession("visual", "Alex", UserRole.TRACKER, "Testserver", "1.2.1"), preferences = AppPreferencesState(),
        onPrivacyMode = {}, onUseSystemTheme = {}, onDarkMode = {}, onNotificationPreferences = {}, onSync = {}, onRenewSession = {},
        sharing = null, cycleProfile = null, carePreferences = null, invitation = null, relationship = RelationshipState(known = true, connected = true, peerName = "Sam"),
        settingsBusy = false, settingsError = false, passwordChangeStatus = PasswordChangeStatus.IDLE, oidc = OidcLinkUiState(), backupStatus = LocalBackupStatus.IDLE,
        onCreateInvitation = {}, onAcceptInvitation = {}, onRefreshInvitation = {}, onRevokeInvitation = {}, onUpdateSharing = {}, onUpdateCarePreferences = {}, onFertilityPrediction = {},
        onChangePassword = { _, _, _ -> }, onClearPasswordChangeStatus = {}, onBeginAuthentikLink = {}, onCompleteAuthentikLink = { _, _ -> }, onClearAuthentikError = {},
        onExportLocalBackup = { _, _ -> }, onImportLocalBackup = { _, _ -> }, onClearLocalBackupStatus = {}, onLogout = {},
    )

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag("design-frame").captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val language = compose.activity.resources.configuration.locales[0].language
        val destination = File(context.getExternalFilesDir(null), "redesign120-$name-$language.png")
        destination.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
