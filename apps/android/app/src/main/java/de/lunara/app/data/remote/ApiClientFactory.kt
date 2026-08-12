package de.lunara.app.data.remote

import de.lunara.app.core.network.GatewayCredentials
import de.lunara.app.core.security.SecureCookieJar
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private class GatewayAuthenticator(
    private val credentials: GatewayCredentials?,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): okhttp3.Request? {
        val gateway = credentials ?: return null
        if (responseCount(response) >= 2 || response.request.header("Authorization")?.startsWith("Basic ") == true) {
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

internal object GatewayHeaderPolicy {
    fun shouldAttach(
        credentialsPresent: Boolean,
        hasValidCookie: Boolean,
        existingAuthorization: String?,
    ): Boolean = credentialsPresent && !hasValidCookie && existingAuthorization == null
}

/**
 * Authentik's proxy provider can redirect anonymous traffic directly to its HTML login page.
 * Supplying the app-password once, before that redirect, establishes the encrypted session cookie.
 */
private class PreemptiveGatewayHeaderInterceptor(
    private val credentials: GatewayCredentials?,
    private val cookieJar: SecureCookieJar,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val gateway = credentials
        if (!GatewayHeaderPolicy.shouldAttach(gateway != null, cookieJar.hasValidFor(request.url), request.header("Authorization"))) {
            return chain.proceed(request)
        }
        return chain.proceed(
            request.newBuilder()
                .header("Authorization", Credentials.basic(gateway!!.username, gateway.appPassword, StandardCharsets.UTF_8))
                .build(),
        )
    }
}

private class AppTokenInterceptor(
    private val accessToken: String?,
    private val gatewayEnabled: Boolean,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = accessToken?.takeIf(String::isNotBlank) ?: return chain.proceed(chain.request())
        val header = if (gatewayEnabled) "X-Lunara-Authorization" else "Authorization"
        return chain.proceed(chain.request().newBuilder().header(header, "Bearer $token").build())
    }
}

@Singleton
class ApiClientFactory @Inject constructor(
    private val cookieJar: SecureCookieJar,
) {
    fun create(gateway: GatewayCredentials?, accessToken: String?): LunaraApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .cookieJar(cookieJar)
            .authenticator(GatewayAuthenticator(gateway))
            .addInterceptor(PreemptiveGatewayHeaderInterceptor(gateway, cookieJar))
            .addInterceptor(AppTokenInterceptor(accessToken, gateway != null))
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://localhost.invalid/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LunaraApi::class.java)
    }
}
