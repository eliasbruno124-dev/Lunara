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
import de.lunara.app.data.local.PeriodEntity
import de.lunara.app.data.local.DailyLogEntity
import de.lunara.app.ui.main.PartnerDashboard
import de.lunara.app.ui.main.PasswordChangeDialog
import de.lunara.app.ui.main.PasswordChangeStatus
import de.lunara.app.ui.main.TrackerDashboard
import de.lunara.app.ui.main.TodayLogDraft
import de.lunara.app.ui.theme.LunaraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class RoleDashboardComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun trackerCanRecordPeriodStart() {
        var starts = 0
        compose.setContent {
            LunaraTheme {
                TrackerDashboard(
                    displayName = "Alex",
                    periods = emptyList(),
                    dailyLogs = emptyList(),
                    prediction = null,
                    pendingMutations = 0,
                    conflictMutations = 0,
                    lastSyncAt = null,
                    onPeriodStarted = { starts++ },
                    onPeriodUpdated = { _, _ -> },
                    onPeriodDeleted = {},
                    onDailyLog = {},
                    onSync = {},
                    onDiscardConflicts = {},
                )
            }
        }
        compose.onNodeWithText("Periode gestartet").assertIsDisplayed().performClick()
        assertEquals(1, starts)
    }

    @Test
    fun trackerCanCreateOfflineDailyLog() {
        var draft: TodayLogDraft? = null
        compose.setContent {
            LunaraTheme {
                TrackerDashboard(
                    displayName = "Alex",
                    periods = emptyList(),
                    dailyLogs = emptyList(),
                    prediction = null,
                    pendingMutations = 0,
                    conflictMutations = 0,
                    lastSyncAt = null,
                    onPeriodStarted = {},
                    onPeriodUpdated = { _, _ -> },
                    onPeriodDeleted = {},
                    onDailyLog = { draft = it },
                    onSync = {},
                    onDiscardConflicts = {},
                )
            }
        }
        compose.onNodeWithText("Heute eintragen").performClick()
        compose.onNodeWithText("Speichern").assertIsDisplayed().performClick()
        assertEquals(TodayLogDraft(null, null, null, null, null, emptyList()), draft)
    }

    @Test
    fun existingDailyLogIsPreservedWhenSavedWithoutChanges() {
        var draft: TodayLogDraft? = null
        val existing = DailyLogEntity(
            id = "daily-1",
            userId = "tracker",
            localDate = LocalDate.now().toString(),
            flowLevel = "HEAVY",
            painLevel = 7,
            mood = "Okay",
            energy = 6,
            sleepQuality = 8,
            symptomsJson = "[\"cramps\",\"stress\"]",
            createdAt = "",
            updatedAt = "",
        )
        compose.setContent {
            LunaraTheme {
                TrackerDashboard(
                    displayName = "Alex",
                    periods = emptyList(),
                    dailyLogs = listOf(existing),
                    prediction = null,
                    pendingMutations = 0,
                    conflictMutations = 0,
                    lastSyncAt = null,
                    onPeriodStarted = {},
                    onPeriodUpdated = { _, _ -> },
                    onPeriodDeleted = {},
                    onDailyLog = { draft = it },
                    onSync = {},
                    onDiscardConflicts = {},
                )
            }
        }

        compose.onNodeWithTag("today-log").performClick()
        compose.onNodeWithTag("today-log-save").performClick()

        assertEquals(
            TodayLogDraft("HEAVY", 7, "Okay", 6, 8, listOf("cramps", "stress")),
            draft,
        )
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
    fun trackerCanEditAndConfirmDeleteForExistingPeriod() {
        var updated: Pair<String, LocalDate>? = null
        var deleted: String? = null
        val period = PeriodEntity("period-1", "tracker", "2026-08-10", createdAt = "", updatedAt = "")
        compose.setContent {
            LunaraTheme {
                TrackerDashboard(
                    "Alex",
                    listOf(period),
                    emptyList(),
                    null,
                    0,
                    0,
                    null,
                    {},
                    { id, date -> updated = id to date },
                    { id -> deleted = id },
                    {},
                    {},
                    {},
                )
            }
        }

        compose.onNodeWithTag("period-manage-period-1").performClick()
        compose.onNodeWithTag("period-delete").performClick()
        compose.onNodeWithTag("period-delete-confirm").performClick()

        assertEquals("period-1", deleted)
        assertEquals(null, updated)
    }

    @Test
    fun conflictResolutionRequiresConfirmation() {
        var resolutions = 0
        compose.setContent {
            LunaraTheme {
                TrackerDashboard(
                    displayName = "Alex",
                    periods = emptyList(),
                    dailyLogs = emptyList(),
                    prediction = null,
                    pendingMutations = 1,
                    conflictMutations = 1,
                    lastSyncAt = null,
                    onPeriodStarted = {},
                    onPeriodUpdated = { _, _ -> },
                    onPeriodDeleted = {},
                    onDailyLog = {},
                    onSync = {},
                    onDiscardConflicts = { resolutions++ },
                )
            }
        }

        compose.onNodeWithText("Konflikte auflösen").performClick()
        assertEquals(0, resolutions)
        compose.onNodeWithText("Serverfassung verwenden").performClick()
        assertEquals(1, resolutions)
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
