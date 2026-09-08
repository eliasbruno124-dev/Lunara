package de.lunara.app.ui.onboarding

import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lunara.app.BuildConfig
import de.lunara.app.core.network.ServerAddressException
import de.lunara.app.core.network.ServerAddressParser
import de.lunara.app.core.network.ServerChangeConfirmationRequired
import de.lunara.app.data.remote.dto.MetaDto
import de.lunara.app.data.repository.ApiException
import de.lunara.app.data.repository.AuthentikLoginResult
import de.lunara.app.data.repository.ConnectionRepository
import de.lunara.app.data.repository.shouldClearPendingInvitationAfterOidcFailure
import de.lunara.app.security.limitAccountPasswordInput
import de.lunara.app.security.AuthentikAuthorizationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import javax.inject.Inject
import javax.net.ssl.SSLException

enum class OnboardingStage { INTRO, LOCAL, SERVER, AUTH }
enum class AuthMode { LOGIN, SETUP, REGISTER, JOIN }
enum class OnboardingError {
    INVALID_URL,
    HTTPS_REQUIRED,
    INCOMPLETE_CREDENTIALS,
    CREDENTIALS_REQUIRE_HTTPS,
    QR_CREDENTIALS,
    QR_CAMERA,
    DNS,
    TLS,
    TIMEOUT,
    GATEWAY_AUTH,
    GATEWAY_AUTH_JOIN,
    API,
    USERNAME,
    USERNAME_IN_USE,
    PASSWORD_REQUIRED,
    PASSWORD,
    DISPLAY_NAME,
    BOOTSTRAP_TOKEN,
    BOOTSTRAP_REJECTED,
    SETUP_COMPLETE,
    INVITATION_CODE,
    INVITATION_INVALID,
    INVITATION_UNAVAILABLE,
    INVITATION_ACCOUNT_NOT_EMPTY,
    INVITATION_ACCEPT_CONFLICT,
    PARTNER_ALREADY_CONNECTED,
    INVALID_CREDENTIALS,
    REGISTRATION_UNAVAILABLE,
    OIDC_CANCELLED,
    OIDC_NOT_LINKED,
    OIDC_FAILED,
    GENERIC,
}

data class OnboardingUiState(
    val stage: OnboardingStage = OnboardingStage.INTRO,
    val language: String = "de",
    // Deliberately blank: a production server or credential-bearing gateway URL must never
    // appear in a distributable APK or be silently selected for a new user.
    val serverUrl: String = "",
    val allowInsecureHttp: Boolean = false,
    val isBusy: Boolean = false,
    val meta: MetaDto? = null,
    val authMode: AuthMode = AuthMode.LOGIN,
    val username: String = "",
    val password: String = "",
    val displayName: String = "",
    val bootstrapToken: String = "",
    val invitationCode: String = "",
    val invitationConfirmationReady: Boolean = false,
    val authentikGatewayReady: Boolean = false,
    val serverChangeConfirmationRequired: Boolean = false,
    val error: OnboardingError? = null,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun chooseLanguage(language: String) {
        require(language == "de" || language == "en")
        _state.update { it.copy(language = language) }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
    }

    fun continueFromIntro() = _state.update { it.copy(stage = OnboardingStage.SERVER, error = null) }
    fun beginLocalSetup() = _state.update { it.copy(stage = OnboardingStage.LOCAL, error = null) }
    fun back() = _state.update {
        it.copy(
            stage = when (it.stage) {
                OnboardingStage.AUTH -> OnboardingStage.SERVER
                OnboardingStage.LOCAL, OnboardingStage.SERVER -> OnboardingStage.INTRO
                OnboardingStage.INTRO -> OnboardingStage.INTRO
            },
            error = null,
        )
    }
    fun setServerUrl(value: String) = _state.update {
        it.copy(
            serverUrl = value,
            error = null,
            meta = null,
            authentikGatewayReady = false,
            serverChangeConfirmationRequired = false,
        )
    }
    fun acceptScannedServerUrl(value: String) {
        val parsed = runCatching { ServerAddressParser.parse(value, BuildConfig.ALLOW_INSECURE_HTTP) }.getOrNull()
        if (parsed == null) {
            _state.update { it.copy(error = OnboardingError.INVALID_URL) }
        } else if (parsed.gatewayCredentials != null) {
            _state.update { it.copy(error = OnboardingError.QR_CREDENTIALS) }
        } else {
            _state.update { it.copy(serverUrl = parsed.baseUrl, error = null, meta = null) }
        }
    }
    fun setAllowHttp(value: Boolean) = _state.update { it.copy(allowInsecureHttp = value, error = null) }
    fun setAuthMode(value: AuthMode) {
        if (value != AuthMode.JOIN) connectionRepository.clearPendingInvitation()
        _state.update { state ->
            if (value == AuthMode.REGISTER && !registrationAvailable(state)) {
                state.copy(error = OnboardingError.REGISTRATION_UNAVAILABLE, password = "")
            } else {
                state.copy(
                    authMode = value,
                    password = "",
                    invitationConfirmationReady = false,
                    error = null,
                )
            }
        }
    }
    fun setUsername(value: String) = _state.update { it.copy(username = value.take(64), error = null) }
    fun setPassword(value: String) = _state.update {
        it.copy(password = limitAccountPasswordInput(value), error = null)
    }
    fun setDisplayName(value: String) = _state.update { it.copy(displayName = value.take(120), error = null) }
    fun setBootstrapToken(value: String) = _state.update { it.copy(bootstrapToken = value.take(256), error = null) }
    fun setInvitationCode(value: String) = _state.update {
        it.copy(invitationCode = value.take(512), invitationConfirmationReady = false, error = null)
    }
    fun reportInvalidInvitationQr() = _state.update { it.copy(error = OnboardingError.INVITATION_CODE) }
    fun reportQrCameraUnavailable() = _state.update { it.copy(error = OnboardingError.QR_CAMERA) }

    fun submitLocalSetup() {
        val snapshot = _state.value
        if (snapshot.isBusy) return
        if (snapshot.displayName.trim().length !in 1..120) {
            _state.update { it.copy(error = OnboardingError.DISPLAY_NAME) }
            return
        }
        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            runCatching { connectionRepository.startLocalMode(snapshot.displayName) }
                .onSuccess { resetForNewSession() }
                .onFailure {
                    _state.update { state -> state.copy(isBusy = false, error = OnboardingError.GENERIC) }
                }
        }
    }

    fun testConnection(confirmServerChange: Boolean = false) {
        val snapshot = _state.value
        val parsed = runCatching {
            ServerAddressParser.parse(
                snapshot.serverUrl,
                allowInsecureHttp = BuildConfig.ALLOW_INSECURE_HTTP && snapshot.allowInsecureHttp,
            )
        }.getOrElse {
            _state.update { state -> state.copy(error = mapError(it)) }
            return
        }
        _state.update { it.copy(isBusy = true, error = null, serverChangeConfirmationRequired = false) }
        viewModelScope.launch {
            runCatching {
                val meta = connectionRepository.testAndSaveConnection(parsed, confirmServerChange)
                val gatewayReady = if (
                    authentikJoinPolicy(meta) == AuthentikJoinPolicy.LEGACY_OPTIONAL
                ) {
                    connectionRepository.hasAuthentikGatewaySession()
                } else {
                    false
                }
                meta to gatewayReady
            }
                .onSuccess { (meta, authentikGatewayReady) ->
                    _state.update {
                        it.copy(
                            isBusy = false,
                            stage = OnboardingStage.AUTH,
                            meta = meta,
                            serverUrl = parsed.baseUrl,
                            authMode = if (meta.setupRequired) AuthMode.SETUP else AuthMode.LOGIN,
                            authentikGatewayReady = authentikGatewayReady,
                            error = null,
                        )
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        if (failure is ServerChangeConfirmationRequired) {
                            it.copy(isBusy = false, serverChangeConfirmationRequired = true, error = null)
                        } else {
                            it.copy(isBusy = false, error = mapError(failure))
                        }
                    }
                }
        }
    }

    fun confirmServerChange() = testConnection(confirmServerChange = true)
    fun cancelServerChange() = _state.update { it.copy(serverChangeConfirmationRequired = false) }

    fun submitAuthentication() {
        val snapshot = _state.value
        if (snapshot.isBusy) return
        if (snapshot.invitationConfirmationReady) {
            val code = parseInvitationQrPayload(snapshot.invitationCode, snapshot.serverUrl) ?: run {
                _state.update { it.copy(error = OnboardingError.INVITATION_CODE) }
                return
            }
            _state.update { it.copy(isBusy = true, error = null) }
            viewModelScope.launch {
                runCatching { connectionRepository.acceptInvitation(code) }
                    .onSuccess { resetForNewSession() }
                    .onFailure { failure ->
                        _state.update { it.copy(isBusy = false, error = mapInvitationAcceptanceError(failure)) }
                    }
            }
            return
        }
        // Current Authentik-primary servers complete LOGIN and JOIN through the
        // browser callback. There is intentionally no hidden local-credential
        // fallback behind the primary OIDC UI.
        if (
            usesPrimaryAuthentikIdentity(snapshot.meta) &&
            snapshot.authMode in setOf(AuthMode.LOGIN, AuthMode.JOIN)
        ) {
            _state.update { it.copy(error = OnboardingError.OIDC_FAILED) }
            return
        }
        val validationError = validateOnboardingForm(snapshot)
        if (validationError != null) {
            _state.update { it.copy(error = validationError) }
            return
        }
        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            runCatching {
                when (snapshot.authMode) {
                    AuthMode.LOGIN -> connectionRepository.login(snapshot.username, snapshot.password)
                    AuthMode.SETUP -> connectionRepository.setup(
                        snapshot.bootstrapToken,
                        snapshot.username,
                        snapshot.displayName,
                        snapshot.password,
                        snapshot.language,
                    )
                    AuthMode.REGISTER -> connectionRepository.register(
                        snapshot.username,
                        snapshot.displayName,
                        snapshot.password,
                        snapshot.language,
                    )
                    AuthMode.JOIN -> connectionRepository.join(
                        requireNotNull(parseInvitationQrPayload(snapshot.invitationCode, snapshot.serverUrl)),
                        snapshot.username,
                        snapshot.displayName,
                        snapshot.password,
                        snapshot.language,
                    )
                }
            }.onSuccess {
                resetForNewSession()
            }.onFailure { failure ->
                _state.update { state ->
                    val mappedError = mapAuthenticationError(failure, snapshot.authMode)
                    // Fail closed against an unexpectedly stale intermediary/meta response:
                    // the authoritative setup endpoint can move the UI straight to login.
                    authenticationFailureState(state, mappedError)
                }
            }
        }
    }

    fun beginAuthentikLogin(onReady: (Intent) -> Unit) {
        val snapshot = _state.value
        val oidc = snapshot.meta?.oidc
        val gatewayJoinPolicy = authentikJoinPolicy(snapshot.meta)
        if (
            snapshot.isBusy ||
            snapshot.authMode !in setOf(AuthMode.LOGIN, AuthMode.JOIN) ||
            oidc == null ||
            (snapshot.authMode == AuthMode.JOIN &&
                (gatewayJoinPolicy == AuthentikJoinPolicy.NONE || snapshot.authentikGatewayReady))
        ) return
        val invitationForOidc = if (usesPrimaryAuthentikIdentity(snapshot.meta) && snapshot.authMode == AuthMode.JOIN) {
            parseInvitationQrPayload(snapshot.invitationCode, snapshot.serverUrl) ?: run {
                _state.update { it.copy(error = OnboardingError.INVITATION_CODE) }
                return
            }
        } else {
            null
        }
        if (shouldClearPendingInvitationBeforeOidc(invitationForOidc)) {
            connectionRepository.clearPendingInvitation()
        } else {
            connectionRepository.rememberPendingInvitation(requireNotNull(invitationForOidc))
        }
        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            runCatching { connectionRepository.authentikAuthorizationIntent(oidc) }
                .onSuccess { intent ->
                    _state.update { it.copy(isBusy = false) }
                    onReady(intent)
                }
                .onFailure {
                    connectionRepository.clearPendingInvitation()
                    _state.update { state -> state.copy(isBusy = false, error = OnboardingError.OIDC_FAILED) }
                }
        }
    }

    fun completeAuthentikLogin(result: Intent?) {
        val snapshot = _state.value
        val continuation = invitationOidcContinuation(snapshot, connectionRepository.pendingInvitationCode())
        // A restored AppAuth callback has no reliable in-memory meta/auth-mode.
        // The encrypted invitation proof is authoritative for this one callback.
        val mode = if (continuation != null) AuthMode.JOIN else snapshot.authMode
        val primaryIdentity = continuation != null || usesPrimaryAuthentikIdentity(snapshot.meta)
        val gatewayJoinPolicy = authentikJoinPolicy(snapshot.meta)
        if (snapshot.isBusy) return
        val invitationCode = continuation?.code
        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            runCatching {
                connectionRepository.authentikLogin(
                    result = result,
                    invitationCode = invitationCode,
                    preferredLanguage = if (primaryIdentity) {
                        snapshot.language.uppercase(Locale.ROOT)
                    } else {
                        null
                    },
                    // Creation is deliberately distinct from invitation
                    // redemption. Only a primary Authentik LOGIN may create a
                    // missing TRACKER projection; JOIN always needs its code.
                    createIfMissing = true.takeIf {
                        continuation == null && primaryIdentity && shouldCreateMissingAuthentikTracker(snapshot.meta, mode)
                    },
                    allowLegacyGatewayPreparation = continuation == null && !primaryIdentity,
                )
            }
                .onSuccess { outcome ->
                    when (outcome) {
                        is AuthentikLoginResult.SignedIn -> resetForNewSession()
                        AuthentikLoginResult.InvitationConfirmationRequired -> _state.update {
                            it.copy(
                                stage = OnboardingStage.AUTH,
                                authMode = AuthMode.JOIN,
                                invitationCode = requireNotNull(invitationCode),
                                isBusy = false,
                                invitationConfirmationReady = true,
                                error = null,
                            )
                        }
                        AuthentikLoginResult.LegacyGatewayPrepared -> _state.update {
                            if (
                                mode == AuthMode.JOIN &&
                                gatewayJoinPolicy == AuthentikJoinPolicy.LEGACY_OPTIONAL
                            ) {
                                it.copy(isBusy = false, authentikGatewayReady = true, error = null)
                            } else {
                                it.copy(isBusy = false, error = OnboardingError.OIDC_NOT_LINKED)
                            }
                        }
                    }
                }
                .onFailure { failure ->
                    // A committed partner provisioning keeps its proof through a transient
                    // post-commit role refresh failure. Actual callback/auth failures must
                    // never turn a later ordinary OIDC login into invitation redemption.
                    if (shouldClearPendingInvitationAfterOidcFailure(failure)) {
                        connectionRepository.clearPendingInvitation()
                    }
                    _state.update {
                        it.copy(
                            isBusy = false,
                            error = mapAuthentikError(failure, mode, primaryIdentity),
                        )
                    }
                }
        }
    }

    private fun mapAuthentikError(
        error: Throwable,
        mode: AuthMode,
        primaryIdentity: Boolean,
    ): OnboardingError = when {
        error is AuthentikAuthorizationException &&
            error.message?.contains("cancel", ignoreCase = true) == true -> OnboardingError.OIDC_CANCELLED
        primaryIdentity && mode == AuthMode.JOIN && error is ApiException && error.status == 404 ->
            OnboardingError.INVITATION_INVALID
        primaryIdentity && mode == AuthMode.JOIN && error is ApiException && error.status == 410 ->
            OnboardingError.INVITATION_UNAVAILABLE
        primaryIdentity && mode == AuthMode.JOIN && error is ApiException && error.status == 409 ->
            OnboardingError.PARTNER_ALREADY_CONNECTED
        primaryIdentity && mode == AuthMode.LOGIN && error is ApiException && error.status == 403 ->
            OnboardingError.REGISTRATION_UNAVAILABLE
        error is ApiException && error.status in setOf(401, 404) -> OnboardingError.OIDC_NOT_LINKED
        else -> OnboardingError.OIDC_FAILED
    }

    private fun mapAuthenticationError(error: Throwable, mode: AuthMode): OnboardingError {
        if (error is ApiException && mode == AuthMode.SETUP) {
            if (error.status == 401) return OnboardingError.BOOTSTRAP_REJECTED
            if (error.status == 409) return OnboardingError.SETUP_COMPLETE
        }
        if (error is ApiException && mode == AuthMode.LOGIN && error.status == 401) {
            return OnboardingError.INVALID_CREDENTIALS
        }
        if (error is ApiException && mode == AuthMode.REGISTER) {
            if (error.status == 403) return OnboardingError.REGISTRATION_UNAVAILABLE
            if (error.status == 409) return OnboardingError.USERNAME_IN_USE
        }
        if (error is ApiException && mode == AuthMode.JOIN) {
            if (error.status == 404) return OnboardingError.INVITATION_INVALID
            if (error.status == 410) return OnboardingError.INVITATION_UNAVAILABLE
            if (error.status == 403) return OnboardingError.INVITATION_ACCOUNT_NOT_EMPTY
            if (error.status == 409) {
                return if (error.message?.contains("username", ignoreCase = true) == true) {
                    OnboardingError.USERNAME_IN_USE
                } else {
                    OnboardingError.PARTNER_ALREADY_CONNECTED
                }
            }
            gatewayJoinRecoveryError(
                mode = mode,
                failure = error,
                gatewayOidcAvailable = _state.value.meta?.let {
                    authentikJoinPolicy(it) != AuthentikJoinPolicy.NONE
                } == true,
            )?.let { return it }
        }
        return mapError(error)
    }

    private fun mapInvitationAcceptanceError(error: Throwable): OnboardingError = when {
        error is ApiException && error.status == 404 -> OnboardingError.INVITATION_INVALID
        error is ApiException && error.status == 410 -> OnboardingError.INVITATION_UNAVAILABLE
        // A move conflict can mean data, a non-standalone source, a self-invite,
        // or an occupied target. Do not expose or infer server-side relationship data.
        error is ApiException && error.status == 409 -> OnboardingError.INVITATION_ACCEPT_CONFLICT
        error is ApiException && error.status == 403 -> OnboardingError.INVITATION_ACCOUNT_NOT_EMPTY
        else -> mapError(error)
    }

    fun resetForNewSession() {
        val language = _state.value.language
        _state.value = OnboardingUiState(language = language)
    }

    private fun mapError(error: Throwable): OnboardingError = when (error) {
        ServerAddressException.InvalidUrl -> OnboardingError.INVALID_URL
        ServerAddressException.HttpsRequired -> OnboardingError.HTTPS_REQUIRED
        ServerAddressException.IncompleteCredentials -> OnboardingError.INCOMPLETE_CREDENTIALS
        ServerAddressException.CredentialsRequireHttps -> OnboardingError.CREDENTIALS_REQUIRE_HTTPS
        is UnknownHostException -> OnboardingError.DNS
        is SSLException -> OnboardingError.TLS
        is SocketTimeoutException -> OnboardingError.TIMEOUT
        is ApiException -> if (error.status in 300..399 || error.status == 401 || error.status == 403) {
            OnboardingError.GATEWAY_AUTH
        } else {
            OnboardingError.API
        }
        else -> OnboardingError.GENERIC
    }
}
