package de.lunara.app.core.network

import java.net.IDN
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class GatewayCredentials(val username: String, val appPassword: String)

data class ParsedServerAddress(
    val baseUrl: String,
    val gatewayCredentials: GatewayCredentials?,
)

sealed class ServerAddressException(message: String) : IllegalArgumentException(message) {
    data object InvalidUrl : ServerAddressException("invalid_url")
    data object HttpsRequired : ServerAddressException("https_required")
    data object IncompleteCredentials : ServerAddressException("incomplete_credentials")
    data object CredentialsRequireHttps : ServerAddressException("credentials_require_https")
}

object ServerAddressParser {
    fun parse(raw: String, allowInsecureHttp: Boolean): ParsedServerAddress {
        val candidate = raw.trim()
        if (candidate.isBlank() || candidate.any { it == '\r' || it == '\n' }) {
            throw ServerAddressException.InvalidUrl
        }

        val uri = runCatching { URI(candidate) }.getOrElse { throw ServerAddressException.InvalidUrl }
        val scheme = uri.scheme?.lowercase() ?: throw ServerAddressException.InvalidUrl
        if (scheme != "https" && scheme != "http") throw ServerAddressException.InvalidUrl
        if (scheme == "http" && !allowInsecureHttp) throw ServerAddressException.HttpsRequired
        if (uri.host.isNullOrBlank() || uri.fragment != null || uri.query != null) {
            throw ServerAddressException.InvalidUrl
        }

        val userInfo = uri.rawUserInfo
        val credentials = userInfo?.let {
            val separator = it.indexOf(':')
            if (separator <= 0 || separator == it.lastIndex) {
                throw ServerAddressException.IncompleteCredentials
            }
            val username = decode(it.substring(0, separator))
            val appPassword = decode(it.substring(separator + 1))
            if (
                username.isBlank() || appPassword.isBlank() ||
                username.length > MAX_GATEWAY_USERNAME_LENGTH ||
                appPassword.length > MAX_GATEWAY_PASSWORD_LENGTH ||
                ':' in username ||
                username.any(Char::isISOControl) || appPassword.any(Char::isISOControl)
            ) {
                throw ServerAddressException.IncompleteCredentials
            }
            if (scheme != "https") throw ServerAddressException.CredentialsRequireHttps
            GatewayCredentials(username, appPassword)
        }

        val host = runCatching { IDN.toASCII(uri.host.lowercase()) }
            .getOrElse { throw ServerAddressException.InvalidUrl }
        val port = when {
            uri.port == -1 -> ""
            scheme == "https" && uri.port == 443 -> ""
            scheme == "http" && uri.port == 80 -> ""
            uri.port in 1..65535 -> ":${uri.port}"
            else -> throw ServerAddressException.InvalidUrl
        }
        val enteredPath = uri.rawPath.orEmpty().trimEnd('/')
        val path = enteredPath.removeSuffix(META_ENDPOINT)
        val sanitized = "$scheme://$host$port${if (path.isBlank()) "" else path}/"
        return ParsedServerAddress(sanitized, credentials)
    }

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
    }.getOrElse { throw ServerAddressException.InvalidUrl }

    private const val META_ENDPOINT = "/api/v1/meta"
    private const val MAX_GATEWAY_USERNAME_LENGTH = 128
    private const val MAX_GATEWAY_PASSWORD_LENGTH = 512
}

object ServerChangePolicy {
    fun requiresConfirmation(currentBaseUrl: String?, requestedBaseUrl: String): Boolean =
        currentBaseUrl != null && currentBaseUrl != requestedBaseUrl
}

class ServerChangeConfirmationRequired : IllegalStateException("server_change_confirmation_required")
