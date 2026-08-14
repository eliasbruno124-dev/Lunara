package de.lunara.app.notifications

import com.google.gson.Gson
import de.lunara.app.core.security.SecretStore
import de.lunara.app.data.remote.dto.PredictionDto
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Stores health-derived notification inputs only in Android Keystore-backed encrypted storage. */
@Singleton
class NotificationDataCache @Inject constructor(
    private val secretStore: SecretStore,
    private val gson: Gson,
) {
    private var clock: Clock = Clock.systemUTC()

    internal constructor(secretStore: SecretStore, gson: Gson, clock: Clock) : this(secretStore, gson) {
        this.clock = clock
    }

    @Synchronized
    fun saveTrackerPrediction(ownerUserId: String, prediction: PredictionDto) {
        require(ownerUserId.isNotBlank())
        secretStore.putString(
            TRACKER_PREDICTION_KEY,
            gson.toJson(
                TrackerPredictionRecord(
                    ownerUserId = ownerUserId,
                    fetchedAt = clock.instant().toString(),
                    prediction = prediction,
                ),
            ),
        )
    }

    @Synchronized
    fun trackerPrediction(ownerUserId: String): PredictionDto? {
        if (ownerUserId.isBlank()) return null
        val record = secretStore.getString(TRACKER_PREDICTION_KEY)
            ?.let { encoded ->
                runCatching { gson.fromJson(encoded, TrackerPredictionRecord::class.java) }.getOrNull()
            }
            ?: return null
        if (record.ownerUserId != ownerUserId) return null
        val fetchedAt = runCatching { Instant.parse(record.fetchedAt) }.getOrNull() ?: return null
        val age = Duration.between(fetchedAt, clock.instant())
        if (age.isNegative || age > MAX_TRACKER_PREDICTION_AGE) return null
        return record.prediction
    }

    @Synchronized
    fun clear() {
        secretStore.putString(TRACKER_PREDICTION_KEY, null)
    }

    private data class TrackerPredictionRecord(
        val ownerUserId: String,
        val fetchedAt: String,
        val prediction: PredictionDto,
    )

    private companion object {
        const val TRACKER_PREDICTION_KEY = "notifications.tracker-prediction.v1"
        val MAX_TRACKER_PREDICTION_AGE: Duration = Duration.ofDays(14)
    }
}
