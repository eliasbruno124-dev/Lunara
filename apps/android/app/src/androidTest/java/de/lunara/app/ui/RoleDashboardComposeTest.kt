package de.lunara.app.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import de.lunara.app.data.remote.dto.PartnerDashboardDto
import de.lunara.app.ui.main.PartnerDashboard
import de.lunara.app.ui.main.PasswordChangeDialog
import de.lunara.app.ui.main.PasswordChangeStatus
import de.lunara.app.ui.main.TrackerDashboard
import de.lunara.app.ui.theme.LunaraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RoleDashboardComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun trackerCanRecordPeriodStart() {
        var starts = 0
        compose.setContent {
            LunaraTheme { TrackerDashboard("Alex", emptyList(), emptyList(), null, 0, null, { starts++ }, {}, {}) }
        }
        compose.onNodeWithText("Periode gestartet").assertIsDisplayed().performClick()
        assertEquals(1, starts)
    }

    @Test
    fun trackerCanCreateOfflineDailyLog() {
        var logs = 0
        compose.setContent {
            LunaraTheme { TrackerDashboard("Alex", emptyList(), emptyList(), null, 0, null, {}, { logs++ }, {}) }
        }
        compose.onNodeWithText("Heute eintragen").performClick()
        compose.onNodeWithText("Speichern").assertIsDisplayed().performClick()
        assertEquals(1, logs)
    }

    @Test
    fun partnerSeesPrivacyGuardAndNoPeriodWriteAction() {
        compose.setContent {
            LunaraTheme { PartnerDashboard("Sam", PartnerDashboardDto(), false, {}) }
        }
        compose.onNodeWithText("Es werden ausschließlich freigegebene Informationen angezeigt.").assertIsDisplayed()
        compose.onNodeWithText("Heute ist kein Tipp freigegeben oder verfügbar.").assertIsDisplayed()
        compose.onAllNodesWithText("Frag kurz, ob heute praktische Unterstützung hilfreich wäre.").assertCountEquals(0)
        compose.onAllNodesWithText("Eine konkrete kleine Aufgabe anbieten").assertCountEquals(0)
        compose.onAllNodesWithText("Periode gestartet").assertCountEquals(0)
    }

    @Test
    fun passwordDialogSubmitsThreeLocalSecretFields() {
        var submitted: Triple<String, String, String>? = null
        compose.setContent {
            LunaraTheme {
                PasswordChangeDialog(
                    status = PasswordChangeStatus.IDLE,
                    onSubmit = { current, new, confirmation -> submitted = Triple(current, new, confirmation) },
                    onInputChanged = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("Aktuelles Lunara-Passwort").performTextInput("old-password-123")
        compose.onNodeWithText("Neues Lunara-Passwort").performTextInput("new-password-456")
        compose.onNodeWithText("Neues Passwort wiederholen").performTextInput("new-password-456")
        compose.onNodeWithTag("password-change-submit").performClick()

        assertEquals(Triple("old-password-123", "new-password-456", "new-password-456"), submitted)
    }
}
