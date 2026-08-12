package de.lunara.app.data.repository

import de.lunara.app.core.network.GatewayCredentials
import de.lunara.app.core.security.SecretStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionSecrets @Inject constructor(private val store: SecretStore) {
    var accessToken: String?
        get() = store.getString(ACCESS)
        set(value) = store.putString(ACCESS, value)
    var refreshToken: String?
        get() = store.getString(REFRESH)
        set(value) = store.putString(REFRESH, value)

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

    fun clearAccountSecrets() {
        accessToken = null
        refreshToken = null
        gatewayCredentials = null
    }

    private companion object {
        const val ACCESS = "session.access_token"
        const val REFRESH = "session.refresh_token"
        const val GATEWAY_USER = "gateway.username"
        const val GATEWAY_PASSWORD = "gateway.app_password"
    }
}
