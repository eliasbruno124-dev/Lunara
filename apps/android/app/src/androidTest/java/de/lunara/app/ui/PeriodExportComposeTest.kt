package de.lunara.app.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.JsonParser
import de.lunara.app.R
import de.lunara.app.core.model.UserRole
import de.lunara.app.data.local.LunaraDatabase
import de.lunara.app.data.preferences.AppMode
import de.lunara.app.data.repository.PeriodDateRange
import de.lunara.app.data.repository.PeriodExportFormat
import de.lunara.app.data.repository.PeriodFileFormat
import de.lunara.app.data.repository.PeriodFileException
import de.lunara.app.data.repository.PeriodFileProblem
import de.lunara.app.data.repository.PeriodImportDraft
import de.lunara.app.data.repository.PeriodImportSummary
import de.lunara.app.data.repository.PeriodTransferIdentity
import de.lunara.app.data.repository.commitPeriodImport
import de.lunara.app.ui.main.PeriodTransferControls
import de.lunara.app.ui.main.PeriodTransferStatus
import de.lunara.app.ui.main.PeriodTransferUiState
import de.lunara.app.ui.theme.LunaraTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic controls; no actual document picker, account, or external file. */
class PeriodExportComposeTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun csvExportRequiresPlaintextWarningConfirmation() {
        var selected: PeriodFileFormat? = null
        compose.setContent { LunaraTheme { PeriodTransferControls(UserRole.TRACKER, PeriodTransferUiState(), { selected = it }, {}, {}, {}) } }
        compose.onNodeWithTag("period-export-csv").performClick()
        compose.onNodeWithTag("period-export-warning").assertIsDisplayed()
        compose.runOnIdle { assertNull(selected) }
        compose.onNodeWithTag("period-export-confirm").performClick()
        compose.runOnIdle { assertEquals(PeriodFileFormat.CSV, selected) }
    }

    @Test fun cancelledJsonExportNeverLaunchesWrite() {
        var exports = 0
        compose.setContent { LunaraTheme { PeriodTransferControls(UserRole.TRACKER, PeriodTransferUiState(), { exports++ }, {}, {}, {}) } }
        compose.onNodeWithTag("period-export-json").performClick()
        compose.onNodeWithText(text(R.string.cancel)).performClick()
        compose.runOnIdle { assertEquals(0, exports) }
    }

    @Test fun importSelectionDoesNotConfirmUntilPreviewReviewed() {
        var selections = 0
        var confirmed = 0
        val state = mutableStateOf(PeriodTransferUiState())
        compose.setContent { LunaraTheme { PeriodTransferControls(UserRole.TRACKER, state.value, {}, { selections++ }, {
            confirmed++; state.value = state.value.copy(status = PeriodTransferStatus.BUSY)
        }, {}) } }
        compose.onNodeWithTag("period-import-file").performClick()
        compose.onNodeWithTag("period-import-choose").performClick()
        compose.runOnIdle {
            assertEquals(1, selections); assertEquals(0, confirmed)
            state.value = PeriodTransferUiState(PeriodTransferStatus.PREVIEW, PeriodImportSummary(2, 1, 0))
        }
        compose.onNodeWithTag("period-import-preview").assertIsDisplayed()
        compose.onNodeWithTag("period-import-confirm").performClick()
        compose.onNodeWithTag("period-import-confirm").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, confirmed) }
    }

    @Test fun overlapPreviewCannotBeImported() {
        compose.setContent { LunaraTheme { PeriodTransferControls(UserRole.TRACKER,
            PeriodTransferUiState(PeriodTransferStatus.PREVIEW, PeriodImportSummary(1, 0, 2)), {}, {}, { error("Conflict must not import") }, {}) } }
        compose.onNodeWithTag("period-import-confirm").assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.period_import_conflicts)).assertIsDisplayed()
    }

    @Test fun busyTransferDisablesNewPickersAndPartnersHaveNoControls() {
        val role = mutableStateOf(UserRole.TRACKER)
        compose.setContent { LunaraTheme { PeriodTransferControls(role.value, PeriodTransferUiState(PeriodTransferStatus.BUSY), {}, {}, {}, {}) } }
        compose.onNodeWithTag("period-export-csv").assertIsNotEnabled()
        compose.onNodeWithTag("period-export-json").assertIsNotEnabled()
        compose.onNodeWithTag("period-import-file").assertIsNotEnabled()
        compose.runOnIdle { role.value = UserRole.PARTNER }
        compose.onNodeWithTag("period-transfer-controls").assertDoesNotExist()
    }

    @Test fun ambiguousDateFailureGivesExplicitIsoConversionGuidance() {
        compose.setContent { LunaraTheme { PeriodTransferControls(UserRole.TRACKER,
            PeriodTransferUiState(PeriodTransferStatus.FAILED, problem = PeriodFileProblem.AMBIGUOUS_DATE), {}, {}, {}, {}) } }
        compose.onNodeWithText(text(R.string.period_import_ambiguous_date)).assertIsDisplayed()
    }
}

/** Tests the exact transaction core called under repository identity/sync guards, in memory. */
class PeriodImportAtomicDeviceTest {
    private lateinit var database: LunaraDatabase
    private val ranges = listOf(PeriodDateRange("2025-01-02", "2025-01-05"), PeriodDateRange("2025-02-02", null))
    @Before fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LunaraDatabase::class.java).build()
    }
    @After fun closeDatabase() = database.close()
    private fun draft(mode: AppMode, entries: List<PeriodDateRange> = ranges, existing: List<PeriodDateRange> = emptyList()) =
        PeriodImportDraft(PeriodTransferIdentity("synthetic-owner", mode, null, 0), entries, PeriodExportFormat.plan(entries, existing))

    @Test fun localImportAddsOnlyLocalRowsAndRepeatSkipsDuplicates() = runBlocking {
        val initial = draft(AppMode.LOCAL)
        assertEquals(2, commitPeriodImport(database, Gson(), initial) {})
        val stored = database.periodDao().activeForUser("synthetic-owner")
        assertTrue(stored.all { it.syncState == "LOCAL" && it.source == "IMPORT" && it.version == 0L })
        assertEquals(0, database.mutationDao().observeCount().first())
        assertEquals(0, commitPeriodImport(database, Gson(), draft(AppMode.LOCAL, existing = ranges)) {})
        assertEquals(stored, database.periodDao().activeForUser("synthetic-owner"))
    }

    @Test fun serverImportAtomicallyQueuesOneCreatePerNewPeriod() = runBlocking {
        assertEquals(2, commitPeriodImport(database, Gson(), draft(AppMode.SERVER)) {})
        val stored = database.periodDao().activeForUser("synthetic-owner")
        assertEquals(2, database.mutationDao().observeCount().first())
        for (period in stored) {
            assertEquals("PENDING", period.syncState)
            val mutation = database.mutationDao().forEntity("PERIOD", period.id).single()
            assertEquals(0L, mutation.baseVersion); assertEquals("UPSERT", mutation.operation)
            val payload = JsonParser.parseString(mutation.payloadJson).asJsonObject
            assertEquals("IMPORT", payload["source"].asString)
            assertEquals(period.startDate, payload["startDate"].asString)
            if (period.endDate == null) assertTrue(payload["endDate"].isJsonNull)
        }
    }

    @Test fun overlapOrChangedPreviewAddsNothingAndPreservesExistingRows() = runBlocking {
        commitPeriodImport(database, Gson(), draft(AppMode.LOCAL)) {}
        val before = database.periodDao().activeForUser("synthetic-owner")
        val overlap = listOf(PeriodDateRange("2025-01-05", "2025-01-08"))
        for (invalid in listOf(draft(AppMode.LOCAL, overlap, ranges), draft(AppMode.LOCAL))) {
            try { commitPeriodImport(database, Gson(), invalid) {}; fail("Expected stale/conflicting preview") }
            catch (error: PeriodFileException) { assertEquals(PeriodFileProblem.CHANGED, error.problem) }
            assertEquals(before, database.periodDao().activeForUser("synthetic-owner"))
            assertEquals(0, database.mutationDao().observeCount().first())
        }
    }

    @Test fun lateMutationCollisionRollsBackEarlierInsertAndQueue() = runBlocking {
        val ids = listOf("period-a", "mutation-a", "client-same", "period-b", "mutation-b", "client-same").iterator()
        try { commitPeriodImport(database, Gson(), draft(AppMode.SERVER), newId = { ids.next() }) {}; fail("Expected collision") }
        catch (_: IllegalStateException) { }
        assertTrue(database.periodDao().activeForUser("synthetic-owner").isEmpty())
        assertEquals(0, database.mutationDao().observeCount().first())
    }

    @Test fun accountChangeDuringBatchRollsBackAlreadyInsertedRows() = runBlocking {
        for (failAt in listOf(3, 4)) {
            var checks = 0
            try { commitPeriodImport(database, Gson(), draft(AppMode.SERVER)) {
                if (++checks == failAt) throw PeriodFileException(PeriodFileProblem.ACCOUNT_CHANGED)
            }; fail("Expected identity change") }
            catch (error: PeriodFileException) { assertEquals(PeriodFileProblem.ACCOUNT_CHANGED, error.problem) }
            assertTrue(database.periodDao().activeForUser("synthetic-owner").isEmpty())
            assertEquals(0, database.mutationDao().observeCount().first())
        }
    }
}
