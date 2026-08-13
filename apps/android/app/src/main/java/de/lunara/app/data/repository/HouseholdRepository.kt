package de.lunara.app.data.repository

import com.google.gson.Gson
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
    private val gson: Gson,
) {
    internal suspend fun invitationOverview(): InvitationOverview {
        val base = preferences.state.first().baseUrl ?: throw ApiException(400)
        var api = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
        var response = api
            .openInvitations(endpoint(base, "api/v1/household/invitations"))
        if (response.code() == 401 && connectionRepository.refresh()) {
            api = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
            response = api
                .openInvitations(endpoint(base, "api/v1/household/invitations"))
        }
        val remote = response.requireBody().firstOrNull()
        val cached = secrets.activeInvitationJson?.let { raw ->
            runCatching { gson.fromJson(raw, InvitationDto::class.java) }.getOrNull()
        }
        val merged = InvitationCachePolicy.merge(remote, cached)
        secrets.activeInvitationJson = merged?.takeIf { it.code != null }?.let(gson::toJson)
        var relationshipResponse = api.relationship(endpoint(base, "api/v1/household"))
        if (relationshipResponse.code() == 401 && connectionRepository.refresh()) {
            relationshipResponse = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
                .relationship(endpoint(base, "api/v1/household"))
        }
        val partnerConnected = relationshipResponse.requireBody().members.any { it.role == "PARTNER" }
        return InvitationOverview(merged, partnerConnected)
    }

    suspend fun createInvitation(): InvitationDto {
        val base = preferences.state.first().baseUrl ?: throw ApiException(400)
        var response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
            .createInvitation(endpoint(base, "api/v1/household/invitations"), CreateInvitationRequest())
        if (response.code() == 401 && connectionRepository.refresh()) {
            response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
                .createInvitation(endpoint(base, "api/v1/household/invitations"), CreateInvitationRequest())
        }
        return response.requireBody().also { invitation ->
            secrets.activeInvitationJson = gson.toJson(invitation)
        }
    }

    suspend fun revokeInvitation(invitationId: String) {
        require(invitationId.isNotBlank())
        val base = preferences.state.first().baseUrl ?: throw ApiException(400)
        val url = endpoint(base, "api/v1/household/invitations/$invitationId")
        var response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
            .revokeInvitation(url)
        if (response.code() == 401 && connectionRepository.refresh()) {
            response = apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
                .revokeInvitation(url)
        }
        if (!response.isSuccessful) throw ApiException(response.code())
        secrets.activeInvitationJson = null
    }
}

internal data class InvitationOverview(
    val invitation: InvitationDto?,
    val partnerConnected: Boolean,
)

internal object InvitationCachePolicy {
    fun merge(remote: InvitationDto?, cached: InvitationDto?): InvitationDto? = when {
        remote == null -> null
        cached?.id == remote.id && !cached.code.isNullOrBlank() -> remote.copy(code = cached.code)
        else -> remote
    }
}
