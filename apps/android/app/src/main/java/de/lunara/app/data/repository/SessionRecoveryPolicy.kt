package de.lunara.app.data.repository

import de.lunara.app.core.model.UserRole
import de.lunara.app.data.preferences.AppMode
import de.lunara.app.data.preferences.AppPreferencesState
import java.net.URI

class SessionRecoveryCancelledException : Exception("Session recovery was cancelled")

internal data class RecoveryIdentity(val baseUrl: String, val userId: String, val role: UserRole)

/** Recovery renews credentials for the existing vault; it never changes its owner or scope. */
internal object SessionRecoveryPolicy {
    fun normalizedBaseUrl(raw: String?): String? = raw?.let {
        runCatching {
            val uri = URI(it.trim()).normalize()
            require(uri.scheme.equals("https", true) || uri.scheme.equals("http", true))
            require(uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null)
            require(uri.port == -1 || uri.port in 1..65535)
            val scheme = uri.scheme.lowercase(java.util.Locale.ROOT)
            val port = when {
                scheme == "https" && uri.port == 443 -> -1
                scheme == "http" && uri.port == 80 -> -1
                else -> uri.port
            }
            val origin = URI(scheme, null, uri.host.lowercase(java.util.Locale.ROOT), port, null, null, null)
            origin.toASCIIString() + uri.rawPath.orEmpty().trimEnd('/') + "/"
        }.getOrNull()
    }

    fun identity(state: AppPreferencesState): RecoveryIdentity? {
        if (state.appMode != AppMode.SERVER) return null
        val base = normalizedBaseUrl(state.baseUrl) ?: return null
        val session = state.session ?: return null
        return RecoveryIdentity(base, session.userId, session.role)
    }

    fun markerMatches(
        mode: AppMode,
        baseUrl: String?, userId: String?, role: UserRole?,
        markedBaseUrl: String?, markedUserId: String?, markedRole: String?,
    ): Boolean = mode == AppMode.SERVER && userId != null && role != null && markedBaseUrl != null &&
        normalizedBaseUrl(baseUrl) == markedBaseUrl && userId == markedUserId && role.name == markedRole

    fun canCommit(
        expected: RecoveryIdentity,
        current: RecoveryIdentity?,
        returned: RecoveryIdentity,
        expectedGeneration: Long,
        currentGeneration: Long,
        cancelled: Boolean = false,
    ): Boolean = !cancelled && expectedGeneration == currentGeneration &&
        expected == current && expected == returned
}
