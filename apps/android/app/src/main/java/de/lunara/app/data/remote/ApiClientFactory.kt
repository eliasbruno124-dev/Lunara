package de.lunara.app.data.remote

import de.lunara.app.core.network.GatewayCredentials
import de.lunara.app.core.security.SecureCookieJar
import de.lunara.app.security.AuthentikAuthManager
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Route
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.nio.charset.StandardCharsets
import android.os.Looper
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

private class GatewayAuthenticator(
    private val credentials: GatewayCredentials?,
    private val expectedOrigin: GatewayOrigin,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): okhttp3.Request? {
        val gateway = credentials ?: return null
        if (
            !expectedOrigin.matches(response.request.url) ||
            responseCount(response) >= 2 ||
            response.request.header("Authorization")?.startsWith("Basic ") == true
        ) {
            return null
        }
        return response.request.newBuilder()
            .header(
                "Authorization",
                Credentials.basic(gateway.username, gateway.appPassword, StandardCharsets.UTF_8),
            )
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}

internal data class GatewayOrigin(
    val scheme: String,
    val host: String,
    val port: Int,
) {
    fun matches(url: HttpUrl): Boolean =
        scheme == url.scheme && host == url.host && port == url.port

    companion object {
        fun fromBaseUrl(baseUrl: String): GatewayOrigin = baseUrl.toHttpUrl().let {
            GatewayOrigin(it.scheme, it.host, it.port)
        }
    }
}

internal object GatewayHeaderPolicy {
    fun shouldAttach(
        credentialsPresent: Boolean,
        requestMatchesOrigin: Boolean,
        existingAuthorization: String?,
    ): Boolean = credentialsPresent && requestMatchesOrigin && existingAuthorization == null
}

/**
 * A reverse proxy may redirect anonymous API traffic to an HTML login page. Cookies set
 * during that anonymous redirect are not proof of authentication, so the app password is sent
 * preemptively on every request to the configured server origin and never to another origin.
 */
private class PreemptiveGatewayHeaderInterceptor(
    private val credentials: GatewayCredentials?,
    private val bearerProvider: () -> String?,
    private val expectedOrigin: GatewayOrigin,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!expectedOrigin.matches(request.url) || request.header("Authorization") != null) {
            return chain.proceed(request)
        }
        val authorization = GatewayAuthorizationPolicy.value(credentials, bearerProvider())
            ?: return chain.proceed(request)
        return chain.proceed(
            request.newBuilder()
                .header("Authorization", authorization)
                .build(),
        )
    }
}

internal object GatewayAuthorizationPolicy {
    fun value(credentials: GatewayCredentials?, bearerToken: String?): String? =
        bearerToken?.takeIf(String::isNotBlank)?.let { "Bearer $it" }
            ?: credentials?.let {
                Credentials.basic(it.username, it.appPassword, StandardCharsets.UTF_8)
            }
}

private class AppTokenInterceptor(
    private val accessToken: String?,
    private val gatewayEnabled: Boolean,
    private val expectedOrigin: GatewayOrigin,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = accessToken?.takeIf(String::isNotBlank) ?: return chain.proceed(request)
        val header = if (gatewayEnabled) "X-Lunara-Authorization" else "Authorization"
        if (!AppTokenHeaderPolicy.shouldAttach(
                tokenPresent = true,
                requestMatchesOrigin = expectedOrigin.matches(request.url),
                existingHeader = request.header(header),
            )
        ) return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(header, "Bearer $token").build())
    }
}

internal object AppTokenHeaderPolicy {
    fun shouldAttach(
        tokenPresent: Boolean,
        requestMatchesOrigin: Boolean,
        existingHeader: String?,
    ): Boolean = tokenPresent && requestMatchesOrigin && existingHeader == null
}

internal object BlockingTokenBridgePolicy {
    fun requireBackgroundThread(isMainThread: Boolean) {
        check(!isMainThread) { "Synchronous Lunara networking is forbidden on the Android main thread" }
    }
}

@Singleton
class ApiClientFactory @Inject constructor(
    private val cookieJar: SecureCookieJar,
    private val authentik: AuthentikAuthManager,
) {
    private val dispatcher = Dispatcher()

    fun create(
        baseUrl: String,
        gateway: GatewayCredentials?,
        accessToken: String?,
        outerBearerOverride: String? = null,
        useStoredAuthentik: Boolean = true,
    ): LunaraApi {
        val gatewayOrigin = GatewayOrigin.fromBaseUrl(baseUrl)
        val pendingBearer = outerBearerOverride?.takeIf(String::isNotBlank)
        // Pin the provider account at client creation. A queued request from A must never
        // silently pick up B's Authentik bearer after logout/sign-in on the same server.
        val authentikGeneration = if (useStoredAuthentik) authentik.configuredGenerationFor(baseUrl) else null
        val gatewayEnabled = gateway != null || pendingBearer != null ||
            authentikGeneration != null
        val bearerProvider = {
            pendingBearer ?: if (authentikGeneration == null) null else run {
                BlockingTokenBridgePolicy.requireBackgroundThread(
                    Looper.myLooper() == Looper.getMainLooper(),
                )
                runBlocking { authentik.freshAccessTokenFor(baseUrl, authentikGeneration) }
            }
        }
        val client = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .cookieJar(cookieJar.scoped())
            .authenticator(GatewayAuthenticator(gateway, gatewayOrigin))
            .addInterceptor(PreemptiveGatewayHeaderInterceptor(gateway, bearerProvider, gatewayOrigin))
            .addInterceptor(AppTokenInterceptor(accessToken, gatewayEnabled, gatewayOrigin))
            // A REST client must not follow a browser-login redirect and try to parse
            // HTML as JSON. A valid gateway header returns the API response directly.
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://localhost.invalid/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LunaraApi::class.java)
    }

    fun cancelAll() {
        dispatcher.cancelAll()
    }
}
