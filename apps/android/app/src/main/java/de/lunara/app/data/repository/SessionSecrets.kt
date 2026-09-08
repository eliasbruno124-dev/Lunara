package de.lunara.app.data.repository

import de.lunara.app.core.network.GatewayCredentials
import de.lunara.app.core.security.SecretStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionSecrets @Inject constructor(private val store: SecretStore) {
    private var authentikGeneration: Long = 0

    var accessToken: String?
        get() = store.getString(ACCESS)
        set(value) = store.putString(ACCESS, value)
    var refreshToken: String?
        get() = store.getString(REFRESH)
        set(value) = store.putString(REFRESH, value)

    @Synchronized
    fun authentikSnapshot(): AuthentikSecretSnapshot = AuthentikSecretSnapshot(
        generation = authentikGeneration,
        baseUrl = store.getString(AUTHENTIK_BASE_URL),
        stateJson = store.getString(AUTHENTIK_STATE),
    )

    @Synchronized
    fun replaceAuthentik(baseUrl: String, stateJson: String) {
        authentikGeneration++
        store.putString(AUTHENTIK_BASE_URL, baseUrl)
        store.putString(AUTHENTIK_STATE, stateJson)
    }

    /** Refuses a late refresh result after logout, secure wipe or another login. */
    @Synchronized
    fun replaceAuthentikStateIfCurrent(expectedGeneration: Long, stateJson: String): Boolean {
        if (authentikGeneration != expectedGeneration || store.getString(AUTHENTIK_BASE_URL) == null) {
            return false
        }
        store.putString(AUTHENTIK_STATE, stateJson)
        return true
    }

    @Synchronized
    fun clearAuthentik() {
        authentikGeneration++
        store.putString(AUTHENTIK_STATE, null)
        store.putString(AUTHENTIK_BASE_URL, null)
    }

    /** The one-time invitation bearer code is kept only in Android Keystore-backed storage. */
    var activeInvitationJson: String?
        get() = store.getString(ACTIVE_INVITATION)
        set(value) = store.putString(ACTIVE_INVITATION, value)

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
        clearAuthentik()
        activeInvitationJson = null
        pendingInvitationCode = null
        gatewayCredentials = null
    }

    data class AuthentikSecretSnapshot(
        val generation: Long,
        val baseUrl: String?,
        val stateJson: String?,
    )

    private companion object {
        const val ACCESS = "session.access_token"
        const val REFRESH = "session.refresh_token"
        const val AUTHENTIK_STATE = "gateway.authentik_state.v1"
        const val AUTHENTIK_BASE_URL = "gateway.authentik_base_url.v1"
        const val ACTIVE_INVITATION = "relationship.active_invitation.v1"
        const val PENDING_INVITATION = "relationship.pending_invitation.v1"
        const val GATEWAY_USER = "gateway.username"
        const val GATEWAY_PASSWORD = "gateway.app_password"
    }
}
