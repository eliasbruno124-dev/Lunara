package de.lunara.app.data.repository

import com.google.gson.Gson
import de.lunara.app.data.preferences.AppPreferences
import de.lunara.app.data.remote.ApiClientFactory
import de.lunara.app.data.remote.dto.CreateInvitationRequest
import de.lunara.app.data.remote.dto.InvitationDto
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import java.time.Instant
import de.lunara.app.data.preferences.AppMode

@Singleton
class HouseholdRepository @Inject constructor(
    private val preferences: AppPreferences,
    private val secrets: SessionSecrets,
    private val apiFactory: ApiClientFactory,
    private val connectionRepository: ConnectionRepository,
    private val gson: Gson,
    private val credentialGate: SessionCredentialGate,
) {
    suspend fun cachedRelationship(expectedUserId: String): RelationshipState? {
        val state = preferences.state.first()
        if (state.appMode != AppMode.SERVER || state.session?.userId != expectedUserId) return null
        val base = state.baseUrl ?: return null
        val snapshot = secrets.relationshipJson?.let {
            runCatching { gson.fromJson(it, RelationshipSnapshot::class.java) }.getOrNull()
        }
        return RelationshipPolicy.cached(snapshot, base, expectedUserId)
    }

    /** Both roles can read their relationship; invitation permissions must not gate it. */
    suspend fun relationship(expectedUserId: String): RelationshipState {
        val generation = credentialGate.snapshot()
        val initial = preferences.state.first()
        val base = initial.baseUrl ?: throw ApiException(400)
        if (initial.appMode != AppMode.SERVER || initial.session?.userId != expectedUserId) throw SessionChangedException()
        suspend fun currentApi() = credentialGate.exclusive {
            val current = preferences.state.first()
            if (credentialGate.snapshot() != generation || current.appMode != AppMode.SERVER || current.baseUrl != base || current.session?.userId != expectedUserId) throw SessionChangedException()
            apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
        }
        var response = currentApi()
            .relationship(endpoint(base, "api/v1/household"))
        if (response.code() == 401 && connectionRepository.refresh()) {
            response = currentApi()
                .relationship(endpoint(base, "api/v1/household"))
        }
        val result = RelationshipPolicy.fromServer(response.requireBody(), expectedUserId, Instant.now().toString())
        val committed = credentialGate.commitIfCurrent(generation) {
            val current = preferences.state.first()
            if (current.appMode != AppMode.SERVER || current.baseUrl != base || current.session?.userId != expectedUserId) {
                return@commitIfCurrent false
            }
            secrets.relationshipJson = gson.toJson(RelationshipSnapshot(base, expectedUserId, result))
            true
        }
        if (!committed) throw SessionChangedException()
        return result
    }

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
        return InvitationOverview(merged)
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
)

internal object InvitationCachePolicy {
    fun merge(remote: InvitationDto?, cached: InvitationDto?): InvitationDto? = when {
        remote == null -> null
        cached?.id == remote.id && !cached.code.isNullOrBlank() -> remote.copy(code = cached.code)
        else -> remote
    }
}
