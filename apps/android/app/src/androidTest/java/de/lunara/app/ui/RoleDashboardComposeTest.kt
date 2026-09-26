package de.lunara.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import de.lunara.app.data.remote.dto.PartnerDashboardDto
import de.lunara.app.data.remote.dto.PartnerTipDto
import de.lunara.app.data.remote.dto.PredictionDto
import de.lunara.app.data.remote.dto.FertilityEstimateDto
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
                    onPeriodEnded = {},
                    onPeriodUpdated = { _, _, _ -> },
                    onPeriodDeleted = {},
                    onDailyLog = {},
                    onSync = {},
                    onDiscardConflicts = {},
                )
            }
        }
        compose.onNodeWithTag("tracker-dashboard").performScrollToNode(hasTestTag("period-start"))
        compose.onNodeWithText("Periode begonnen").assertIsDisplayed().performClick()
        assertEquals(1, starts)
    }

    @Test
    fun trackerPrimaryActionEndsAnOngoingPeriodAndThenConfirmsCompletion() {
        var ended: String? = null
        val start = LocalDate.now().minusDays(2)
        val ongoing = PeriodEntity(
            id = "ongoing",
            userId = "tracker",
            startDate = start.toString(),
            createdAt = "",
            updatedAt = "",
        )
        var visiblePeriods by mutableStateOf(listOf(ongoing))
        compose.setContent {
            LunaraTheme {
                TrackerDashboard(
                    displayName = "Alex",
                    periods = visiblePeriods,
                    dailyLogs = emptyList(),
                    prediction = null,
                    pendingMutations = 1,
                    conflictMutations = 0,
                    lastSyncAt = null,
                    onPeriodStarted = {},
                    onPeriodEnded = { id ->
                        ended = id
                        visiblePeriods = listOf(
                            ongoing.copy(endDate = LocalDate.now().toString(), syncState = "PENDING"),
                        )
                    },
                    onPeriodUpdated = { _, _, _ -> },
                    onPeriodDeleted = {},
                    onDailyLog = {},
                    onSync = {},
                    onDiscardConflicts = {},
                )
            }
        }

        compose.onNodeWithTag("tracker-dashboard").performScrollToNode(hasTestTag("period-end"))
        compose.onNodeWithTag("period-end").assertIsDisplayed().performClick()
        assertEquals("ongoing", ended)
        compose.onNodeWithTag("period-done").assertIsDisplayed().assertIsNotEnabled()
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
                    onPeriodEnded = {},
                    onPeriodUpdated = { _, _, _ -> },
                    onPeriodDeleted = {},
                    onDailyLog = { draft = it },
                    onSync = {},
                    onDiscardConflicts = {},
                )
            }
        }
        compose.onNodeWithTag("tracker-dashboard").performScrollToNode(hasTestTag("today-log"))
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
                    onPeriodEnded = {},
                    onPeriodUpdated = { _, _, _ -> },
                    onPeriodDeleted = {},
                    onDailyLog = { draft = it },
                    onSync = {},
                    onDiscardConflicts = {},
                )
            }
        }

        compose.onNodeWithTag("tracker-dashboard").performScrollToNode(hasTestTag("today-log"))
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
        compose.onNodeWithTag("partner-dashboard").performScrollToNode(hasText("Es werden ausschließlich freigegebene Informationen angezeigt."))
        compose.onNodeWithText("Es werden ausschließlich freigegebene Informationen angezeigt.").assertIsDisplayed()
        compose.onNodeWithTag("partner-dashboard").performScrollToNode(hasText("Heute ist kein Tipp freigegeben oder verfügbar."))
        compose.onNodeWithText("Heute ist kein Tipp freigegeben oder verfügbar.").assertIsDisplayed()
        compose.onAllNodesWithText("Frag kurz, ob heute praktische Unterstützung hilfreich wäre.").assertCountEquals(0)
        compose.onAllNodesWithText("Eine konkrete kleine Aufgabe anbieten").assertCountEquals(0)
        compose.onAllNodesWithText("Periode gestartet").assertCountEquals(0)
        compose.onAllNodesWithText("Geschätztes fruchtbares Fenster").assertCountEquals(0)
    }

    @Test
    fun partnerSeesOnlyServerSharedPhaseFertilityAndContextualTip() {
        compose.setContent {
            LunaraTheme {
                PartnerDashboard(
                    displayName = "Sam",
                    dashboard = PartnerDashboardDto(
                        prediction = PredictionDto(
                            confidence = "MEDIUM",
                            fertileWindowFrom = "2026-09-10",
                            fertileWindowTo = "2026-09-16",
                            fertilityEstimate = FertilityEstimateDto(
                                enabled = true,
                                available = true,
                                estimated = true,
                                reason = "AVAILABLE",
                                basedOnCycleCount = 6,
                                notContraception = true,
                                doesNotConfirmOvulation = true,
                            ),
                        ),
                        phase = "LUTEAL_ESTIMATE",
                        tip = PartnerTipDto(
                            "tip-1",
                            "Erst fragen",
                            "Frag, was heute hilfreich wäre.",
                            "Biete eine kleine Handlung an",
                            "COMMUNICATION",
                            "LUTEAL_ESTIMATE",
                        ),
                    ),
                    unavailable = false,
                    onRefresh = {},
                )
            }
        }

        compose.onNodeWithTag("partner-dashboard").performScrollToNode(hasText("Mögliche aktuelle Zyklusphase"))
        compose.onNodeWithText("Mögliche aktuelle Zyklusphase").assertIsDisplayed()
        compose.onNodeWithText("Lutealphase, geschätzt").assertIsDisplayed()
        compose.onNodeWithTag("partner-dashboard").performScrollToNode(hasText("Geschätztes fruchtbares Fenster"))
        compose.onNodeWithText("Geschätztes fruchtbares Fenster").assertIsDisplayed()
        compose.onNodeWithTag("partner-dashboard").performScrollToNode(hasText("Nur eine grobe kalendarische Schätzung: keine Aussage über einen tatsächlichen Eisprung und nicht zur Verhütung verwenden."))
        compose.onNodeWithText(
            "Nur eine grobe kalendarische Schätzung: keine Aussage über einen tatsächlichen Eisprung und nicht zur Verhütung verwenden.",
        ).assertIsDisplayed()
        compose.onNodeWithTag("partner-dashboard").performScrollToNode(hasText("Warum dieser Tipp?"))
        compose.onNodeWithText("Warum dieser Tipp?").performClick()
        compose.onNodeWithTag("partner-dashboard").performScrollToNode(hasText("Berücksichtigt eine freigegebene Phase"))
        compose.onNodeWithText("Berücksichtigt eine freigegebene Phase").assertIsDisplayed()
    }

    @Test
    fun trackerCanEditAndConfirmDeleteForExistingPeriod() {
        var updated: Triple<String, LocalDate, LocalDate?>? = null
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
                    {},
                    { id, start, end -> updated = Triple(id, start, end) },
                    { id -> deleted = id },
                    {},
                    {},
                    {},
                )
            }
        }

        compose.onNodeWithTag("tracker-dashboard").performScrollToNode(hasTestTag("period-manage-period-1"))
        compose.onNodeWithTag("period-manage-period-1").performClick()
        compose.onNodeWithTag("period-delete").performScrollTo().performClick()
        compose.onNodeWithTag("period-delete-confirm").performClick()

        assertEquals("period-1", deleted)
        assertEquals(null, updated)
    }

    @Test
    fun trackerCanSetAndRemoveAnOptionalPeriodEnd() {
        var updated: Triple<String, LocalDate, LocalDate?>? = null
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
                    {},
                    { id, start, end -> updated = Triple(id, start, end) },
                    {},
                    {},
                    {},
                    {},
                )
            }
        }

        compose.onNodeWithTag("tracker-dashboard").performScrollToNode(hasTestTag("period-manage-period-1"))
        compose.onNodeWithTag("period-manage-period-1").performClick()
        compose.onNodeWithTag("period-range-reset").performScrollTo().performClick()
        compose.onNodeWithTag("period-picker-day-2026-08-11").performScrollTo().performClick()
        compose.onNodeWithTag("period-picker-day-2026-08-12").performScrollTo().performClick()
        compose.onNodeWithTag("period-update-confirm").performClick()

        assertEquals(
            Triple("period-1", LocalDate.parse("2026-08-11"), LocalDate.parse("2026-08-12")),
            updated,
        )
    }

    @Test
    fun trackerCanRemoveAnExistingPeriodEnd() {
        var updated: Triple<String, LocalDate, LocalDate?>? = null
        val period = PeriodEntity(
            "period-1",
            "tracker",
            "2026-08-10",
            endDate = "2026-08-12",
            createdAt = "",
            updatedAt = "",
        )
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
                    {},
                    { id, start, end -> updated = Triple(id, start, end) },
                    {},
                    {},
                    {},
                    {},
                )
            }
        }

        compose.onNodeWithTag("tracker-dashboard").performScrollToNode(hasTestTag("period-manage-period-1"))
        compose.onNodeWithTag("period-manage-period-1").performClick()
        compose.onNodeWithTag("period-end-clear").performScrollTo().performClick()
        compose.onNodeWithTag("period-update-confirm").performClick()

        assertEquals(Triple("period-1", LocalDate.parse("2026-08-10"), null), updated)
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
                    onPeriodEnded = {},
                    onPeriodUpdated = { _, _, _ -> },
                    onPeriodDeleted = {},
                    onDailyLog = {},
                    onSync = {},
                    onDiscardConflicts = { resolutions++ },
                )
            }
        }

        compose.onNodeWithTag("tracker-dashboard").performScrollToNode(hasText("Konflikte auflösen"))
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
