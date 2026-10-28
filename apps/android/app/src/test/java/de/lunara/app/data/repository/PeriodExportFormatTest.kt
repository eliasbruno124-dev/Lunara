package de.lunara.app.data.repository

import de.lunara.app.core.model.AppSession
import de.lunara.app.core.model.UserRole
import de.lunara.app.data.local.PeriodEntity
import de.lunara.app.data.preferences.AppMode
import de.lunara.app.data.preferences.AppPreferencesState
import java.io.ByteArrayInputStream
import java.io.File
import java.time.LocalDate
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.CancellationException

class PeriodExportFormatTest {
    private val today = LocalDate.of(2026, 10, 28)
    private val closed = PeriodDateRange("2026-09-01", "2026-09-05")
    private val open = PeriodDateRange("2026-10-27", null)
    private val identity = PeriodTransferIdentity("owner", AppMode.SERVER, "https://example.invalid", 3)
    private fun decode(text: String) = PeriodExportFormat.decode(text.toByteArray(Charsets.UTF_8), today)
    private fun problem(expected: PeriodFileProblem, block: () -> Unit) {
        try { block(); fail("Expected $expected") } catch (error: PeriodFileException) { assertEquals(expected, error.problem) }
    }

    @Test fun csvAndJsonRoundTripClosedAndOngoingRangesDeterministically() {
        for (format in PeriodFileFormat.entries) {
            val bytes = PeriodExportFormat.encode(listOf(open, closed), format, today)
            assertEquals(listOf(closed, open), PeriodExportFormat.decode(bytes, today))
            assertArrayEquals(bytes, PeriodExportFormat.encode(listOf(closed, open), format, today))
        }
        assertEquals("startDate,endDate\r\n2026-09-01,2026-09-05\r\n2026-10-27,\r\n", PeriodExportFormat.encode(listOf(open, closed), PeriodFileFormat.CSV, today).toString(Charsets.UTF_8))
    }

    @Test fun jsonWireContractContainsOnlyVersionAndDatesIncludingExplicitNull() {
        val text = PeriodExportFormat.encode(listOf(open), PeriodFileFormat.JSON, today).toString(Charsets.UTF_8)
        assertTrue(text.contains("\"format\": \"lunara-periods\""))
        assertTrue(text.contains("\"formatVersion\": 1"))
        assertTrue(text.contains("\"endDate\": null"))
        for (forbidden in listOf("userId", "source", "syncState", "prediction", "createdAt")) assertFalse(text.contains(forbidden))
    }

    @Test fun exportIncludesPendingConflictAndLocalEditsButNeverForeignOrDeletedRows() {
        fun row(id: String, state: String, owner: String = "owner", deleted: String? = null) = PeriodEntity(id, owner, closed.startDate, closed.endDate, createdAt = "", updatedAt = "", deletedAt = deleted, syncState = state)
        val rows = listOf(row("pending", "PENDING"), row("conflict", "CONFLICT"), row("local", "LOCAL"), row("foreign", "SYNCED", "someone-else"), row("deleted", "LOCAL", deleted = "deleted"))
        assertEquals(listOf(closed, closed, closed), PeriodExportFormat.ownedRanges(rows, identity))
    }

    @Test fun csvSupportsBomCrLfDelimitersQuotedCellsAndAliases() {
        for (delimiter in listOf(',', ';', '\t')) {
            assertEquals(listOf(closed), decode("\uFEFF\"Start_Datum\"${delimiter}\"End-Datum\"${delimiter}Notiz\r\n\"01.09.2026\"${delimiter}\"05.09.2026\"${delimiter}\"frei, mit; Text und \"\"Zitat\"\"\"\r\n"))
        }
        assertEquals(listOf(closed), decode("Périod Start Date;Périod End Date\n2026/09/01;2026/09/05"))
    }

    @Test fun csvAcceptsKnownGermanAndEnglishHeaderPairs() {
        for ((start, end) in listOf("startDate" to "endDate", "start" to "end", "periodStart" to "periodEnd", "periodStartDate" to "periodEndDate", "Beginn" to "Ende", "Periodenbeginn" to "Periodenende", "Von" to "Bis")) {
            assertEquals(listOf(closed), decode("$start,$end\n2026-09-01,2026-09-05"))
        }
    }

    @Test fun csvIgnoresQuotedMultilineMetadataWithoutDerivingSymptoms() {
        assertEquals(listOf(closed), decode("startDate,endDate,note\n2026-09-01,2026-09-05,\"first\nsecond\""))
        problem(PeriodFileProblem.INVALID_FORMAT) { decode("localDate,flowLevel\n2026-09-01,HEAVY") }
    }

    @Test fun missingOrEmptyEndDateIsOngoing() {
        for (text in listOf("startDate\n2026-10-27", "startDate,endDate\n2026-10-27,", "startDate\tendDate\n2026-10-27\t", "[{\"startDate\":\"2026-10-27\"}]", "[{\"startDate\":\"2026-10-27\",\"endDate\":null}]")) assertEquals(listOf(open), decode(text))
    }

    @Test fun utf16FilesRequireBomAndQuotedMetadataDoesNotChooseDelimiter() {
        val text = "\uFEFFstartDate;endDate;\"metadata,with,many,commas\"\r\n2026-09-01;2026-09-05;ignored"
        for (charset in listOf(Charsets.UTF_16LE, Charsets.UTF_16BE)) assertEquals(listOf(closed), PeriodExportFormat.decode(text.toByteArray(charset), today))
    }

    @Test fun jsonAcceptsArrayWrapperLegacyBackupAndNestedDataButIgnoresMetadata() {
        val row = "{\"id\":\"foreign-id\",\"userId\":\"foreign-owner\",\"start_date\":\"2026-09-01\",\"end_date\":\"2026-09-05\"}"
        for (text in listOf("[$row]", "\t{\"periods\":[$row]}", "{\"product\":\"Lunara\",\"schemaVersion\":1,\"periods\":[$row],\"dailyLogs\":[]}", "{\"data\":{\"periods\":[$row]}}")) assertEquals(listOf(closed), decode(text))
    }

    @Test fun datesNormalizeGermanYearFirstAndUnambiguousSlashForms() {
        for (value in listOf("2026-09-23", "2026/09/23", "23.09.2026", "23.9.2026", "23/09/2026", "09/23/2026", "23/9/2026", "9/23/2026")) assertEquals("2026-09-23", decode("startDate\n$value").single().startDate)
        for (value in listOf("04/05/2025", "4/5/2025")) problem(PeriodFileProblem.AMBIGUOUS_DATE) { decode("startDate\n$value") }
        assertEquals("2025-12-12", decode("startDate\n12/12/2025").single().startDate)
    }

    @Test fun isoTimestampsUseWrittenCalendarDateNotTimezoneConvertedDate() {
        assertEquals(listOf(closed), decode("startDate,endDate\n2026-09-01T00:30:00+14:00,2026-09-05T23:30:00.000Z"))
        problem(PeriodFileProblem.INVALID_DATE) { decode("startDate\n2026-09-01T99:30:00Z") }
        problem(PeriodFileProblem.INVALID_DATE) { decode("startDate\n2026-09-01Tanything") }
    }

    @Test fun invalidFutureReversedAndNonLeapDatesRejectEntireFile() {
        for (row in listOf("2025-02-29,", "2026-13-01,", "2026-10-29,", "2026-09-05,2026-09-01", "2026-09-01,2026-10-29", "=1+1,", "2026-9-1,")) {
            problem(PeriodFileProblem.INVALID_DATE) { decode("startDate,endDate\n2026-08-01,2026-08-02\n$row") }
        }
        assertEquals("2024-02-29", decode("startDate\n2024-02-29").single().startDate)
    }

    @Test fun malformedCsvAndAmbiguousHeadersAreRejected() {
        for (text in listOf("startDate,endDate\n\"2026-09-01,2026-09-05", "startDate,endDate\n\"2026-09-01\"oops,", "startDate,start_date\n2026-09-01,2026-09-05", "startDate,endDate\n2026-09-01,2026-09-05,extra")) problem(PeriodFileProblem.INVALID_FORMAT) { decode(text) }
    }

    @Test fun invalidJsonSchemasAndTypesFailClosed() {
        for (text in listOf("{}", "{'periods':[]}", "{\"periods\":[]}{}", "{\"periods\":null}", "{\"periods\":[1]}", "{\"format\":\"lunara-periods\",\"formatVersion\":2,\"periods\":[]}", "{\"prediction\":{\"startDate\":\"2026-01-01\"}}", "[{\"startDate\":\"2026-01-01\",\"start\":\"2026-01-02\"}]")) problem(PeriodFileProblem.INVALID_FORMAT) { decode(text) }
        for (text in listOf("[{\"startDate\":123}]", "[{\"startDate\":null}]", "[{\"startDate\":\"2026-01-01\",\"endDate\":{}}]")) problem(PeriodFileProblem.INVALID_DATE) { decode(text) }
    }

    @Test fun byteAndEntryLimitsAreEnforcedWithoutTruncation() {
        problem(PeriodFileProblem.TOO_LARGE) { PeriodExportFormat.decode(ByteArray(PeriodExportFormat.MAX_BYTES + 1), today) }
        problem(PeriodFileProblem.TOO_LARGE) { readBoundedPeriodFile(ByteArrayInputStream(ByteArray(PeriodExportFormat.MAX_BYTES + 1))) }
        assertEquals(PeriodExportFormat.MAX_BYTES, readBoundedPeriodFile(ByteArrayInputStream(ByteArray(PeriodExportFormat.MAX_BYTES))).size)
        val tooMany = "startDate,endDate\n" + "2026-01-01,2026-01-01\n".repeat(5001)
        problem(PeriodFileProblem.TOO_MANY) { decode(tooMany) }
        assertEquals(5000, decode("startDate,endDate\n" + "2026-01-01,2026-01-01\n".repeat(5000)).size)
        problem(PeriodFileProblem.INVALID_FORMAT) { PeriodExportFormat.decode(byteArrayOf(0xc3.toByte(), 0x28), today) }
    }

    @Test fun duplicateJsonKeysAndExcessiveNestingCannotSilentlySelectDates() {
        problem(PeriodFileProblem.INVALID_FORMAT) { decode("[{\"startDate\":\"2026-09-01\",\"startDate\":\"2026-10-01\"}]") }
        problem(PeriodFileProblem.INVALID_FORMAT) { decode("[".repeat(100) + "]".repeat(100)) }
    }

    @Test fun duplicatesAreSkippedWithoutLosingNewRanges() {
        val plan = PeriodExportFormat.plan(listOf(closed, closed, open, open), listOf(closed), today)
        assertEquals(listOf(open), plan.additions)
        assertEquals(PeriodImportSummary(1, 3, 0), plan.summary)
        assertTrue(plan.summary.canImport)
    }

    @Test fun inclusiveOverlapsWithExistingAndWithinFileBlockWholeImport() {
        val plan = PeriodExportFormat.plan(listOf(PeriodDateRange("2026-09-05", "2026-09-08"), open), listOf(closed), today)
        assertEquals(PeriodImportSummary(1, 0, 1), plan.summary)
        assertFalse(plan.summary.canImport)
        val internal = PeriodExportFormat.plan(listOf(closed, PeriodDateRange("2026-09-03", "2026-09-08")), emptyList(), today)
        assertEquals(2, internal.summary.conflictCount)
        assertFalse(internal.summary.canImport)
    }

    @Test fun sameStartDifferentEndIsConflictNotDuplicateAndOpenPeriodExtendsToToday() {
        assertEquals(PeriodImportSummary(0, 0, 1), PeriodExportFormat.plan(listOf(closed.copy(endDate = "2026-09-06")), listOf(closed), today).summary)
        assertEquals(1, PeriodExportFormat.plan(listOf(PeriodDateRange(today.toString(), today.toString())), listOf(open), today).summary.conflictCount)
    }

    @Test fun aLongExistingRangeConflictsWithAllNestedCandidates() {
        val long = PeriodDateRange("2026-01-01", "2026-09-30")
        assertEquals(2, PeriodExportFormat.plan(listOf(closed, PeriodDateRange("2026-02-01", "2026-02-02")), listOf(long), today).summary.conflictCount)
    }

    @Test fun emptyAndDuplicateOnlyImportsCannotBeConfirmed() {
        assertFalse(PeriodExportFormat.plan(emptyList(), emptyList(), today).summary.canImport)
        assertEquals(PeriodImportSummary(0, 1, 0), PeriodExportFormat.plan(listOf(closed), listOf(closed), today).summary)
    }

    @Test fun capturedIdentityRequiresSameOwnerTrackerRoleModeOriginAndGeneration() {
        val session = AppSession("owner", "Synthetic", UserRole.TRACKER, "Example", "1")
        val prefs = AppPreferencesState(baseUrl = identity.baseUrl, session = session)
        assertTrue(identity.matches(prefs, 3))
        assertFalse(identity.matches(prefs, 4))
        assertFalse(identity.matches(prefs.copy(session = null), 3))
        assertFalse(identity.matches(prefs.copy(session = session.copy(userId = "other")), 3))
        assertFalse(identity.matches(prefs.copy(session = session.copy(role = UserRole.PARTNER)), 3))
        assertFalse(identity.matches(prefs.copy(baseUrl = "https://other.invalid"), 3))
        assertFalse(identity.matches(prefs.copy(appMode = AppMode.LOCAL), 3))
        assertTrue(identity.copy(mode = AppMode.LOCAL, baseUrl = null).matches(prefs.copy(appMode = AppMode.LOCAL, baseUrl = null), 3))
    }

    @Test fun sameAccountCredentialRenewalFinishesOldOperationWithoutBlockingNextTransfer() {
        val renewed = identity.copy(generation = identity.generation + 1)
        assertEquals(PeriodTransferCompletion.ACCOUNT_CHANGED, PeriodTransferCompletionPolicy.evaluate(identity, identity, renewed))
        assertEquals(PeriodTransferCompletion.APPLY, PeriodTransferCompletionPolicy.evaluate(renewed, renewed, renewed))
        assertEquals(PeriodTransferCompletion.IGNORE, PeriodTransferCompletionPolicy.evaluate(identity, renewed, renewed))
        assertEquals(PeriodTransferCompletion.IGNORE, PeriodTransferCompletionPolicy.evaluate(identity, identity, renewed.copy(userId = "other")))
        assertEquals(PeriodTransferCompletion.IGNORE, PeriodTransferCompletionPolicy.evaluate(identity, identity, null))
    }

    @Test fun postCommitSchedulingFailureDoesNotMisreportDurableImportButCancellationPropagates() {
        scheduleCommittedPeriodImport { throw IllegalStateException("Synthetic scheduler unavailable") }
        var scheduled = 0
        scheduleCommittedPeriodImport { scheduled++ }
        assertEquals(1, scheduled)
        try { scheduleCommittedPeriodImport { throw CancellationException("Synthetic cancellation") }; fail("Cancellation must propagate") }
        catch (_: CancellationException) { }
    }

    @Test fun serializationDoesNotDependOnDeviceLocale() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(listOf(closed), decode("PERIOD START DATE;PERIOD END DATE\n2026-09-01;2026-09-05"))
            assertTrue(PeriodExportFormat.encode(listOf(closed), PeriodFileFormat.CSV, today).toString(Charsets.UTF_8).contains("2026-09-01"))
        } finally { Locale.setDefault(before) }
    }

    @Test fun transferResourcesExistInBothLanguagesWithMatchingPlaceholders() {
        fun entries(locale: String): Map<String, String> {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$locale/strings.xml"))
            val nodes = document.getElementsByTagName("string")
            return (0 until nodes.length).map { nodes.item(it) }.filter { it.attributes.getNamedItem("name").nodeValue.startsWith("period_transfer_") || it.attributes.getNamedItem("name").nodeValue.startsWith("period_export_") || it.attributes.getNamedItem("name").nodeValue.startsWith("period_import_") }
                .associate { it.attributes.getNamedItem("name").nodeValue to it.textContent }
        }
        val de = entries("values"); val en = entries("values-en")
        assertTrue(de.isNotEmpty()); assertEquals(de.keys, en.keys)
        de.forEach { (key, value) -> assertEquals(key, Regex("%[0-9]+\\$[ds]").findAll(value).map { it.value }.toList(), Regex("%[0-9]+\\$[ds]").findAll(en.getValue(key)).map { it.value }.toList()) }
    }
}
