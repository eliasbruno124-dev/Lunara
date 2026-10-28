package de.lunara.app.security

import android.content.Context
import android.content.Intent
import android.net.Uri
import de.lunara.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lunara.app.data.remote.dto.OidcMetaDto
import de.lunara.app.data.repository.SessionSecrets
import de.lunara.app.data.repository.SessionRecoveryPolicy
import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.NoClientAuthentication
import net.openid.appauth.ResponseTypeValues
import java.net.URI
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AuthentikAuthorizationException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** Only a definitive provider rejection requires a new interactive login, not a network outage. */
class AuthentikSessionExpiredException(cause: Throwable? = null) :
    IOException("Authentik session requires authentication", cause)

/** A queued request must not borrow a replacement account's provider credentials. */
internal class StaleAuthentikSessionException : IOException("Authentik session changed before request completed")

class PendingAuthentikSession internal constructor(
    internal val authState: AuthState,
    val accessToken: String,
)

/**
 * Runs a native-app OAuth Authorization Code flow in the system browser.
 *
 * No client secret is present in the APK. AppAuth creates state, nonce and an
 * S256 PKCE verifier. The serialized AuthState is committed only after the
 * Lunara API accepted the external identity and is kept in Keystore-backed
 * encrypted storage.
 */
@Singleton
class AuthentikAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secrets: SessionSecrets,
) {
    private val authorizationService by lazy { AuthorizationService(context) }
    private val renewal = DurableSessionRenewal()
    private val pendingRequest = AtomicReference<String?>(null)

    suspend fun authorizationIntent(meta: OidcMetaDto): Intent {
        val issuer = canonicalIssuer(meta.issuer)
        val discovery = canonicalDiscoveryUrl(meta.discoveryUrl, issuer)
        val configuration = suspendCancellableCoroutine<AuthorizationServiceConfiguration> { continuation ->
            AuthorizationServiceConfiguration.fetchFromUrl(discovery) { serviceConfiguration, failure ->
                when {
                    failure != null -> continuation.resumeWithException(
                        AuthentikAuthorizationException("OIDC discovery failed", failure),
                    )
                    serviceConfiguration == null -> continuation.resumeWithException(
                        AuthentikAuthorizationException("OIDC discovery returned no configuration"),
                    )
                    else -> continuation.resume(serviceConfiguration)
                }
            }
        }
        val redirectUri = Uri.parse(
            OidcRedirectPolicy.select(
                packageName = context.packageName,
                verifiedAppLinkEnabled = BuildConfig.OIDC_USE_VERIFIED_APP_LINK,
                appLinkHost = BuildConfig.OIDC_APP_LINK_HOST,
            ),
        )
        val clientId = (meta.clientId ?: meta.audience)
            ?.takeIf(String::isNotBlank)
            ?: throw AuthentikAuthorizationException("OIDC client identifier is missing")
        val scopes = meta.scopes
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()
            .plus("openid")
            .joinToString(" ")
        val request = AuthorizationRequest.Builder(
            configuration,
            clientId,
            ResponseTypeValues.CODE,
            redirectUri,
        )
            .setScope(scopes)
            .build()
        if (
            request.codeVerifier.isNullOrBlank() ||
            request.codeVerifierChallenge.isNullOrBlank() ||
            request.codeVerifierChallengeMethod != "S256" ||
            request.state.isNullOrBlank() ||
            request.nonce.isNullOrBlank()
        ) {
            throw AuthentikAuthorizationException("OIDC request lacks required PKCE or replay protection")
        }
        // The result must refer to the exact request created by this process. If Android
        // kills the process while the browser is open, authentication deliberately fails
        // closed and the user starts the short login flow again.
        pendingRequest.set(request.jsonSerializeString())
        return authorizationService.getAuthorizationRequestIntent(request)
    }

    suspend fun completeAuthorization(result: Intent?): PendingAuthentikSession {
        val expectedRequest = pendingRequest.getAndSet(null)
            ?: throw AuthentikAuthorizationException("OIDC authorization session is no longer active")
        val data = result ?: throw AuthentikAuthorizationException("OIDC authorization was cancelled")
        val response = AuthorizationResponse.fromIntent(data)
        val authorizationFailure = AuthorizationException.fromIntent(data)
        if (authorizationFailure != null) {
            throw AuthentikAuthorizationException("OIDC authorization failed", authorizationFailure)
        }
        response ?: throw AuthentikAuthorizationException("OIDC authorization returned no response")
        if (response.request.jsonSerializeString() != expectedRequest) {
            throw AuthentikAuthorizationException("OIDC response does not match the active request")
        }
        if (!OidcRedirectPolicy.isAllowed(
                uri = response.request.redirectUri.toString(),
                packageName = context.packageName,
                verifiedAppLinkEnabled = BuildConfig.OIDC_USE_VERIFIED_APP_LINK,
                appLinkHost = BuildConfig.OIDC_APP_LINK_HOST,
            )
        ) {
            throw AuthentikAuthorizationException("OIDC redirect URI does not match this app build")
        }
        val state = AuthState(response, null)
        val tokenResponse = suspendCancellableCoroutine<net.openid.appauth.TokenResponse> { continuation ->
            authorizationService.performTokenRequest(
                response.createTokenExchangeRequest(),
                NoClientAuthentication.INSTANCE,
            ) { token, failure ->
                state.update(token, failure)
                when {
                    failure != null -> continuation.resumeWithException(
                        AuthentikAuthorizationException("OIDC token exchange failed", failure),
                    )
                    token == null -> continuation.resumeWithException(
                        AuthentikAuthorizationException("OIDC token exchange returned no token"),
                    )
                    else -> continuation.resume(token)
                }
            }
        }
        val accessToken = tokenResponse.accessToken
            ?.takeIf(String::isNotBlank)
            ?: throw AuthentikAuthorizationException("OIDC access token is missing")
        if (state.refreshToken.isNullOrBlank()) {
            throw AuthentikAuthorizationException("OIDC provider did not issue an offline refresh token")
        }
        return PendingAuthentikSession(state, accessToken)
    }

    fun commit(baseUrl: String, pending: PendingAuthentikSession) {
        secrets.replaceAuthentik(
            canonicalBaseUrl(baseUrl),
            pending.authState.jsonSerializeString(),
        )
    }

    fun configuredGenerationFor(baseUrl: String): Long? {
        val snapshot = secrets.authentikSnapshot()
        val expectedBase = runCatching { canonicalBaseUrl(baseUrl) }.getOrNull() ?: return null
        return snapshot.generation.takeIf {
            SessionRecoveryPolicy.normalizedBaseUrl(snapshot.baseUrl) == expectedBase &&
                readState(snapshot.stateJson) != null
        }
    }

    fun isConfiguredFor(baseUrl: String): Boolean = configuredGenerationFor(baseUrl) != null

    /**
     * Returns an access token that AppAuth has checked for expiry first. This is
     * intentionally called before an API request reaches the Authentik proxy:
     * an expired outer token is answered with a browser redirect, so waiting
     * for an inner Lunara 401 would be too late.
     */
    suspend fun freshAccessTokenFor(
        baseUrl: String,
        expectedGeneration: Long? = configuredGenerationFor(baseUrl),
    ): String? = renewal.run {
        val snapshot = secrets.authentikSnapshot()
        val expectedBase = runCatching { canonicalBaseUrl(baseUrl) }.getOrNull() ?: return@run null
        if (expectedGeneration == null) return@run null
        if (snapshot.generation != expectedGeneration) throw StaleAuthentikSessionException()
        if (SessionRecoveryPolicy.normalizedBaseUrl(snapshot.baseUrl) != expectedBase) {
            throw StaleAuthentikSessionException()
        }
        val state = readState(snapshot.stateJson) ?: throw IOException("Stored OIDC session is unreadable")
        val accessToken = suspendCancellableCoroutine<String> { continuation ->
            state.performActionWithFreshTokens(
                authorizationService,
                NoClientAuthentication.INSTANCE,
            ) { accessToken, _, failure ->
                when {
                    failure != null -> continuation.resumeWithException(
                        if (failure.type == AuthorizationException.TYPE_OAUTH_TOKEN_ERROR &&
                            failure.error in setOf("invalid_grant", "invalid_client", "unauthorized_client", "invalid_token")
                        ) AuthentikSessionExpiredException(failure)
                        else IOException("Authentik token refresh could not complete", failure),
                    )
                    accessToken.isNullOrBlank() -> continuation.resumeWithException(
                        IOException("OIDC refresh returned no access token"),
                    )
                    else -> continuation.resume(accessToken)
                }
            }
        }
        if (!secrets.replaceAuthentikStateIfCurrent(
            snapshot.generation,
            state.jsonSerializeString(),
        )) throw StaleAuthentikSessionException()
        // Return this renewal's token, never a new account's token read after releasing the lock.
        accessToken
    }

    fun clear() = secrets.clearAuthentik()

    private fun readState(serialized: String?): AuthState? = serialized?.let {
        runCatching { AuthState.jsonDeserialize(serialized) }.getOrNull()
    }

    private fun canonicalIssuer(raw: String): String {
        val parsed = URI(raw.trim())
        require(parsed.scheme == "https" && parsed.host != null && parsed.userInfo == null)
        require(parsed.query == null && parsed.fragment == null)
        return raw.trim().trimEnd('/') + "/"
    }

    private fun canonicalDiscoveryUrl(raw: String?, canonicalIssuer: String): Uri {
        val expected = URI("${canonicalIssuer}.well-known/openid-configuration")
        val parsed = URI(raw?.trim()?.takeIf(String::isNotBlank) ?: expected.toString())
        require(parsed.scheme == "https" && parsed.host != null && parsed.userInfo == null)
        require(parsed.query == null && parsed.fragment == null)
        // Discovery may be hosted below the issuer path, but never on a different origin.
        require(parsed.host.equals(expected.host, ignoreCase = true) && parsed.port == expected.port)
        return Uri.parse(parsed.toString())
    }

    private fun canonicalBaseUrl(raw: String): String {
        val normalized = requireNotNull(SessionRecoveryPolicy.normalizedBaseUrl(raw))
        // External bearer tokens are never sent over the development HTTP mode.
        require(URI(normalized).scheme == "https")
        return normalized
    }
}
