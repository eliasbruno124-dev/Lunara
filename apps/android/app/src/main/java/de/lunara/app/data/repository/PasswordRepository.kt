package de.lunara.app.data.repository

import de.lunara.app.data.preferences.AppPreferences
import de.lunara.app.data.remote.ApiClientFactory
import de.lunara.app.data.remote.dto.ChangePasswordRequest
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PasswordRepository @Inject constructor(
    private val preferences: AppPreferences,
    private val secrets: SessionSecrets,
    private val apiFactory: ApiClientFactory,
    private val connectionRepository: ConnectionRepository,
) {
    suspend fun changePassword(currentPassword: String, newPassword: String) {
        val state = preferences.state.first()
        val base = state.baseUrl ?: throw ApiException(400, "server_not_configured")
        if (state.session == null) throw ApiException(401, "session_required")
        val request = ChangePasswordRequest(currentPassword, newPassword)

        var response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
            .changePassword(endpoint(base, "api/v1/auth/change-password"), request)
        if (response.code() == 401 && connectionRepository.refresh()) {
            response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
                .changePassword(endpoint(base, "api/v1/auth/change-password"), request)
        }
        if (!response.isSuccessful) throw ApiException(response.code())
    }
}
