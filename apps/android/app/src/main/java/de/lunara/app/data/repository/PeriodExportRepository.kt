package de.lunara.app.data.repository

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lunara.app.data.local.LunaraDatabase
import de.lunara.app.data.preferences.AppPreferences
import de.lunara.app.data.sync.ProcessSyncGate
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class PeriodImportDraft(
    val identity: PeriodTransferIdentity,
    val ranges: List<PeriodDateRange>,
    val plan: PeriodImportPlan,
)

@Singleton
class PeriodExportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: LunaraDatabase,
    private val preferences: AppPreferences,
    private val credentialGate: SessionCredentialGate,
) {
    suspend fun exportTo(uri: Uri, format: PeriodFileFormat, identity: PeriodTransferIdentity, today: LocalDate = LocalDate.now()): Int = withContext(Dispatchers.IO) {
        val snapshot = ProcessSyncGate.serialized {
            var payload: Pair<ByteArray, Int>? = null
            val committed = credentialGate.commitIfCurrent(identity.generation) {
                requireIdentity(identity)
                val ranges = PeriodExportFormat.ownedRanges(database.periodDao().activeForUser(identity.userId), identity)
                val bytes = PeriodExportFormat.encode(ranges, format, today)
                requireIdentity(identity)
                payload = bytes to ranges.size
                true
            }
            if (!committed) throw PeriodFileException(PeriodFileProblem.ACCOUNT_CHANGED)
            checkNotNull(payload)
        }
        // External providers may block. Never hold account/wipe or sync locks across SAF I/O.
        // The immutable snapshot can contain only the captured owner's data, never a new login.
        val bytes = snapshot.first
        requireIdentity(identity)
        currentCoroutineContext().ensureActive()
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            var offset = 0
            while (offset < bytes.size) {
                currentCoroutineContext().ensureActive()
                requireIdentity(identity)
                val length = minOf(8192, bytes.size - offset)
                output.write(bytes, offset, length)
                offset += length
            }
            requireIdentity(identity)
            output.flush()
        } ?: error("Document unavailable")
        requireIdentity(identity)
        snapshot.second
    }

    suspend fun preview(uri: Uri, identity: PeriodTransferIdentity, today: LocalDate = LocalDate.now()): PeriodImportDraft = withContext(Dispatchers.IO) {
        requireIdentity(identity)
        val bytes = context.contentResolver.openInputStream(uri)?.use(::readBoundedPeriodFile)
            ?: error("Document unavailable")
        val ranges = PeriodExportFormat.decode(bytes, today)
        currentCoroutineContext().ensureActive()
        ProcessSyncGate.serialized {
            requireIdentity(identity)
            val existing = PeriodExportFormat.ownedRanges(database.periodDao().activeForUser(identity.userId), identity)
            val plan = PeriodExportFormat.plan(ranges, existing, today)
            requireIdentity(identity)
            PeriodImportDraft(identity, ranges, plan)
        }
    }

    private suspend fun requireIdentity(identity: PeriodTransferIdentity) {
        if (!identity.matches(preferences.state.first(), credentialGate.snapshot())) throw PeriodFileException(PeriodFileProblem.ACCOUNT_CHANGED)
    }
}

internal fun readBoundedPeriodFile(input: InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val read = input.read(buffer)
        if (read == -1) break
        if (output.size() + read > PeriodExportFormat.MAX_BYTES) throw PeriodFileException(PeriodFileProblem.TOO_LARGE)
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
