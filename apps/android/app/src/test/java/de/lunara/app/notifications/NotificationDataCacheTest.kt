package de.lunara.app.notifications

import com.google.gson.Gson
import de.lunara.app.core.security.SecretStore
import de.lunara.app.data.remote.dto.PredictionDto
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationDataCacheTest {
    private val instant = Instant.parse("2026-08-14T08:00:00Z")
    private val prediction = PredictionDto(
        predictedStartFrom = "2026-08-15",
        predictedStartTo = "2026-08-17",
        confidence = "LOW",
    )

    @Test
    fun trackerPredictionIsBoundToOwnerAndExpires() {
        val secrets = InMemorySecretStore()
        NotificationDataCache(secrets, Gson(), fixedClock(instant)).saveTrackerPrediction("tracker-a", prediction)

        assertEquals(
            prediction,
            NotificationDataCache(secrets, Gson(), fixedClock(instant.plusSeconds(60))).trackerPrediction("tracker-a"),
        )
        assertNull(NotificationDataCache(secrets, Gson(), fixedClock(instant)).trackerPrediction("tracker-b"))
        assertNull(
            NotificationDataCache(
                secrets,
                Gson(),
                fixedClock(instant.plusSeconds(15 * 24 * 60 * 60L)),
            ).trackerPrediction("tracker-a"),
        )
    }

    @Test
    fun malformedOrClearedCacheFailsClosed() {
        val secrets = InMemorySecretStore()
        secrets.putString("notifications.tracker-prediction.v1", "not-json")
        val cache = NotificationDataCache(secrets, Gson(), fixedClock(instant))
        assertNull(cache.trackerPrediction("tracker-a"))

        cache.saveTrackerPrediction("tracker-a", prediction)
        cache.clear()
        assertNull(cache.trackerPrediction("tracker-a"))
    }

    private fun fixedClock(value: Instant): Clock = Clock.fixed(value, ZoneOffset.UTC)

    private class InMemorySecretStore : SecretStore {
        private val values = mutableMapOf<String, ByteArray>()
        override fun contains(key: String): Boolean = values.containsKey(key)

        override fun putString(key: String, value: String?) = putBytes(key, value?.encodeToByteArray())
        override fun getString(key: String): String? = getBytes(key)?.decodeToString()

        override fun putBytes(key: String, value: ByteArray?) {
            if (value == null) values.remove(key) else values[key] = value.copyOf()
            value?.fill(0)
        }

        override fun getBytes(key: String): ByteArray? = values[key]?.copyOf()
        override fun clear() = values.clear()
    }
}
