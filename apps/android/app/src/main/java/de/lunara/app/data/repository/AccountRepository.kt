package de.lunara.app.data.repository

import de.lunara.app.data.preferences.AppPreferences
import de.lunara.app.data.remote.ApiClientFactory
import de.lunara.app.data.remote.dto.LogoutRequest
import kotlinx.coroutines.flow.first
import de.lunara.app.core.security.SecureWipeCoordinator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val preferences: AppPreferences,
    private val secrets: SessionSecrets,
    private val apiFactory: ApiClientFactory,
    private val localDataWiper: LocalDataWiper,
    private val credentialGate: SessionCredentialGate,
) {
    suspend fun eraseLocalAccount() {
        // Reject any login/refresh response that was already in flight before
        // logout began. Stored credentials remain available for the one
        // best-effort server-side revocation below.
        credentialGate.invalidate()
        // Server revocation is best-effort: a lost connection must never prevent
        // the promised local health-data and token wipe.
        val state = preferences.state.first()
        val refresh = secrets.refreshToken
        SecureWipeCoordinator.execute(
            bestEffortRemoteLogout = {
                if (state.baseUrl != null && refresh != null) {
                    apiFactory.create(state.baseUrl, secrets.gatewayCredentials, secrets.accessToken)
                        .logout(endpoint(state.baseUrl, "api/v1/auth/logout"), LogoutRequest(refresh))
                }
            },
            localSteps = listOf({ localDataWiper.wipe() }),
        )
    }
}
