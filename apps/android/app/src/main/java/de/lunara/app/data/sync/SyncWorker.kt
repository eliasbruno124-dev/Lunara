package de.lunara.app.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.lunara.app.data.local.LunaraDatabase
import de.lunara.app.data.local.SyncMetadataEntity
import de.lunara.app.data.local.PeriodEntity
import de.lunara.app.data.local.DailyLogEntity
import de.lunara.app.data.local.PartnerDashboardEntity
import de.lunara.app.data.repository.ApiException
import de.lunara.app.data.preferences.AppPreferences
import de.lunara.app.data.remote.ApiClientFactory
import de.lunara.app.data.remote.dto.SyncMutationDto
import de.lunara.app.data.remote.dto.SyncRequest
import de.lunara.app.data.repository.ConnectionRepository
import de.lunara.app.data.repository.SessionSecrets
import de.lunara.app.data.repository.endpoint
import de.lunara.app.data.repository.requireBody
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/** A single gate shared by every one-shot and periodic worker in this app process. */
internal object ProcessSyncGate {
    private val mutex = Mutex()

    suspend fun <T> serialized(block: suspend () -> T): T = mutex.withLock { block() }
}

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: LunaraDatabase,
    private val preferences: AppPreferences,
    private val secrets: SessionSecrets,
    private val apiFactory: ApiClientFactory,
    private val connectionRepository: ConnectionRepository,
    private val gson: Gson,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = ProcessSyncGate.serialized {
        runCatching { doSerializedWork() }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            if (runAttemptCount >= MutationQueuePolicy.MAX_ATTEMPTS) Result.failure() else Result.retry()
        }
    }

    private suspend fun doSerializedWork(): Result {
        val state = preferences.state.first()
        val base = state.baseUrl ?: return Result.success()
        // Never issue /sync without a Lunara access token. A refresh token may restore the
        // authenticated session first; without either credential there is nothing safe to send.
        when (SyncAuthenticationPolicy.decide(state.session != null, secrets.accessToken, secrets.refreshToken)) {
            SyncAuthenticationAction.SKIP -> return Result.success()
            SyncAuthenticationAction.REFRESH -> if (!connectionRepository.refresh()) {
                return if (runAttemptCount >= MutationQueuePolicy.MAX_ATTEMPTS) Result.failure() else Result.retry()
            }
            SyncAuthenticationAction.SYNC -> Unit
        }

        database.mutationDao().releaseInterrupted()
        val now = Instant.now()
        val pending = MutationQueuePolicy.stableOrder(database.mutationDao().ready(now.toString()))
        val ids = pending.map { it.id }
        if (ids.isNotEmpty()) database.mutationDao().claim(ids)

        return runCatching {
            val mapType = object : TypeToken<Map<String, Any?>>() {}.type
            val storedCursor = database.syncMetadataDao().current()?.cursor ?: "0"
            val request = SyncRequest(
                cursor = storedCursor,
                mutations = pending.map {
                    SyncMutationDto(
                        it.clientMutationId,
                        it.entityType,
                        it.operation,
                        gson.fromJson(it.payloadJson, mapType),
                    )
                },
            )

            var body = if (pending.isNotEmpty()) {
                var response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
                    .sync(endpoint(base, "api/v1/sync"), request)
                if (response.code() == 401 && connectionRepository.refresh()) {
                    response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
                        .sync(endpoint(base, "api/v1/sync"), request)
                }
                response.requireBody()
            } else {
                var response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
                    .pullSync(endpoint(base, "api/v1/sync"), storedCursor)
                if (response.code() == 401 && connectionRepository.refresh()) {
                    response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
                        .pullSync(endpoint(base, "api/v1/sync"), storedCursor)
                }
                val pull = response.requireBody()
                de.lunara.app.data.remote.dto.SyncResponseDto(
                    cursor = pull.cursor,
                    hasMore = pull.hasMore,
                    changes = pull.changes,
                    partnerDashboard = pull.partnerDashboard,
                )
            }
            var pagePending = pending
            var previousCursor = storedCursor
            var pageCount = 1
            while (true) {
                val acknowledged = body.results.map { it.clientMutationId }
                val completedAt = Instant.now().toString()
                val cachedTip = database.partnerDashboardDao().current()?.let { cached ->
                    runCatching {
                        gson.fromJson(
                            cached.payloadJson,
                            de.lunara.app.data.remote.dto.PartnerDashboardDto::class.java,
                        ).tip
                    }.getOrNull()
                }
                database.withTransaction {
                    database.mutationDao().acknowledge(acknowledged)
                    body.results.forEach { result ->
                        val local = pagePending.firstOrNull { it.clientMutationId == result.clientMutationId }
                        val version = (result.entity?.get("version") as? Number)?.toLong() ?: 1L
                        if (local?.entityType == "PERIOD") {
                            database.periodDao().markSynced(local.entityId, "SYNCED", version)
                        } else if (local?.entityType == "DAILY_LOG") {
                            database.dailyLogDao().markSynced(local.entityId, "SYNCED", version)
                        }
                    }
                    applyChanges(body.changes, state.session?.userId.orEmpty())
                    body.partnerDashboard?.let {
                        database.partnerDashboardDao().save(
                            PartnerDashboardEntity(
                                payloadJson = gson.toJson(it.copy(tip = it.tip ?: cachedTip)),
                                updatedAt = completedAt,
                            ),
                        )
                    }
                    database.syncMetadataDao().save(SyncMetadataEntity(cursor = body.cursor, lastSuccessAt = completedAt))
                }
                preferences.setLastSync(completedAt)
                if (!body.hasMore) break
                check(body.cursor != previousCursor) { "Sync cursor did not advance" }
                previousCursor = body.cursor
                pagePending = emptyList()
                check(pageCount < MAX_PAGES_PER_RUN) { "Sync page limit exceeded" }
                pageCount += 1

                var response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
                    .pullSync(endpoint(base, "api/v1/sync"), body.cursor)
                if (response.code() == 401 && connectionRepository.refresh()) {
                    response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
                        .pullSync(endpoint(base, "api/v1/sync"), body.cursor)
                }
                val pull = response.requireBody()
                body = de.lunara.app.data.remote.dto.SyncResponseDto(
                    cursor = pull.cursor,
                    hasMore = pull.hasMore,
                    changes = pull.changes,
                    partnerDashboard = pull.partnerDashboard,
                )
            }
            Result.success()
        }.getOrElse { throwable ->
            if (throwable is ApiException && throwable.status == 409 && pending.isNotEmpty()) {
                database.mutationDao().markConflicted(pending.map { it.clientMutationId })
                return Result.failure()
            }
            val attempt = pending.maxOfOrNull { it.attemptCount } ?: runAttemptCount
            val next = now.plusMillis(MutationQueuePolicy.nextDelayMillis(attempt)).toString()
            val errorCode = throwable.javaClass.simpleName.take(64)
            val terminal = MutationQueuePolicy.isTerminalAfterNextFailure(attempt)
            if (ids.isNotEmpty()) {
                if (terminal) database.mutationDao().markFailed(ids, errorCode)
                else database.mutationDao().retry(ids, next, errorCode)
            }
            if (terminal) Result.failure() else Result.retry()
        }
    }

    private suspend fun applyChanges(changes: List<de.lunara.app.data.remote.dto.SyncChangeDto>, userId: String) {
        changes.forEach { change ->
            val id = change.entityId ?: change.entity?.get("id") as? String ?: return@forEach
            val entity = change.entity
            val deleted = change.operation == "DELETE" || entity == null
            when (change.entityType) {
                "PeriodEvent", "PERIOD" -> {
                    if (deleted) {
                        database.periodDao().findById(id)?.let { local ->
                            if (local.syncState == "PENDING") return@forEach
                            database.periodDao().applyDelete(
                                id,
                                (entity?.get("deletedAt") as? String) ?: change.occurredAt ?: Instant.now().toString(),
                                change.occurredAt ?: Instant.now().toString(),
                                change.entityVersion ?: local.version,
                            )
                        }
                    } else {
                        val local = database.periodDao().findById(id)
                        if (local?.syncState != "PENDING") {
                            database.periodDao().upsertAll(
                                listOf(
                                    PeriodEntity(
                                        id = id,
                                        userId = userId,
                                        startDate = entity.string("startDate") ?: return@forEach,
                                        endDate = entity.string("endDate"),
                                        source = entity.string("source") ?: "SYNC",
                                        createdAt = entity.string("createdAt") ?: change.occurredAt ?: Instant.now().toString(),
                                        updatedAt = entity.string("updatedAt") ?: change.occurredAt ?: Instant.now().toString(),
                                        deletedAt = entity.string("deletedAt"),
                                        version = entity.long("version") ?: change.entityVersion ?: 1,
                                        syncState = "SYNCED",
                                    ),
                                ),
                            )
                        }
                    }
                }
                "DailyLog", "DAILY_LOG" -> {
                    if (deleted) {
                        database.dailyLogDao().findById(id)?.let { local ->
                            if (local.syncState == "PENDING") return@forEach
                            database.dailyLogDao().applyDelete(
                                id,
                                (entity?.get("deletedAt") as? String) ?: change.occurredAt ?: Instant.now().toString(),
                                change.occurredAt ?: Instant.now().toString(),
                                change.entityVersion ?: local.version,
                            )
                        }
                    } else {
                        val local = database.dailyLogDao().findById(id)
                        if (local?.syncState != "PENDING") {
                            database.dailyLogDao().upsertAll(
                                listOf(
                                    DailyLogEntity(
                                        id = id,
                                        userId = userId,
                                        localDate = entity.string("localDate") ?: return@forEach,
                                        flowLevel = entity.string("flowLevel"),
                                        painLevel = entity.int("painLevel"),
                                        mood = entity.string("mood"),
                                        energy = entity.int("energy"),
                                        sleepQuality = entity.int("sleepQuality"),
                                        bodyTemperature = (entity["bodyTemperature"] as? Number)?.toDouble(),
                                        symptomsJson = gson.toJson(entity["customSymptoms"] ?: emptyList<String>()),
                                        encryptedNote = entity.string("note"),
                                        createdAt = entity.string("createdAt") ?: change.occurredAt ?: Instant.now().toString(),
                                        updatedAt = entity.string("updatedAt") ?: change.occurredAt ?: Instant.now().toString(),
                                        deletedAt = entity.string("deletedAt"),
                                        version = entity.long("version") ?: change.entityVersion ?: 1,
                                        syncState = "SYNCED",
                                    ),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun Map<String, Any?>.string(key: String): String? = this[key] as? String
    private fun Map<String, Any?>.long(key: String): Long? = (this[key] as? Number)?.toLong()
    private fun Map<String, Any?>.int(key: String): Int? = (this[key] as? Number)?.toInt()

    private companion object {
        // Each page contains at most 500 changes. The hard stop protects against
        // a broken server cursor without treating normal pagination as a retry.
        const val MAX_PAGES_PER_RUN = 100
    }
}
