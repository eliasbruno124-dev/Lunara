package de.lunara.app.data.repository

import androidx.room.withTransaction
import com.google.gson.Gson
import de.lunara.app.core.model.UserRole
import de.lunara.app.data.local.LunaraDatabase
import de.lunara.app.data.local.MutationEntity
import de.lunara.app.data.local.PeriodEntity
import de.lunara.app.data.local.DailyLogEntity
import de.lunara.app.data.sync.SyncScheduler
import de.lunara.app.data.sync.ProcessSyncGate
import de.lunara.app.data.preferences.AppPreferences
import de.lunara.app.data.preferences.AppMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatterBuilder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PeriodRepository @Inject constructor(
    private val database: LunaraDatabase,
    private val syncScheduler: SyncScheduler,
    private val gson: Gson,
    private val preferences: AppPreferences,
    private val credentialGate: SessionCredentialGate,
) {
    fun observePeriods(): Flow<List<PeriodEntity>> = database.periodDao().observeActive()
    fun observePendingCount(): Flow<Int> = combine(
        database.mutationDao().observeCount(),
        database.periodDao().observeResolvingCount(),
        database.dailyLogDao().observeResolvingCount(),
        preferences.state,
    ) { mutations, resolvingPeriods, resolvingLogs, prefs ->
        if (prefs.appMode == AppMode.LOCAL) 0 else mutations + resolvingPeriods + resolvingLogs
    }
    fun observeConflictCount(): Flow<Int> = combine(
        database.mutationDao().observeConflictCount(),
        preferences.state,
    ) { conflicts, prefs -> if (prefs.appMode == AppMode.LOCAL) 0 else conflicts }
    fun observeDailyLogs(): Flow<List<DailyLogEntity>> = database.dailyLogDao().observeActive()

    suspend fun retryFailedMutations() = ProcessSyncGate.serialized {
        if (preferences.state.first().appMode == AppMode.LOCAL) return@serialized
        val now = MutationTimestampPolicy.format(Instant.now())
        database.withTransaction {
            database.mutationDao().resetFailed(now)
            // A deliberate “sync now” bypasses queue delay but keeps attempt counters so
            // repeated manual taps cannot evade the terminal retry limit.
            database.mutationDao().makePendingReady(now)
        }
        syncScheduler.requestManual()
    }

    /**
     * Explicitly keeps the authoritative server version for conflicted records. Every dependent
     * mutation is removed and local rows are marked for a cursor-zero reload. Their content stays
     * available while offline and is replaced only after the server answers successfully. This is
     * intentionally never invoked without UI confirmation.
     */
    suspend fun discardConflictsAndReload(): Int = ProcessSyncGate.serialized {
        if (preferences.state.first().appMode == AppMode.LOCAL) return@serialized 0
        val conflicts = database.mutationDao().conflicts()
            .distinctBy { it.entityType to it.entityId }
        if (conflicts.isEmpty()) return@serialized 0
        database.withTransaction {
            conflicts.forEach { conflict ->
                database.mutationDao().deleteForEntity(conflict.entityType, conflict.entityId)
                when (conflict.entityType) {
                    PERIOD_ENTITY_TYPE -> database.periodDao().prepareServerReload(conflict.entityId)
                    DAILY_LOG_ENTITY_TYPE -> database.dailyLogDao().prepareServerReload(conflict.entityId)
                }
            }
            database.syncMetadataDao().clear()
        }
        syncScheduler.requestAfterMutation()
        conflicts.size
    }

    suspend fun startPeriod(
        userId: String,
        date: LocalDate = LocalDate.now(Clock.systemDefaultZone()),
    ): String = createPeriod(userId, date, null)

    /**
     * Creates one complete optimistic period record and exactly one offline mutation. Historical
     * periods therefore never need a follow-up update merely to attach their known end date.
     */
    suspend fun createPeriod(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate? = null,
    ): String = ProcessSyncGate.serialized {
        ensureCurrentTracker(userId)
        val localMode = preferences.state.first().appMode == AppMode.LOCAL
        val today = LocalDate.now(Clock.systemDefaultZone())
        PeriodDatePolicy.requireValid(
            startDate,
            endDate,
            today,
        )
        database.periodDao().findByStartDate(userId, startDate.toString())?.let { existing ->
            if (SyncEditPolicy.isBlocked(existing.syncState)) throw PeriodSyncConflictException()
            if (existing.endDate == endDate?.toString()) return@serialized existing.id
            throw DuplicatePeriodDateException()
        }
        if (
            PeriodOverlapPolicy.hasOverlap(
                database.periodDao().activeForUser(userId),
                startDate,
                endDate,
                today,
            )
        ) {
            throw OverlappingPeriodException()
        }
        val entityId = UUID.randomUUID().toString()
        val mutationId = UUID.randomUUID().toString()
        val now = nextMutationTime()
        val entityTimestamp = now.toString()
        val mutationTimestamp = MutationTimestampPolicy.format(now)
        val period = PeriodEntity(
            id = entityId,
            userId = userId,
            startDate = startDate.toString(),
            endDate = endDate?.toString(),
            createdAt = entityTimestamp,
            updatedAt = entityTimestamp,
            syncState = if (localMode) "LOCAL" else "PENDING",
        )
        val mutation = MutationEntity(
            id = UUID.randomUUID().toString(),
            clientMutationId = mutationId,
            entityType = PERIOD_ENTITY_TYPE,
            entityId = entityId,
            operation = "UPSERT",
            payloadJson = periodCreatePayload(period),
            baseVersion = 0,
            createdAt = mutationTimestamp,
            nextAttemptAt = mutationTimestamp,
        )
        database.withTransaction {
            database.periodDao().insert(period)
            if (!localMode) {
                check(database.mutationDao().enqueue(mutation) != -1L) { "Mutation ID collision" }
            }
        }
        if (!localMode) syncScheduler.requestAfterMutation()
        entityId
    }

    suspend fun updatePeriodDates(
        userId: String,
        periodId: String,
        startDate: LocalDate,
        endDate: LocalDate?,
    ): String = ProcessSyncGate.serialized {
        updatePeriodDatesLocked(userId, periodId, startDate, endDate)
    }

    /** Revalidates the confirmed preview and atomically adds dates; never replaces existing rows. */
    suspend fun importPeriodRanges(draft: PeriodImportDraft): Int = ProcessSyncGate.serialized {
        val identity = draft.identity
        var inserted = 0
        val committed = credentialGate.commitIfCurrent(identity.generation) {
            val localMode = identity.mode == AppMode.LOCAL
            inserted = commitPeriodImport(database, gson, draft, now = ::nextMutationTime) {
                if (!identity.matches(preferences.state.first(), credentialGate.snapshot())) throw PeriodFileException(PeriodFileProblem.ACCOUNT_CHANGED)
            }
            if (inserted > 0 && !localMode) scheduleCommittedPeriodImport(syncScheduler::requestAfterMutation)
            true
        }
        if (!committed) throw PeriodFileException(PeriodFileProblem.ACCOUNT_CHANGED)
        inserted
    }

    /** Reads the current start under the same process gate and only changes the end date. */
    suspend fun endPeriod(
        userId: String,
        periodId: String,
        endDate: LocalDate = LocalDate.now(Clock.systemDefaultZone()),
    ): Boolean = ProcessSyncGate.serialized {
        ensureCurrentTracker(userId)
        val current = requireOwnedActivePeriod(userId, periodId)
        if (!PeriodMutationPolicy.canEnd(current)) return@serialized false
        updatePeriodDatesLocked(userId, periodId, LocalDate.parse(current.startDate), endDate)
        true
    }

    private suspend fun updatePeriodDatesLocked(
        userId: String,
        periodId: String,
        startDate: LocalDate,
        endDate: LocalDate?,
    ): String {
        ensureCurrentTracker(userId)
        val localMode = preferences.state.first().appMode == AppMode.LOCAL
        val today = LocalDate.now(Clock.systemDefaultZone())
        PeriodDatePolicy.requireValid(startDate, endDate, today)
        val now = nextMutationTime()
        val entityTimestamp = now.toString()
        val mutationTimestamp = MutationTimestampPolicy.format(now)
        var changed = false
        database.withTransaction {
            val current = requireOwnedActivePeriod(userId, periodId)
            if (!PeriodMutationPolicy.datesChanged(current, startDate, endDate)) {
                return@withTransaction
            }
            database.periodDao().findByStartDate(userId, startDate.toString())
                ?.takeIf { it.id != periodId }
                ?.let { throw DuplicatePeriodDateException() }
            if (
                PeriodOverlapPolicy.hasOverlap(
                    database.periodDao().activeForUser(userId),
                    startDate,
                    endDate,
                    today,
                    excludingId = periodId,
                )
            ) {
                throw OverlappingPeriodException()
            }

            if (localMode) {
                database.periodDao().upsertAll(
                    listOf(
                        current.copy(
                            startDate = startDate.toString(),
                            endDate = endDate?.toString(),
                            updatedAt = entityTimestamp,
                            version = current.version + 1,
                            syncState = "LOCAL",
                        ),
                    ),
                )
                changed = true
                return@withTransaction
            }

            val queued = database.mutationDao().forEntity(PERIOD_ENTITY_TYPE, periodId)
            if (queued.any { it.state == "CONFLICT" }) throw PeriodSyncConflictException()
            database.mutationDao().reviveFailedForEntity(PERIOD_ENTITY_TYPE, periodId, mutationTimestamp)
            val normalizedQueue = if (queued.any { it.state == "FAILED" }) {
                database.mutationDao().forEntity(PERIOD_ENTITY_TYPE, periodId)
            } else queued
            val tail = PeriodMutationPolicy.replaceableTail(normalizedQueue)
            val baseVersion = tail?.baseVersion
                ?: PeriodMutationPolicy.predictedServerVersion(current.version, normalizedQueue)
            val payloadJson = if (baseVersion > 0L) {
                PeriodPayloadPolicy.update(gson, periodId, startDate, endDate, baseVersion)
            } else ""
            val pendingPeriod = current.copy(
                startDate = startDate.toString(),
                endDate = endDate?.toString(),
                updatedAt = entityTimestamp,
                syncState = "PENDING",
            )
            database.periodDao().upsertAll(listOf(pendingPeriod))
            if (baseVersion == 0L) {
                val unsentCreate = tail ?: throw PeriodSyncConflictException()
                check(
                    database.mutationDao().replaceUnattempted(
                        unsentCreate.id,
                        "UPSERT",
                        periodCreatePayload(pendingPeriod),
                        0,
                        mutationTimestamp,
                    ) == 1,
                ) { "Queued period creation changed unexpectedly" }
            } else {
                enqueueOrReplacePeriodMutation(
                    tail = tail,
                    entityId = periodId,
                    operation = "UPSERT",
                    payloadJson = payloadJson,
                    baseVersion = baseVersion,
                    now = mutationTimestamp,
                    dependencies = normalizedQueue,
                )
            }
            changed = true
        }
        if (changed && !localMode) syncScheduler.requestAfterMutation()
        return periodId
    }

    suspend fun deletePeriod(userId: String, periodId: String) = ProcessSyncGate.serialized {
        ensureCurrentTracker(userId)
        val localMode = preferences.state.first().appMode == AppMode.LOCAL
        val now = nextMutationTime()
        val entityTimestamp = now.toString()
        val mutationTimestamp = MutationTimestampPolicy.format(now)
        var mutationQueued = false
        database.withTransaction {
            val current = requireOwnedActivePeriod(userId, periodId)
            if (localMode) {
                database.periodDao().upsertAll(
                    listOf(
                        current.copy(
                            deletedAt = entityTimestamp,
                            updatedAt = entityTimestamp,
                            version = current.version + 1,
                            syncState = "LOCAL",
                        ),
                    ),
                )
                return@withTransaction
            }
            val queued = database.mutationDao().forEntity(PERIOD_ENTITY_TYPE, periodId)
            if (queued.any { it.state == "CONFLICT" }) throw PeriodSyncConflictException()

            database.mutationDao().reviveFailedForEntity(PERIOD_ENTITY_TYPE, periodId, mutationTimestamp)
            val normalizedQueue = if (queued.any { it.state == "FAILED" }) {
                database.mutationDao().forEntity(PERIOD_ENTITY_TYPE, periodId)
            } else queued
            val tail = PeriodMutationPolicy.replaceableTail(normalizedQueue)
            val baseVersion = tail?.baseVersion
                ?: PeriodMutationPolicy.predictedServerVersion(current.version, normalizedQueue)
            val payloadJson = if (baseVersion == 0L) {
                periodCreatePayload(current)
            } else {
                gson.toJson(mapOf("id" to periodId, "version" to baseVersion))
            }
            database.periodDao().upsertAll(
                listOf(
                    current.copy(
                        deletedAt = entityTimestamp,
                        updatedAt = entityTimestamp,
                        syncState = "PENDING",
                    ),
                ),
            )
            if (baseVersion == 0L) {
                val unsentCreate = tail ?: throw PeriodSyncConflictException()
                check(
                    database.mutationDao().replaceUnattempted(
                        unsentCreate.id,
                        "UPSERT",
                        payloadJson,
                        0,
                        mutationTimestamp,
                    ) == 1,
                ) { "Queued period creation changed unexpectedly" }
                enqueueOrReplacePeriodMutation(
                    tail = null,
                    entityId = periodId,
                    operation = "DELETE",
                    payloadJson = gson.toJson(mapOf("id" to periodId, "version" to 1)),
                    baseVersion = 1,
                    now = mutationTimestamp,
                    dependencies = database.mutationDao().forEntity(PERIOD_ENTITY_TYPE, periodId),
                )
            } else {
                enqueueOrReplacePeriodMutation(
                    tail = tail,
                    entityId = periodId,
                    operation = "DELETE",
                    payloadJson = payloadJson,
                    baseVersion = baseVersion,
                    now = mutationTimestamp,
                    dependencies = normalizedQueue,
                )
            }
            mutationQueued = true
        }
        if (mutationQueued && !localMode) syncScheduler.requestAfterMutation()
    }

    suspend fun logToday(
        userId: String,
        flowLevel: String?,
        painLevel: Int?,
        mood: String?,
        energy: Int?,
        sleepQuality: Int?,
        symptoms: List<String>,
    ): String = ProcessSyncGate.serialized {
        ensureCurrentTracker(userId)
        val localMode = preferences.state.first().appMode == AppMode.LOCAL
        val localDate = LocalDate.now(Clock.systemDefaultZone()).toString()
        val existing = database.dailyLogDao().findByDate(userId, localDate)
        val id = existing?.id ?: UUID.randomUUID().toString()
        val now = nextMutationTime()
        val entityTimestamp = now.toString()
        val mutationTimestamp = MutationTimestampPolicy.format(now)
        val safeMood = ApiPayloadPolicy.sanitizeMood(mood)
        val safeSymptoms = ApiPayloadPolicy.sanitizeSymptoms(symptoms)
        val safePain = painLevel?.coerceIn(0, 10)
        val safeEnergy = energy?.coerceIn(0, 10)
        val safeSleep = sleepQuality?.coerceIn(0, 10)
        val symptomsJson = gson.toJson(safeSymptoms)
        val log = DailyLogEntity(
            id = id,
            userId = userId,
            localDate = localDate,
            flowLevel = flowLevel,
            painLevel = safePain,
            mood = safeMood,
            energy = safeEnergy,
            sleepQuality = safeSleep,
            // These fields are not editable on the compact Today form yet. Preserve their
            // existing local values rather than erasing them while the server keeps them.
            bodyTemperature = existing?.bodyTemperature,
            symptomsJson = symptomsJson,
            encryptedNote = existing?.encryptedNote,
            createdAt = existing?.createdAt ?: entityTimestamp,
            updatedAt = entityTimestamp,
            version = existing?.version ?: 0,
            syncState = if (localMode) "LOCAL" else "PENDING",
        )
        database.withTransaction {
            if (existing != null && SyncEditPolicy.isBlocked(existing.syncState)) {
                throw DailyLogSyncConflictException()
            }
            val queued = database.mutationDao().forEntity(DAILY_LOG_ENTITY_TYPE, id)
            if (queued.any { it.state == "CONFLICT" }) throw DailyLogSyncConflictException()
            if (localMode) {
                database.dailyLogDao().upsertAll(
                    listOf(log.copy(version = (existing?.version ?: 0) + 1)),
                )
                return@withTransaction
            }
            database.mutationDao().reviveFailedForEntity(DAILY_LOG_ENTITY_TYPE, id, mutationTimestamp)
            val normalizedQueue = if (queued.any { it.state == "FAILED" }) {
                database.mutationDao().forEntity(DAILY_LOG_ENTITY_TYPE, id)
            } else queued
            val tail = PeriodMutationPolicy.replaceableTail(normalizedQueue)
            val baseVersion = tail?.baseVersion
                ?: PeriodMutationPolicy.predictedServerVersion(existing?.version ?: 0, normalizedQueue)
            // Daily logs use full-record update semantics. Explicit nulls are intentional: they
            // clear values that were removed on this device instead of leaving stale server data.
            val payloadJson = DailyLogPayloadPolicy.serialize(
                gson,
                linkedMapOf<String, Any?>(
                    "id" to id,
                    "localDate" to localDate,
                    "flowLevel" to flowLevel,
                    "painLevel" to safePain,
                    "mood" to safeMood,
                    "energy" to safeEnergy,
                    "sleepQuality" to safeSleep,
                    "bodyTemperature" to existing?.bodyTemperature,
                    "customSymptoms" to safeSymptoms,
                    "note" to existing?.encryptedNote,
                ).apply {
                    if (baseVersion > 0L) put("version", baseVersion)
                },
            )
            database.dailyLogDao().upsertAll(listOf(log))
            enqueueOrReplacePeriodMutation(
                tail = tail,
                entityType = DAILY_LOG_ENTITY_TYPE,
                entityId = id,
                operation = "UPSERT",
                payloadJson = payloadJson,
                baseVersion = baseVersion,
                now = mutationTimestamp,
                dependencies = normalizedQueue,
            )
        }
        if (!localMode) syncScheduler.requestAfterMutation()
        id
    }

    private suspend fun ensureCurrentTracker(expectedUserId: String) {
        val session = preferences.state.first().session
        if (session?.userId != expectedUserId || session.role != UserRole.TRACKER) {
            throw SessionChangedException()
        }
    }

    private suspend fun requireOwnedActivePeriod(userId: String, periodId: String): PeriodEntity {
        val period = database.periodDao().findById(periodId)
            ?: throw PeriodNotFoundException()
        if (period.userId != userId || period.deletedAt != null) throw PeriodNotFoundException()
        if (SyncEditPolicy.isBlocked(period.syncState)) throw PeriodSyncConflictException()
        return period
    }

    private fun periodCreatePayload(period: PeriodEntity): String = PeriodPayloadPolicy.create(
        gson = gson,
        id = period.id,
        startDate = LocalDate.parse(period.startDate),
        endDate = period.endDate?.let(LocalDate::parse),
        source = period.source,
    )

    private suspend fun enqueueOrReplacePeriodMutation(
        tail: MutationEntity?,
        entityType: String = PERIOD_ENTITY_TYPE,
        entityId: String,
        operation: String,
        payloadJson: String,
        baseVersion: Long,
        now: String,
        dependencies: List<MutationEntity>,
    ) {
        val dependencyAttemptAt = dependencies.maxOfOrNull { it.nextAttemptAt }
        val nextAttemptAt = if (dependencies.isEmpty()) {
            now
        } else {
            MutationTimestampPolicy.format(nextMutationTime().plusMillis(1)).let { afterDependency ->
                maxOf(afterDependency, dependencyAttemptAt ?: afterDependency)
            }
        }
        if (tail != null) {
            check(
                database.mutationDao().replaceUnattempted(
                    tail.id,
                    operation,
                    payloadJson,
                    baseVersion,
                    nextAttemptAt,
                ) == 1,
            ) { "Queued period mutation changed unexpectedly" }
            return
        }
        val id = UUID.randomUUID().toString()
        check(
            database.mutationDao().enqueue(
                MutationEntity(
                    id = id,
                    clientMutationId = UUID.randomUUID().toString(),
                    entityType = entityType,
                    entityId = entityId,
                    operation = operation,
                    payloadJson = payloadJson,
                    baseVersion = baseVersion,
                    createdAt = now,
                    nextAttemptAt = nextAttemptAt,
                ),
            ) != -1L,
        ) { "Mutation ID collision" }
    }

    private companion object {
        const val PERIOD_ENTITY_TYPE = "PERIOD"
        const val DAILY_LOG_ENTITY_TYPE = "DAILY_LOG"
        private var lastMutationInstant: Instant = Instant.EPOCH

        @Synchronized
        fun nextMutationTime(): Instant {
            val now = Instant.now()
            lastMutationInstant = if (now.isAfter(lastMutationInstant)) now else lastMutationInstant.plusNanos(1)
            return lastMutationInstant
        }
    }
}

/** Scheduling follows a durable commit, so a transient scheduler failure is not an import failure. */
internal fun scheduleCommittedPeriodImport(schedule: () -> Unit) {
    try { schedule() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) {
        // Rows and queue are already durable. Periodic/manual sync can drain the queue later;
        // a scheduler failure must not falsely report that the completed import failed.
    }
}

/** Transaction core shared by the guarded repository and synthetic Room regression tests. */
internal suspend fun commitPeriodImport(
    database: LunaraDatabase,
    gson: Gson,
    draft: PeriodImportDraft,
    now: () -> Instant = Instant::now,
    newId: () -> String = { UUID.randomUUID().toString() },
    ensureIdentity: suspend () -> Unit,
): Int = database.withTransaction {
    ensureIdentity()
    val identity = draft.identity
    val current = PeriodExportFormat.ownedRanges(database.periodDao().activeForUser(identity.userId), identity)
    val plan = PeriodExportFormat.plan(draft.ranges, current)
    if (plan != draft.plan || plan.summary.conflictCount > 0) throw PeriodFileException(PeriodFileProblem.CHANGED)
    for (range in plan.additions) {
        ensureIdentity()
        val instant = now()
        val timestamp = MutationTimestampPolicy.format(instant)
        val period = PeriodEntity(
            id = newId(), userId = identity.userId, startDate = range.startDate, endDate = range.endDate,
            source = "IMPORT", createdAt = instant.toString(), updatedAt = instant.toString(),
            syncState = if (identity.mode == AppMode.LOCAL) "LOCAL" else "PENDING",
        )
        database.periodDao().insert(period)
        if (identity.mode == AppMode.SERVER) check(database.mutationDao().enqueue(MutationEntity(
            id = newId(), clientMutationId = newId(), entityType = "PERIOD", entityId = period.id,
            operation = "UPSERT", baseVersion = 0,
            payloadJson = PeriodPayloadPolicy.create(gson, period.id, LocalDate.parse(range.startDate), range.endDate?.let(LocalDate::parse), "IMPORT"),
            createdAt = timestamp, nextAttemptAt = timestamp,
        )) != -1L) { "Mutation ID collision" }
    }
    ensureIdentity()
    plan.additions.size
}

internal class SessionChangedException : IllegalStateException("Session changed while saving local data")
internal class PeriodNotFoundException : IllegalStateException("Period entry is unavailable")
internal class DuplicatePeriodDateException : IllegalArgumentException("A period already starts on this date")
internal class OverlappingPeriodException : IllegalArgumentException("Period ranges must not overlap")
internal class InvalidPeriodRangeException : IllegalArgumentException("The period end would precede its start")
internal class PeriodDateInFutureException : IllegalArgumentException("Period dates cannot be in the future")
internal class PeriodSyncConflictException : IllegalStateException("Resolve the existing sync conflict first")
internal class DailyLogSyncConflictException : IllegalStateException("Resolve the existing sync conflict first")

internal object SyncEditPolicy {
    fun isBlocked(syncState: String): Boolean = syncState == "CONFLICT" || syncState == "RESOLVING"
}

internal object DailyLogPayloadPolicy {
    /** Dedicated null serialization gives daily-log updates full-record replacement semantics. */
    fun serialize(gson: Gson, fields: Map<String, Any?>): String =
        gson.newBuilder().serializeNulls().create().toJson(fields)
}

internal object PeriodPayloadPolicy {
    /** A historical period is created in one mutation, including its already known end date. */
    fun create(
        gson: Gson,
        id: String,
        startDate: LocalDate,
        endDate: LocalDate?,
        source: String = "MANUAL",
    ): String = gson.newBuilder().serializeNulls().create().toJson(
        linkedMapOf<String, Any?>(
            "id" to id,
            "startDate" to startDate.toString(),
            "endDate" to endDate?.toString(),
            "source" to source,
        ),
    )

    /** Explicit null is required to remove a previously stored end date through PATCH. */
    fun update(
        gson: Gson,
        id: String,
        startDate: LocalDate,
        endDate: LocalDate?,
        version: Long,
    ): String = gson.newBuilder().serializeNulls().create().toJson(
        linkedMapOf<String, Any?>(
            "id" to id,
            "startDate" to startDate.toString(),
            "endDate" to endDate?.toString(),
            "version" to version,
        ),
    )
}

internal object PeriodDatePolicy {
    fun requireValid(startDate: LocalDate, endDate: LocalDate?, today: LocalDate) {
        if (startDate.isAfter(today) || endDate?.isAfter(today) == true) {
            throw PeriodDateInFutureException()
        }
        if (endDate?.isBefore(startDate) == true) throw InvalidPeriodRangeException()
    }
}

internal object PeriodOverlapPolicy {
    /** Open periods extend through today until an explicit local end date is recorded. */
    fun hasOverlap(
        periods: List<PeriodEntity>,
        startDate: LocalDate,
        endDate: LocalDate?,
        today: LocalDate,
        excludingId: String? = null,
    ): Boolean {
        val candidateEnd = endDate ?: today
        return periods.asSequence()
            .filter { it.id != excludingId && it.deletedAt == null }
            .mapNotNull { period ->
                val existingStart = runCatching { LocalDate.parse(period.startDate) }.getOrNull()
                    ?: return@mapNotNull null
                val existingEnd = period.endDate
                    ?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }
                    ?: today
                existingStart to existingEnd
            }
            .any { (existingStart, existingEnd) ->
                !candidateEnd.isBefore(existingStart) && !existingEnd.isBefore(startDate)
            }
    }
}

internal object MutationTimestampPolicy {
    private val formatter = DateTimeFormatterBuilder().appendInstant(9).toFormatter()

    /** Fixed-width UTC instants retain chronological order under Room's TEXT comparisons. */
    fun format(value: Instant): String = formatter.format(value)
}

internal object PeriodMutationPolicy {
    fun canEnd(current: PeriodEntity): Boolean = current.endDate == null

    fun datesChanged(current: PeriodEntity, startDate: LocalDate, endDate: LocalDate?): Boolean =
        current.startDate != startDate.toString() || current.endDate != endDate?.toString()

    fun replaceableTail(queued: List<MutationEntity>): MutationEntity? = queued.lastOrNull()
        ?.takeIf { it.state == "PENDING" && it.attemptCount == 0 }

    fun predictedServerVersion(localVersion: Long, queued: List<MutationEntity>): Long =
        queued.fold(localVersion) { version, mutation ->
            when (mutation.operation) {
                "UPSERT", "DELETE" -> maxOf(version, if (mutation.baseVersion == 0L) 1L else mutation.baseVersion + 1L)
                else -> version
            }
        }
}

internal object ApiPayloadPolicy {
    const val MAX_MOOD_LENGTH = 64
    const val MAX_SYMPTOM_LENGTH = 80
    const val MAX_SYMPTOM_COUNT = 30

    fun sanitizeMood(value: String?): String? = value
        ?.trim()
        ?.take(MAX_MOOD_LENGTH)
        ?.ifBlank { null }

    fun sanitizeSymptoms(values: List<String>): List<String> = values
        .map { it.trim().take(MAX_SYMPTOM_LENGTH) }
        .filter(String::isNotBlank)
        .distinct()
        .take(MAX_SYMPTOM_COUNT)
}
