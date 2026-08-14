package de.lunara.app.notifications

import com.google.gson.Gson
import de.lunara.app.core.security.SecretStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keeps health-derived reminder dates in Android Keystore-backed encrypted storage. */
@Singleton
class NotificationDeliveryStore @Inject constructor(
    private val secretStore: SecretStore,
    private val gson: Gson,
) {
    private val mutex = Mutex()
    private val pending = mutableSetOf<ReservationIdentity>()

    suspend fun reserve(
        type: NotificationType,
        occurrenceKey: String,
        localDate: String,
        maxPerDay: Int,
    ): DeliveryReservation? = mutex.withLock {
        val current = readState()
        val identity = ReservationIdentity(type, occurrenceKey, localDate)
        if (current.delivered[type.name] == occurrenceKey || identity in pending) return@withLock null
        val count = if (current.localDate == localDate) current.count else 0
        val pendingToday = pending.count { it.localDate == localDate }
        val limit = maxPerDay.coerceIn(1, 3)
        if (count + pendingToday >= limit) return@withLock null
        pending += identity
        DeliveryReservation(identity, limit)
    }

    /** Commits the occurrence only after Android accepted the notification for publication. */
    suspend fun complete(reservation: DeliveryReservation, published: Boolean): Boolean = mutex.withLock {
        if (!pending.remove(reservation.identity)) return@withLock false
        if (!published) return@withLock true

        val identity = reservation.identity
        val current = readState()
        if (current.delivered[identity.type.name] == identity.occurrenceKey) return@withLock true
        val count = if (current.localDate == identity.localDate) current.count else 0
        if (count >= reservation.maxPerDay) return@withLock false
        val updated = current.copy(
            localDate = identity.localDate,
            count = count + 1,
            delivered = current.delivered + (identity.type.name to identity.occurrenceKey),
        )
        secretStore.putString(STORAGE_KEY, gson.toJson(updated))
        true
    }

    suspend fun clear() = mutex.withLock {
        pending.clear()
        secretStore.putString(STORAGE_KEY, null)
    }

    private fun readState(): DeliveryState = secretStore.getString(STORAGE_KEY)
        ?.let { encoded -> runCatching { gson.fromJson(encoded, DeliveryState::class.java) }.getOrNull() }
        ?: DeliveryState()

    class DeliveryReservation internal constructor(
        internal val identity: ReservationIdentity,
        internal val maxPerDay: Int,
    )

    internal data class ReservationIdentity(
        val type: NotificationType,
        val occurrenceKey: String,
        val localDate: String,
    )

    private data class DeliveryState(
        val localDate: String? = null,
        val count: Int = 0,
        val delivered: Map<String, String> = emptyMap(),
    )

    private companion object {
        const val STORAGE_KEY = "notifications.delivery-state.v1"
    }
}
