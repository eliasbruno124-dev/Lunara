package de.lunara.app.data.repository

import com.google.gson.Gson
import de.lunara.app.core.network.GatewayCredentials
import de.lunara.app.core.security.SecretStore
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionSecrets @Inject constructor(private val store: SecretStore) {
    private var authentikGeneration: Long = 0
    private val gson = Gson()
    private var authentikPersistencePending = false
    private var refreshRequestPersistencePending = false

    var accessToken: String?
        get() = store.getString(ACCESS)
        set(value) = store.putString(ACCESS, value)
    var refreshToken: String?
        get() = store.getString(REFRESH)
        set(value) = store.putString(REFRESH, value)

    /** A logical rotation keeps its request ID through network loss and process restart. */
    @Synchronized
    fun refreshRequestIdFor(refreshToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(refreshToken.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val stored = store.getString(REFRESH_REQUEST)?.split(':', limit = 2)
        if (stored?.size == 2 && stored[0] == digest) {
            val requestId = runCatching { UUID.fromString(stored[1]).toString() }.getOrNull()
            if (requestId != null && requestId == stored[1]) {
                if (refreshRequestPersistencePending) persistRefreshRequest("$digest:$requestId")
                return requestId
            }
        }
        return UUID.randomUUID().toString().also { persistRefreshRequest("$digest:$it") }
    }

    @Synchronized
    fun clearRefreshRequestId() = persistRefreshRequest(null)

    private fun persistRefreshRequest(serialized: String?) {
        // Never send a replay UUID that exists only in process memory after commit failure.
        refreshRequestPersistencePending = true
        store.putString(REFRESH_REQUEST, serialized)
        refreshRequestPersistencePending = false
    }

    @Synchronized
    fun authentikSnapshot(): AuthentikSecretSnapshot {
        val persisted = if (store.contains(AUTHENTIK_SESSION)) {
            // Presence is independent of decryption success. A corrupt encrypted record,
            // malformed payload or logout tombstone must never resurrect legacy secrets.
            val serialized = store.getString(AUTHENTIK_SESSION)
            runCatching { gson.fromJson(serialized, PersistedAuthentikSession::class.java) }.getOrNull()
        } else {
            val base = store.getString(AUTHENTIK_BASE_URL)
            val state = store.getString(AUTHENTIK_STATE)
            // Read-only inspection is also used while creating UI clients. Migrate only
            // during the next durable renewal on IO, not during this snapshot read.
            if (base != null && state != null) PersistedAuthentikSession(base, state) else null
        }
        val valid = persisted?.takeIf { !it.baseUrl.isNullOrBlank() && !it.stateJson.isNullOrBlank() }
        return AuthentikSecretSnapshot(authentikGeneration, valid?.baseUrl, valid?.stateJson)
    }

    @Synchronized
    fun replaceAuthentik(baseUrl: String, stateJson: String) {
        require(baseUrl.isNotBlank() && stateJson.isNotBlank())
        authentikGeneration++
        // One encrypted preference commit binds provider credentials to their destination.
        // A process death must not leave account A's token paired with server B's URL.
        persistAuthentikRecord(gson.toJson(PersistedAuthentikSession(baseUrl, stateJson)))
        clearLegacyAuthentik()
    }

    /** Refuses a late refresh result after logout, secure wipe or another login. */
    @Synchronized
    fun replaceAuthentikStateIfCurrent(expectedGeneration: Long, stateJson: String): Boolean {
        if (authentikGeneration != expectedGeneration) return false
        val snapshot = authentikSnapshot()
        val base = snapshot.baseUrl ?: return false
        require(stateJson.isNotBlank())
        val migratingLegacy = !store.contains(AUTHENTIK_SESSION)
        if (snapshot.stateJson != stateJson || migratingLegacy || authentikPersistencePending) {
            persistAuthentikRecord(gson.toJson(PersistedAuthentikSession(base, stateJson)))
        }
        if (migratingLegacy) clearLegacyAuthentik()
        return true
    }

    @Synchronized
    fun clearAuthentik() {
        authentikGeneration++
        // Commit the tombstone before removing migration sources, so an interrupted logout
        // cannot rediscover the old v1 session on the next process start.
        persistAuthentikRecord("{}")
        clearLegacyAuthentik()
    }

    private fun persistAuthentikRecord(serialized: String) {
        // SharedPreferences.commit() can update process-visible memory before failing its
        // disk write. Equality with that memory is not proof of durability: the next identical
        // renewal must retry the checked write before it may report success.
        authentikPersistencePending = true
        store.putString(AUTHENTIK_SESSION, serialized)
        authentikPersistencePending = false
    }

    private fun clearLegacyAuthentik() {
        store.putString(AUTHENTIK_STATE, null)
        store.putString(AUTHENTIK_BASE_URL, null)
    }

    /** The one-time invitation bearer code is kept only in Android Keystore-backed storage. */
    var activeInvitationJson: String?
        get() = store.getString(ACTIVE_INVITATION)
        set(value) = store.putString(ACTIVE_INVITATION, value)

    /** Names/relationship state are private too; scope is checked before exposing this cache. */
    var relationshipJson: String?
        get() = store.getString(RELATIONSHIP)
        set(value) = store.putString(RELATIONSHIP, value)

    /** Pending inbound relationship proof for an AppAuth round trip; never put this in saved UI state. */
    var pendingInvitationCode: String?
        get() = store.getString(PENDING_INVITATION)
        set(value) = store.putString(PENDING_INVITATION, value)

    var gatewayCredentials: GatewayCredentials?
        get() {
            val user = store.getString(GATEWAY_USER) ?: return null
            val password = store.getString(GATEWAY_PASSWORD) ?: return null
            return GatewayCredentials(user, password)
        }
        set(value) {
            store.putString(GATEWAY_USER, value?.username)
            store.putString(GATEWAY_PASSWORD, value?.appPassword)
        }

    @Synchronized
    fun clearAccountSecrets() {
        accessToken = null
        refreshToken = null
        clearRefreshRequestId()
        clearAuthentik()
        activeInvitationJson = null
        relationshipJson = null
        pendingInvitationCode = null
        gatewayCredentials = null
    }

    data class AuthentikSecretSnapshot(
        val generation: Long,
        val baseUrl: String?,
        val stateJson: String?,
    )

    private data class PersistedAuthentikSession(val baseUrl: String?, val stateJson: String?)

    private companion object {
        const val ACCESS = "session.access_token"
        const val REFRESH = "session.refresh_token"
        const val REFRESH_REQUEST = "session.refresh_request.v1"
        const val AUTHENTIK_SESSION = "gateway.authentik_session.v2"
        const val AUTHENTIK_STATE = "gateway.authentik_state.v1"
        const val AUTHENTIK_BASE_URL = "gateway.authentik_base_url.v1"
        const val ACTIVE_INVITATION = "relationship.active_invitation.v1"
        const val RELATIONSHIP = "relationship.snapshot.v1"
        const val PENDING_INVITATION = "relationship.pending_invitation.v1"
        const val GATEWAY_USER = "gateway.username"
        const val GATEWAY_PASSWORD = "gateway.app_password"
    }
}
