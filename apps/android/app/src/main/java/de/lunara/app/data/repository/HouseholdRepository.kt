package de.lunara.app.data.repository

import de.lunara.app.data.preferences.AppPreferences
import de.lunara.app.data.remote.ApiClientFactory
import de.lunara.app.data.remote.dto.CreateInvitationRequest
import de.lunara.app.data.remote.dto.InvitationDto
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HouseholdRepository @Inject constructor(
    private val preferences: AppPreferences,
    private val secrets: SessionSecrets,
    private val apiFactory: ApiClientFactory,
    private val connectionRepository: ConnectionRepository,
) {
    suspend fun createInvitation(): InvitationDto {
        val base = preferences.state.first().baseUrl ?: throw ApiException(400)
        var response = apiFactory.create(secrets.gatewayCredentials, secrets.accessToken)
            .createInvitation(endpoint(base, "api/v1/household/invitations"), CreateInvitationRequest())
        if (response.code() == 401 && connectionRepository.refresh()) {
            response = apiFactory.create(secrets.gatewayCredentials, secrets.accessToken)
                .createInvitation(endpoint(base, "api/v1/household/invitations"), CreateInvitationRequest())
        }
        return response.requireBody()
    }
}
