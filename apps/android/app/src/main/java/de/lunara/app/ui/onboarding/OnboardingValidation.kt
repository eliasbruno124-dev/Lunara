package de.lunara.app.ui.onboarding

import de.lunara.app.security.isValidExistingPassword
import de.lunara.app.security.isValidNewAccountPassword
import de.lunara.app.data.repository.ApiException
import de.lunara.app.data.remote.dto.MetaDto
import java.net.URI
import java.util.Locale

private val usernamePattern = Regex("^[\\p{L}\\p{N}][\\p{L}\\p{N}._-]*$")
private val invitationPattern = Regex("^[A-HJ-NP-Z2-9]{8,12}$", RegexOption.IGNORE_CASE)

internal fun registrationAvailable(state: OnboardingUiState): Boolean =
    state.meta?.let { !it.setupRequired && it.registrationEnabled } == true

/**
 * A current Authentik gateway owns the visible identity lifecycle. Lunara still
 * creates an internal data-owner projection, but it must not ask the person for
 * a second username, display name or password.
 *
 * Missing/legacy gateway metadata deliberately keeps the old form available.
 */
internal fun usesPrimaryAuthentikIdentity(meta: MetaDto?): Boolean =
    meta?.oidc != null &&
        meta.gatewayMode == "authentik" &&
        meta.primaryAuthenticationMethod == "OIDC" &&
        meta.oidc.enrollment?.createIfMissingRequired == true &&
        meta.oidc.enrollment.localPasswordRequired == false

internal fun requiresLocalIdentityFields(state: OnboardingUiState): Boolean =
    !usesPrimaryAuthentikIdentity(state.meta) ||
        state.authMode !in setOf(AuthMode.LOGIN, AuthMode.JOIN)

/** Only the explicit primary LOGIN path may bootstrap a new TRACKER projection. */
internal fun shouldCreateMissingAuthentikTracker(meta: MetaDto?, mode: AuthMode): Boolean =
    usesPrimaryAuthentikIdentity(meta) && mode == AuthMode.LOGIN

/**
 * AppAuth can recreate the activity after the browser has completed. Prefer
 * the encrypted pending proof over transient form state so that callback can
 * only continue the invitation flow, never fall through to normal sign-in.
 */
internal data class InvitationOidcContinuation(
    val code: String,
    val recoveredFromSecureStore: Boolean,
)

internal fun invitationOidcContinuation(
    state: OnboardingUiState,
    pendingInvitationCode: String?,
): InvitationOidcContinuation? {
    parseInvitationQrPayload(pendingInvitationCode, state.serverUrl)?.let {
        return InvitationOidcContinuation(it, recoveredFromSecureStore = true)
    }
    if (usesPrimaryAuthentikIdentity(state.meta) && state.authMode == AuthMode.JOIN) {
        return parseInvitationQrPayload(state.invitationCode, state.serverUrl)
            ?.let { InvitationOidcContinuation(it, recoveredFromSecureStore = false) }
    }
    return null
}

/** A normal OIDC start must not inherit an abandoned relationship proof. */
internal fun shouldClearPendingInvitationBeforeOidc(invitationCode: String?): Boolean = invitationCode == null

internal fun validateOnboardingForm(state: OnboardingUiState): OnboardingError? {
    if (!requiresLocalIdentityFields(state)) {
        return when (state.authMode) {
            AuthMode.LOGIN -> null
            AuthMode.JOIN -> if (parseInvitationQrPayload(state.invitationCode, state.serverUrl) == null) {
                OnboardingError.INVITATION_CODE
            } else {
                null
            }
            // requiresLocalIdentityFields() is always true for these modes.
            AuthMode.SETUP, AuthMode.REGISTER -> null
        }
    }

    val username = state.username.trim()
    if (username.length !in 3..64 || !usernamePattern.matches(username)) {
        return OnboardingError.USERNAME
    }
    if (state.authMode == AuthMode.LOGIN) {
        if (!isValidExistingPassword(state.password)) return OnboardingError.PASSWORD_REQUIRED
    } else if (!isValidNewAccountPassword(state.password)) {
        return OnboardingError.PASSWORD
    }

    return when (state.authMode) {
        AuthMode.LOGIN -> null
        AuthMode.SETUP -> when {
            state.displayName.trim().length !in 1..120 -> OnboardingError.DISPLAY_NAME
            normalizeBootstrapToken(state.bootstrapToken).length !in 20..256 -> OnboardingError.BOOTSTRAP_TOKEN
            else -> null
        }
        AuthMode.REGISTER -> when {
            !registrationAvailable(state) -> OnboardingError.REGISTRATION_UNAVAILABLE
            state.displayName.trim().length !in 1..120 -> OnboardingError.DISPLAY_NAME
            else -> null
        }
        AuthMode.JOIN -> when {
            state.displayName.trim().length !in 1..120 -> OnboardingError.DISPLAY_NAME
            parseInvitationQrPayload(state.invitationCode, state.serverUrl) == null -> OnboardingError.INVITATION_CODE
            else -> null
        }
    }
}

internal fun normalizeBootstrapToken(value: String): String = value.trim()

internal fun parseInvitationQrPayload(value: String?, expectedServerUrl: String? = null): String? {
    val raw = value?.trim()?.takeIf(String::isNotBlank) ?: return null
    raw.uppercase(Locale.ROOT).takeIf(invitationPattern::matches)?.let { return it }
    val expected = expectedServerUrl?.let { runCatching { URI(it.trim()) }.getOrNull() } ?: return null
    val link = runCatching { URI(raw) }.getOrNull() ?: return null
    if (
        link.scheme != "https" || link.userInfo != null || link.query != null ||
        expected.scheme != "https" || expected.userInfo != null || expected.query != null ||
        !link.scheme.equals(expected.scheme, ignoreCase = true) ||
        !link.host.equals(expected.host, ignoreCase = true) || link.port != expected.port ||
        link.path != "/invite"
    ) return null
    val fragment = link.fragment ?: return null
    val parts = fragment.split('&')
    if (parts.size != 1 || !parts[0].startsWith("invite=")) return null
    return runCatching {
        java.net.URLDecoder.decode(parts[0].removePrefix("invite="), Charsets.UTF_8.name())
    }.getOrNull()
        ?.trim()?.uppercase(Locale.ROOT)?.takeIf(invitationPattern::matches)
}

/**
 * Produces the public browser/scanner link without putting the bearer code on
 * the wire. The fragment is intentionally not sent to the server.
 */
internal fun canonicalInvitationLink(serverUrl: String?, invitationCode: String): String? {
    val code = parseInvitationQrPayload(invitationCode) ?: return null
    val server = serverUrl?.let { runCatching { URI(it.trim()) }.getOrNull() } ?: return null
    if (
        server.scheme != "https" || server.host.isNullOrBlank() ||
        server.userInfo != null || server.query != null || server.fragment != null
    ) return null
    val authority = buildString {
        append(server.host)
        if (server.port != -1) append(':').append(server.port)
    }
    return "https://$authority/invite#invite=$code"
}

internal enum class AuthentikJoinPolicy { NONE, LEGACY_OPTIONAL, REQUIRED }

internal fun authentikJoinPolicy(meta: MetaDto?): AuthentikJoinPolicy {
    if (meta?.oidc == null) return AuthentikJoinPolicy.NONE
    return when (meta.gatewayMode) {
        "authentik" -> AuthentikJoinPolicy.REQUIRED
        null -> AuthentikJoinPolicy.LEGACY_OPTIONAL
        else -> AuthentikJoinPolicy.NONE
    }
}

internal fun gatewayJoinRecoveryError(
    mode: AuthMode,
    failure: Throwable,
    gatewayOidcAvailable: Boolean,
): OnboardingError? = if (
    mode == AuthMode.JOIN &&
    gatewayOidcAvailable &&
    failure is ApiException &&
    (failure.status in 300..399 || failure.status == 401 || failure.status == 403)
) {
    OnboardingError.GATEWAY_AUTH_JOIN
} else {
    null
}

internal fun authenticationFailureState(
    state: OnboardingUiState,
    error: OnboardingError,
): OnboardingUiState = if (error == OnboardingError.SETUP_COMPLETE) {
    state.copy(
        isBusy = false,
        password = "",
        bootstrapToken = "",
        authMode = AuthMode.LOGIN,
        meta = state.meta?.copy(setupRequired = false),
        error = error,
    )
} else {
    state.copy(
        isBusy = false,
        password = "",
        authentikGatewayReady = if (error == OnboardingError.GATEWAY_AUTH_JOIN) {
            false
        } else {
            state.authentikGatewayReady
        },
        error = error,
    )
}
