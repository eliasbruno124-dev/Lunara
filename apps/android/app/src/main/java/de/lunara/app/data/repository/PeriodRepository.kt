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
import kotlinx.coroutines.flow.Flow
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
) {
    fun observePeriods(): Flow<List<PeriodEntity>> = database.periodDao().observeActive()
    fun observePendingCount(): Flow<Int> = combine(
        database.mutationDao().observeCount(),
        database.periodDao().observeResolvingCount(),
        database.dailyLogDao().observeResolvingCount(),
    ) { mutations, resolvingPeriods, resolvingLogs ->
        mutations + resolvingPeriods + resolvingLogs
    }
    fun observeConflictCount(): Flow<Int> = database.mutationDao().observeConflictCount()
    fun observeDailyLogs(): Flow<List<DailyLogEntity>> = database.dailyLogDao().observeActive()

    suspend fun retryFailedMutations() = ProcessSyncGate.serialized {
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

    suspend fun startPeriod(userId: String, date: LocalDate = LocalDate.now(Clock.systemDefaultZone())): String =
        ProcessSyncGate.serialized {
            ensureCurrentTracker(userId)
            require(!date.isAfter(LocalDate.now(Clock.systemDefaultZone()))) { "A period cannot start in the future" }
            database.periodDao().findByStartDate(userId, date.toString())?.let { existing ->
                if (SyncEditPolicy.isBlocked(existing.syncState)) throw PeriodSyncConflictException()
                return@serialized existing.id
            }
            val entityId = UUID.randomUUID().toString()
            val mutationId = UUID.randomUUID().toString()
            val now = nextMutationTime()
            val entityTimestamp = now.toString()
            val mutationTimestamp = MutationTimestampPolicy.format(now)
            val period = PeriodEntity(
                id = entityId,
                userId = userId,
                startDate = date.toString(),
                createdAt = entityTimestamp,
                updatedAt = entityTimestamp,
            )
            val mutation = MutationEntity(
                id = UUID.randomUUID().toString(),
                clientMutationId = mutationId,
                entityType = PERIOD_ENTITY_TYPE,
                entityId = entityId,
                operation = "UPSERT",
                payloadJson = gson.toJson(
                    mapOf(
                        "id" to entityId,
                        "startDate" to date.toString(),
                        "source" to "MANUAL",
                    ),
                ),
                baseVersion = 0,
                createdAt = mutationTimestamp,
                nextAttemptAt = mutationTimestamp,
            )
            database.withTransaction {
                database.periodDao().insert(period)
                check(database.mutationDao().enqueue(mutation) != -1L) { "Mutation ID collision" }
            }
            syncScheduler.requestAfterMutation()
            entityId
        }

    suspend fun updatePeriodStart(userId: String, periodId: String, startDate: LocalDate): String =
        ProcessSyncGate.serialized {
            ensureCurrentTracker(userId)
            require(!startDate.isAfter(LocalDate.now(Clock.systemDefaultZone()))) { "A period cannot start in the future" }
            val now = nextMutationTime()
            val entityTimestamp = now.toString()
            val mutationTimestamp = MutationTimestampPolicy.format(now)
            database.withTransaction {
                val current = requireOwnedActivePeriod(userId, periodId)
                if (current.startDate == startDate.toString()) return@withTransaction
                current.endDate?.let { endDate ->
                    if (startDate.isAfter(LocalDate.parse(endDate))) throw InvalidPeriodRangeException()
                }
                database.periodDao().findByStartDate(userId, startDate.toString())
                    ?.takeIf { it.id != periodId }
                    ?.let { throw DuplicatePeriodDateException() }

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
                    gson.toJson(
                        mapOf(
                            "id" to periodId,
                            "startDate" to startDate.toString(),
                            "version" to baseVersion,
                        ),
                    )
                } else ""
                val pendingPeriod = current.copy(
                    startDate = startDate.toString(),
                    updatedAt = entityTimestamp,
                    syncState = "PENDING",
                )
                database.periodDao().upsertAll(
                    listOf(
                        pendingPeriod,
                    ),
                )
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
            }
            syncScheduler.requestAfterMutation()
            periodId
        }

    suspend fun deletePeriod(userId: String, periodId: String) = ProcessSyncGate.serialized {
        ensureCurrentTracker(userId)
        val now = nextMutationTime()
        val entityTimestamp = now.toString()
        val mutationTimestamp = MutationTimestampPolicy.format(now)
        var mutationQueued = false
        database.withTransaction {
            val current = requireOwnedActivePeriod(userId, periodId)
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
        if (mutationQueued) syncScheduler.requestAfterMutation()
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
            syncState = "PENDING",
        )
        database.withTransaction {
            if (existing != null && SyncEditPolicy.isBlocked(existing.syncState)) {
                throw DailyLogSyncConflictException()
            }
            val queued = database.mutationDao().forEntity(DAILY_LOG_ENTITY_TYPE, id)
            if (queued.any { it.state == "CONFLICT" }) throw DailyLogSyncConflictException()
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
        syncScheduler.requestAfterMutation()
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

    private fun periodCreatePayload(period: PeriodEntity): String = gson.toJson(
        linkedMapOf<String, Any?>(
            "id" to period.id,
            "startDate" to period.startDate,
            "endDate" to period.endDate,
            "source" to period.source,
        ),
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

internal class SessionChangedException : IllegalStateException("Session changed while saving local data")
internal class PeriodNotFoundException : IllegalStateException("Period entry is unavailable")
internal class DuplicatePeriodDateException : IllegalArgumentException("A period already starts on this date")
internal class InvalidPeriodRangeException : IllegalArgumentException("The period end would precede its start")
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

internal object MutationTimestampPolicy {
    private val formatter = DateTimeFormatterBuilder().appendInstant(9).toFormatter()

    /** Fixed-width UTC instants retain chronological order under Room's TEXT comparisons. */
    fun format(value: Instant): String = formatter.format(value)
}

internal object PeriodMutationPolicy {
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
