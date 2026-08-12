package de.lunara.app.data.repository

import androidx.room.withTransaction
import com.google.gson.Gson
import de.lunara.app.data.local.LunaraDatabase
import de.lunara.app.data.local.MutationEntity
import de.lunara.app.data.local.PeriodEntity
import de.lunara.app.data.local.DailyLogEntity
import de.lunara.app.data.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PeriodRepository @Inject constructor(
    private val database: LunaraDatabase,
    private val syncScheduler: SyncScheduler,
    private val gson: Gson,
) {
    fun observePeriods(): Flow<List<PeriodEntity>> = database.periodDao().observeActive()
    fun observePendingCount(): Flow<Int> = database.mutationDao().observeCount()
    fun observeDailyLogs(): Flow<List<DailyLogEntity>> = database.dailyLogDao().observeActive()

    suspend fun startPeriod(userId: String, date: LocalDate = LocalDate.now(Clock.systemDefaultZone())): String {
        database.periodDao().findByStartDate(userId, date.toString())?.let { return it.id }
        val entityId = UUID.randomUUID().toString()
        val mutationId = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        val period = PeriodEntity(
            id = entityId,
            userId = userId,
            startDate = date.toString(),
            createdAt = now,
            updatedAt = now,
        )
        val mutation = MutationEntity(
            id = UUID.randomUUID().toString(),
            clientMutationId = mutationId,
            entityType = "PERIOD",
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
            createdAt = now,
            nextAttemptAt = now,
        )
        database.withTransaction {
            database.periodDao().insert(period)
            check(database.mutationDao().enqueue(mutation) != -1L) { "Mutation ID collision" }
        }
        syncScheduler.requestImmediate()
        return entityId
    }

    suspend fun logToday(
        userId: String,
        flowLevel: String?,
        painLevel: Int?,
        mood: String?,
        energy: Int?,
        sleepQuality: Int?,
        symptoms: List<String>,
    ): String {
        val localDate = LocalDate.now(Clock.systemDefaultZone()).toString()
        val existing = database.dailyLogDao().findByDate(userId, localDate)
        val id = existing?.id ?: UUID.randomUUID().toString()
        val mutationId = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        val symptomsJson = gson.toJson(symptoms.map(String::trim).filter(String::isNotBlank).distinct().take(30))
        val log = DailyLogEntity(
            id = id,
            userId = userId,
            localDate = localDate,
            flowLevel = flowLevel,
            painLevel = painLevel?.coerceIn(0, 10),
            mood = mood?.take(80),
            energy = energy?.coerceIn(0, 10),
            sleepQuality = sleepQuality?.coerceIn(0, 10),
            symptomsJson = symptomsJson,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            version = existing?.version ?: 0,
            syncState = "PENDING",
        )
        val payload = linkedMapOf<String, Any?>(
            "id" to id,
            "localDate" to localDate,
            "flowLevel" to flowLevel,
            "painLevel" to painLevel,
            "mood" to mood,
            "energy" to energy,
            "sleepQuality" to sleepQuality,
            "customSymptoms" to symptoms,
        ).apply {
            if (existing != null && existing.version > 0) put("version", existing.version)
        }.filterValues { it != null }
        val payloadJson = gson.toJson(payload)
        val mutation = MutationEntity(
            id = UUID.randomUUID().toString(),
            clientMutationId = mutationId,
            entityType = "DAILY_LOG",
            entityId = id,
            operation = "UPSERT",
            payloadJson = payloadJson,
            baseVersion = existing?.version ?: 0,
            createdAt = now,
            nextAttemptAt = now,
        )
        database.withTransaction {
            database.dailyLogDao().upsertAll(listOf(log))
            val replaced = if (existing?.syncState == "PENDING") {
                database.mutationDao().replacePendingPayload("DAILY_LOG", id, payloadJson, now, now)
            } else 0
            if (replaced == 0) check(database.mutationDao().enqueue(mutation) != -1L) { "Mutation ID collision" }
        }
        syncScheduler.requestImmediate()
        return id
    }
}
