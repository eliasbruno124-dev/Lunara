package de.lunara.app.data.repository

import de.lunara.app.core.model.UserRole
import de.lunara.app.data.preferences.AppMode
import de.lunara.app.data.preferences.AppPreferences
import de.lunara.app.data.remote.ApiClientFactory
import de.lunara.app.data.remote.LunaraApi
import de.lunara.app.data.remote.dto.CreatePersonalTipRequest
import kotlinx.coroutines.flow.first
import retrofit2.Response
import javax.inject.Inject
import java.util.UUID

class PersonalTipsRepository @Inject constructor(
    private val preferences: AppPreferences,
    private val secrets: SessionSecrets,
    private val apiFactory: ApiClientFactory,
    private val connection: ConnectionRepository,
    private val credentialGate: SessionCredentialGate,
) {
    suspend fun load(userId: String, baseUrl: String) = request(userId, baseUrl) { api, url -> api.personalTips(url) }.requireBody()
    suspend fun create(userId: String, baseUrl: String, draft: CreatePersonalTipRequest) = request(userId, baseUrl) { api, url -> api.createPersonalTip(url, draft) }.requireBody()
    suspend fun disable(userId: String, baseUrl: String, id: String) {
        val safeId = UUID.fromString(id).toString()
        request(userId, baseUrl) { api, url -> api.disablePersonalTip("$url/$safeId") }
    }

    private suspend fun <T> request(userId: String, base: String, call: suspend (LunaraApi, String) -> Response<T>): Response<T> {
        val generation = credentialGate.snapshot()
        suspend fun currentApi() = credentialGate.exclusive {
            val current = preferences.state.first()
            if (credentialGate.snapshot() != generation || current.appMode != AppMode.SERVER || current.baseUrl != base || current.session?.userId != userId || current.session.role != UserRole.TRACKER) throw SessionChangedException()
            apiFactory.create(base, secrets.gatewayCredentials, secrets.accessToken)
        }
        val url = endpoint(base, "api/v1/tips/custom")
        var response = call(currentApi(), url)
        if (response.code() == 401 && connection.refresh()) response = call(currentApi(), url)
        val current = preferences.state.first()
        if (credentialGate.snapshot() != generation || current.baseUrl != base || current.session?.userId != userId || current.appMode != AppMode.SERVER) throw SessionChangedException()
        if (!response.isSuccessful) throw ApiException(response.code())
        return response
    }
}
