package de.lunara.app.notifications

import com.google.gson.Gson
import de.lunara.app.core.security.SecretStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDeliveryStoreTest {
    @Test
    fun duplicateOccurrenceIsCommittedOnlyAfterSuccessfulPublication() = runTest {
        val store = NotificationDeliveryStore(InMemorySecretStore(), Gson())

        val first = store.reserve(
            NotificationType.PERIOD_WINDOW_STARTS_TOMORROW,
            "period:2026-09-18",
            "2026-09-17",
            2,
        )
        assertNotNull(first)
        assertNull(
            store.reserve(
                NotificationType.PERIOD_WINDOW_STARTS_TOMORROW,
                "period:2026-09-18",
                "2026-09-17",
                2,
            ),
        )

        assertTrue(store.complete(first!!, published = false))
        val retry = store.reserve(
            NotificationType.PERIOD_WINDOW_STARTS_TOMORROW,
            "period:2026-09-18",
            "2026-09-17",
            2,
        )
        assertNotNull(retry)
        assertTrue(store.complete(retry!!, published = true))
        assertNull(
            store.reserve(
                NotificationType.PERIOD_WINDOW_STARTS_TOMORROW,
                "period:2026-09-18",
                "2026-09-17",
                2,
            ),
        )
    }

    @Test
    fun dailyLimitResetsOnNextLocalDate() = runTest {
        val store = NotificationDeliveryStore(InMemorySecretStore(), Gson())

        val first = store.reserve(NotificationType.DAILY_LOG_REMINDER, "log:2026-09-17", "2026-09-17", 1)
        assertNotNull(first)
        assertTrue(store.complete(first!!, published = true))
        assertNull(store.reserve(NotificationType.PARTNER_TIP, "tip:2026-09-17", "2026-09-17", 1))
        assertNotNull(store.reserve(NotificationType.PARTNER_TIP, "tip:2026-09-18", "2026-09-18", 1))
    }

    @Test
    fun pendingReservationCountsTowardLimitButCanBeReleased() = runTest {
        val store = NotificationDeliveryStore(InMemorySecretStore(), Gson())
        val first = store.reserve(NotificationType.DAILY_LOG_REMINDER, "log:2026-09-17", "2026-09-17", 1)

        assertNotNull(first)
        assertNull(store.reserve(NotificationType.PARTNER_TIP, "tip:2026-09-17", "2026-09-17", 1))
        assertTrue(store.complete(first!!, published = false))
        assertNotNull(store.reserve(NotificationType.PARTNER_TIP, "tip:2026-09-17", "2026-09-17", 1))
    }

    @Test
    fun clearRemovesCommittedAndPendingState() = runTest {
        val store = NotificationDeliveryStore(InMemorySecretStore(), Gson())
        val delivered = store.reserve(NotificationType.DAILY_LOG_REMINDER, "log:2026-09-17", "2026-09-17", 2)
        assertTrue(store.complete(delivered!!, published = true))
        assertNotNull(store.reserve(NotificationType.PARTNER_TIP, "tip:2026-09-17", "2026-09-17", 2))

        store.clear()

        assertNotNull(store.reserve(NotificationType.DAILY_LOG_REMINDER, "log:2026-09-17", "2026-09-17", 2))
    }

    private class InMemorySecretStore : SecretStore {
        private val values = mutableMapOf<String, ByteArray>()

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
