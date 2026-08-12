package de.lunara.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PeriodDao {
    @Query("SELECT * FROM period_events WHERE deletedAt IS NULL ORDER BY startDate DESC")
    fun observeActive(): Flow<List<PeriodEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(period: PeriodEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(periods: List<PeriodEntity>)

    @Query("SELECT * FROM period_events WHERE id = :id")
    suspend fun findById(id: String): PeriodEntity?

    @Query("SELECT * FROM period_events WHERE userId = :userId AND startDate = :startDate AND deletedAt IS NULL LIMIT 1")
    suspend fun findByStartDate(userId: String, startDate: String): PeriodEntity?

    @Query("UPDATE period_events SET deletedAt = :deletedAt, updatedAt = :updatedAt, version = :version, syncState = 'SYNCED' WHERE id = :id")
    suspend fun applyDelete(id: String, deletedAt: String, updatedAt: String, version: Long)

    @Query("UPDATE period_events SET syncState = :state, version = :version WHERE id = :id")
    suspend fun markSynced(id: String, state: String, version: Long)

    @Query("DELETE FROM period_events")
    suspend fun clear()
}

@Dao
interface DailyLogDao {
    @Query("SELECT * FROM daily_logs WHERE deletedAt IS NULL ORDER BY localDate DESC")
    fun observeActive(): Flow<List<DailyLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(logs: List<DailyLogEntity>)

    @Query("SELECT * FROM daily_logs WHERE userId = :userId AND localDate = :localDate AND deletedAt IS NULL LIMIT 1")
    suspend fun findByDate(userId: String, localDate: String): DailyLogEntity?

    @Query("SELECT * FROM daily_logs WHERE id = :id")
    suspend fun findById(id: String): DailyLogEntity?

    @Query("UPDATE daily_logs SET deletedAt = :deletedAt, updatedAt = :updatedAt, version = :version, syncState = 'SYNCED' WHERE id = :id")
    suspend fun applyDelete(id: String, deletedAt: String, updatedAt: String, version: Long)

    @Query("UPDATE daily_logs SET syncState = :state, version = :version WHERE id = :id")
    suspend fun markSynced(id: String, state: String, version: Long)

    @Query("DELETE FROM daily_logs")
    suspend fun clear()
}

@Dao
interface MutationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(mutation: MutationEntity): Long

    @Query("SELECT * FROM mutation_queue WHERE state = 'PENDING' AND nextAttemptAt <= :now ORDER BY createdAt, id LIMIT :limit")
    suspend fun ready(now: String, limit: Int = 50): List<MutationEntity>

    @Query("UPDATE mutation_queue SET state = 'IN_FLIGHT' WHERE id IN (:ids) AND state = 'PENDING'")
    suspend fun claim(ids: List<String>): Int

    @Query("DELETE FROM mutation_queue WHERE clientMutationId IN (:clientMutationIds)")
    suspend fun acknowledge(clientMutationIds: List<String>)

    @Query("UPDATE mutation_queue SET state = 'PENDING', attemptCount = attemptCount + 1, nextAttemptAt = :nextAttemptAt, lastErrorCode = :errorCode WHERE id IN (:ids)")
    suspend fun retry(ids: List<String>, nextAttemptAt: String, errorCode: String)

    @Query("UPDATE mutation_queue SET state = 'CONFLICT', lastErrorCode = :reason WHERE clientMutationId IN (:clientMutationIds)")
    suspend fun markConflicted(clientMutationIds: List<String>, reason: String = "VERSION_CONFLICT")

    @Query("UPDATE mutation_queue SET payloadJson = :payloadJson, createdAt = :createdAt, nextAttemptAt = :nextAttemptAt WHERE entityType = :entityType AND entityId = :entityId AND state = 'PENDING'")
    suspend fun replacePendingPayload(
        entityType: String,
        entityId: String,
        payloadJson: String,
        createdAt: String,
        nextAttemptAt: String,
    ): Int

    @Query("UPDATE mutation_queue SET state = 'PENDING' WHERE state = 'IN_FLIGHT'")
    suspend fun releaseInterrupted()

    @Query("SELECT COUNT(*) FROM mutation_queue")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM mutation_queue")
    suspend fun clear()
}

@Dao
interface SyncMetadataDao {
    @Query("SELECT * FROM sync_metadata WHERE deviceKey = 'this-device'")
    suspend fun current(): SyncMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(value: SyncMetadataEntity)

    @Query("DELETE FROM sync_metadata")
    suspend fun clear()
}

@Dao
interface PartnerDashboardDao {
    @Query("SELECT * FROM partner_dashboard_cache WHERE cacheKey = 'current'")
    suspend fun current(): PartnerDashboardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(value: PartnerDashboardEntity)

    @Query("DELETE FROM partner_dashboard_cache")
    suspend fun clear()
}
