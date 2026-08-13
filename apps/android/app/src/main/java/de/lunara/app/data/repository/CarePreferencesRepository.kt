package de.lunara.app.data.repository

import de.lunara.app.data.preferences.AppPreferences
import de.lunara.app.data.remote.ApiClientFactory
import de.lunara.app.data.remote.dto.CarePreferencesDto
import de.lunara.app.data.remote.dto.UpdateCarePreferencesRequest
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CarePreferencesRepository @Inject constructor(
    private val preferences: AppPreferences,
    private val secrets: SessionSecrets,
    private val apiFactory: ApiClientFactory,
    private val connectionRepository: ConnectionRepository,
) {
    suspend fun load(): CarePreferencesDto {
        val base = preferences.state.first().baseUrl ?: throw ApiException(400)
        var response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
            .carePreferences(endpoint(base, "api/v1/care-preferences"))
        if (response.code() == 401 && connectionRepository.refresh()) {
            response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
                .carePreferences(endpoint(base, "api/v1/care-preferences"))
        }
        return response.requireBody()
    }

    suspend fun update(request: UpdateCarePreferencesRequest): CarePreferencesDto {
        val base = preferences.state.first().baseUrl ?: throw ApiException(400)
        var response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
            .updateCarePreferences(endpoint(base, "api/v1/care-preferences"), request)
        if (response.code() == 401 && connectionRepository.refresh()) {
            response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
                .updateCarePreferences(endpoint(base, "api/v1/care-preferences"), request)
        }
        return response.requireBody()
    }
}
