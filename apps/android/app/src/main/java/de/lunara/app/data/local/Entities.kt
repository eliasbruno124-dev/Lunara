package de.lunara.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "period_events", indices = [Index("startDate"), Index("syncState")])
data class PeriodEntity(
    @PrimaryKey val id: String,
    val userId: String,
    /** ISO-8601 calendar date. It is intentionally not an instant. */
    val startDate: String,
    val endDate: String? = null,
    val source: String = "MANUAL",
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
    val version: Long = 0,
    val syncState: String = "PENDING",
)

@Entity(
    tableName = "daily_logs",
    indices = [Index(value = ["userId", "localDate"], unique = true), Index("syncState")],
)
data class DailyLogEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val localDate: String,
    val flowLevel: String? = null,
    val painLevel: Int? = null,
    val mood: String? = null,
    val energy: Int? = null,
    val sleepQuality: Int? = null,
    val bodyTemperature: Double? = null,
    val symptomsJson: String = "[]",
    val encryptedNote: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
    val version: Long = 0,
    val syncState: String = "PENDING",
)

@Entity(
    tableName = "mutation_queue",
    indices = [Index(value = ["clientMutationId"], unique = true), Index(value = ["state", "nextAttemptAt"])],
)
data class MutationEntity(
    @PrimaryKey val id: String,
    val clientMutationId: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val payloadJson: String,
    val baseVersion: Long,
    val createdAt: String,
    val attemptCount: Int = 0,
    val nextAttemptAt: String,
    val state: String = "PENDING",
    val lastErrorCode: String? = null,
)

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val deviceKey: String = "this-device",
    val cursor: String? = null,
    val lastSuccessAt: String? = null,
)

@Entity(tableName = "partner_dashboard_cache")
data class PartnerDashboardEntity(
    @PrimaryKey val cacheKey: String = "current",
    val payloadJson: String,
    val updatedAt: String,
)
