package de.lunara.app.data.repository

import android.os.Build
import de.lunara.app.core.model.AppSession
import de.lunara.app.core.model.UserRole
import de.lunara.app.core.network.ParsedServerAddress
import de.lunara.app.core.network.ServerChangeConfirmationRequired
import de.lunara.app.core.network.ServerChangePolicy
import de.lunara.app.core.security.SecureCookieJar
import de.lunara.app.data.preferences.AppPreferences
import de.lunara.app.data.remote.ApiClientFactory
import de.lunara.app.data.remote.dto.AuthResponseDto
import de.lunara.app.data.remote.dto.JoinRequest
import de.lunara.app.data.remote.dto.LoginRequest
import de.lunara.app.data.remote.dto.MetaDto
import de.lunara.app.data.remote.dto.RefreshRequest
import de.lunara.app.data.remote.dto.SetupRequest
import kotlinx.coroutines.flow.first
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionRepository @Inject constructor(
    private val preferences: AppPreferences,
    private val secrets: SessionSecrets,
    private val apiFactory: ApiClientFactory,
    private val cookieJar: SecureCookieJar,
    private val accountRepository: AccountRepository,
) {
    suspend fun testAndSaveConnection(
        address: ParsedServerAddress,
        confirmServerChange: Boolean = false,
    ): MetaDto {
        val oldBase = preferences.state.first().baseUrl
        val switchingServer = ServerChangePolicy.requiresConfirmation(oldBase, address.baseUrl)
        if (switchingServer && !confirmServerChange) throw ServerChangeConfirmationRequired()

        val api = apiFactory.create(address.gatewayCredentials, null)
        val meta = api.meta(endpoint(address.baseUrl, "api/v1/meta")).requireBody()
        if (meta.apiVersion != SUPPORTED_API_VERSION || meta.name.isBlank()) {
            throw ApiException(426, "unsupported_api_version")
        }
        if (switchingServer) {
            // The new endpoint has been validated and the user explicitly confirmed. Revoke the
            // old session best-effort and destroy all old local state before saving anything new.
            accountRepository.eraseLocalAccount()
        } else if (oldBase == null) {
            // Do not let a stale cookie from an abandoned setup attempt cross into this server.
            cookieJar.clear()
        }
        secrets.gatewayCredentials = address.gatewayCredentials
        preferences.saveConnection(address.baseUrl, meta.name, meta.version)
        return meta
    }

    suspend fun login(username: String, password: String): AppSession = authenticate { base, api ->
        api.login(
            endpoint(base, "api/v1/auth/login"),
            LoginRequest(username.trim(), password, deviceName()),
        ).requireBody()
    }

    suspend fun setup(
        bootstrapToken: String,
        householdName: String,
        username: String,
        displayName: String,
        password: String,
        language: String,
    ): AppSession = authenticate { base, api ->
        api.setup(
            endpoint(base, "api/v1/setup"),
            SetupRequest(
                bootstrapToken = bootstrapToken,
                householdName = householdName.trim(),
                username = username.trim(),
                displayName = displayName.trim(),
                password = password,
                preferredLanguage = language.uppercase(Locale.ROOT),
                deviceName = deviceName(),
            ),
        ).requireBody()
    }

    suspend fun join(
        invitationCode: String,
        username: String,
        displayName: String,
        password: String,
        language: String,
    ): AppSession = authenticate { base, api ->
        api.join(
            endpoint(base, "api/v1/household/join"),
            JoinRequest(
                invitationCode = invitationCode.trim().uppercase(Locale.ROOT),
                username = username.trim(),
                displayName = displayName.trim(),
                password = password,
                preferredLanguage = language.uppercase(Locale.ROOT),
                deviceName = deviceName(),
            ),
        ).requireBody()
    }

    suspend fun refresh(): Boolean {
        val state = preferences.state.first()
        val base = state.baseUrl ?: return false
        val refresh = secrets.refreshToken ?: return false
        val response = apiFactory.create(secrets.gatewayCredentials, null).refresh(
            endpoint(base, "api/v1/auth/refresh"),
            RefreshRequest(refresh),
        )
        if (!response.isSuccessful || response.body() == null) return false
        val auth = response.body()!!
        secrets.accessToken = auth.accessToken
        secrets.refreshToken = auth.refreshToken
        return true
    }

    private suspend fun authenticate(
        call: suspend (baseUrl: String, api: de.lunara.app.data.remote.LunaraApi) -> AuthResponseDto,
    ): AppSession {
        val state = preferences.state.first()
        val base = state.baseUrl ?: throw ApiException(400, "server_not_configured")
        val api = apiFactory.create(secrets.gatewayCredentials, null)
        val auth = call(base, api)
        val rawRole = auth.user.role
        val role = runCatching { UserRole.valueOf(rawRole) }.getOrElse {
            throw ApiException(403, "unsupported_role")
        }
        secrets.accessToken = auth.accessToken
        secrets.refreshToken = auth.refreshToken
        preferences.saveSession(auth.user.id, auth.user.username, auth.user.displayName, role)
        return AppSession(
            auth.user.id,
            auth.user.displayName,
            role,
            state.serverName ?: "Lunara",
            state.serverVersion.orEmpty(),
        )
    }

    private fun deviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(80)

    companion object { const val SUPPORTED_API_VERSION = "v1" }
}
