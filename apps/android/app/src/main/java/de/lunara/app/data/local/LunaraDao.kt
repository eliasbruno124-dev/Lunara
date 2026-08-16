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

    @Query("SELECT * FROM period_events WHERE userId = :userId AND deletedAt IS NULL ORDER BY startDate")
    suspend fun activeForUser(userId: String): List<PeriodEntity>

    @Query("UPDATE period_events SET deletedAt = :deletedAt, updatedAt = :updatedAt, version = :version, syncState = 'SYNCED' WHERE id = :id")
    suspend fun applyDelete(id: String, deletedAt: String, updatedAt: String, version: Long)

    @Query("UPDATE period_events SET syncState = :state, version = :version WHERE id = :id")
    suspend fun markSynced(id: String, state: String, version: Long)

    @Query("UPDATE period_events SET syncState = :state WHERE id = :id")
    suspend fun setSyncState(id: String, state: String)

    @Query("UPDATE period_events SET syncState = 'RESOLVING', version = -1 WHERE id = :id")
    suspend fun prepareServerReload(id: String)

    @Query("DELETE FROM period_events WHERE userId = :userId AND syncState = 'RESOLVING'")
    suspend fun deleteUnmatchedResolving(userId: String): Int

    @Query("SELECT COUNT(*) FROM period_events WHERE syncState = 'RESOLVING'")
    fun observeResolvingCount(): Flow<Int>

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

    @Query("UPDATE daily_logs SET syncState = :state WHERE id = :id")
    suspend fun setSyncState(id: String, state: String)

    @Query("UPDATE daily_logs SET syncState = 'RESOLVING', version = -1 WHERE id = :id")
    suspend fun prepareServerReload(id: String)

    @Query("DELETE FROM daily_logs WHERE userId = :userId AND syncState = 'RESOLVING'")
    suspend fun deleteUnmatchedResolving(userId: String): Int

    @Query("SELECT COUNT(*) FROM daily_logs WHERE syncState = 'RESOLVING'")
    fun observeResolvingCount(): Flow<Int>

    @Query("DELETE FROM daily_logs")
    suspend fun clear()
}

@Dao
interface MutationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(mutation: MutationEntity): Long

    @Query("SELECT mutation_queue.* FROM mutation_queue WHERE state = 'PENDING' AND nextAttemptAt <= :now AND NOT EXISTS (SELECT 1 FROM mutation_queue AS earlier WHERE earlier.entityType = mutation_queue.entityType AND earlier.entityId = mutation_queue.entityId AND earlier.state IN ('PENDING', 'IN_FLIGHT', 'FAILED', 'CONFLICT') AND (earlier.createdAt < mutation_queue.createdAt OR (earlier.createdAt = mutation_queue.createdAt AND earlier.id < mutation_queue.id))) ORDER BY createdAt, id LIMIT :limit")
    suspend fun ready(now: String, limit: Int = 50): List<MutationEntity>

    @Query("UPDATE mutation_queue SET state = 'IN_FLIGHT' WHERE id IN (:ids) AND state = 'PENDING'")
    suspend fun claim(ids: List<String>): Int

    @Query("DELETE FROM mutation_queue WHERE clientMutationId IN (:clientMutationIds)")
    suspend fun acknowledge(clientMutationIds: List<String>)

    @Query("UPDATE mutation_queue SET state = 'PENDING', attemptCount = attemptCount + 1, nextAttemptAt = :nextAttemptAt, lastErrorCode = :errorCode WHERE id IN (:ids)")
    suspend fun retry(ids: List<String>, nextAttemptAt: String, errorCode: String)

    @Query("UPDATE mutation_queue SET state = 'FAILED', attemptCount = attemptCount + 1, lastErrorCode = :errorCode WHERE id IN (:ids)")
    suspend fun markFailed(ids: List<String>, errorCode: String)

    @Query("UPDATE mutation_queue SET state = 'PENDING', nextAttemptAt = :now, lastErrorCode = NULL WHERE state = 'FAILED'")
    suspend fun resetFailed(now: String): Int

    @Query("UPDATE mutation_queue SET nextAttemptAt = :now WHERE state = 'PENDING' AND nextAttemptAt > :now")
    suspend fun makePendingReady(now: String): Int

    @Query("UPDATE mutation_queue SET state = 'CONFLICT', lastErrorCode = :reason WHERE clientMutationId IN (:clientMutationIds)")
    suspend fun markConflicted(clientMutationIds: List<String>, reason: String = "VERSION_CONFLICT")

    @Query("UPDATE mutation_queue SET state = 'CONFLICT', lastErrorCode = :reason WHERE id = :id AND state = 'IN_FLIGHT'")
    suspend fun markConflictedById(id: String, reason: String = "VERSION_CONFLICT")

    @Query("UPDATE mutation_queue SET payloadJson = :payloadJson, createdAt = :createdAt, nextAttemptAt = :nextAttemptAt WHERE entityType = :entityType AND entityId = :entityId AND state = 'PENDING' AND attemptCount = 0")
    suspend fun replacePendingPayload(
        entityType: String,
        entityId: String,
        payloadJson: String,
        createdAt: String,
        nextAttemptAt: String,
    ): Int

    @Query("SELECT * FROM mutation_queue WHERE entityType = :entityType AND entityId = :entityId ORDER BY createdAt, id")
    suspend fun forEntity(entityType: String, entityId: String): List<MutationEntity>

    @Query("SELECT COUNT(*) FROM mutation_queue WHERE entityType = :entityType AND entityId = :entityId AND state IN ('PENDING', 'IN_FLIGHT', 'FAILED', 'CONFLICT')")
    suspend fun unresolvedCountForEntity(entityType: String, entityId: String): Int

    @Query("UPDATE mutation_queue SET operation = :operation, payloadJson = :payloadJson, baseVersion = :baseVersion, nextAttemptAt = :nextAttemptAt, lastErrorCode = NULL WHERE id = :id AND state = 'PENDING' AND attemptCount = 0")
    suspend fun replaceUnattempted(
        id: String,
        operation: String,
        payloadJson: String,
        baseVersion: Long,
        nextAttemptAt: String,
    ): Int

    @Query("UPDATE mutation_queue SET state = 'PENDING', nextAttemptAt = :now, lastErrorCode = NULL WHERE entityType = :entityType AND entityId = :entityId AND state = 'FAILED'")
    suspend fun reviveFailedForEntity(entityType: String, entityId: String, now: String): Int

    @Query("UPDATE mutation_queue SET state = 'PENDING' WHERE state = 'IN_FLIGHT'")
    suspend fun releaseInterrupted()

    @Query("SELECT COUNT(*) FROM mutation_queue WHERE state IN ('PENDING', 'IN_FLIGHT', 'FAILED', 'CONFLICT')")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM mutation_queue WHERE state = 'CONFLICT'")
    fun observeConflictCount(): Flow<Int>

    @Query("SELECT * FROM mutation_queue WHERE state = 'CONFLICT' ORDER BY createdAt, id")
    suspend fun conflicts(): List<MutationEntity>

    @Query("DELETE FROM mutation_queue WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun deleteForEntity(entityType: String, entityId: String)

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

    @Query("SELECT * FROM partner_dashboard_cache WHERE cacheKey = 'current'")
    fun observeCurrent(): Flow<PartnerDashboardEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(value: PartnerDashboardEntity)

    @Query("DELETE FROM partner_dashboard_cache")
    suspend fun clear()
}
