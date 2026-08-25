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
import de.lunara.app.data.repository.ConnectionRepository
import de.lunara.app.security.limitAccountPasswordInput
import de.lunara.app.security.AuthentikAuthorizationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.SocketTimeoutException
import java.net.UnknownHostException
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
    DNS,
    TLS,
    TIMEOUT,
    GATEWAY_AUTH,
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
        it.copy(serverUrl = value, error = null, meta = null, serverChangeConfirmationRequired = false)
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
    fun setAuthMode(value: AuthMode) = _state.update { state ->
        if (value == AuthMode.REGISTER && !registrationAvailable(state)) {
            state.copy(error = OnboardingError.REGISTRATION_UNAVAILABLE, password = "")
        } else {
            state.copy(authMode = value, error = null, password = "")
        }
    }
    fun setUsername(value: String) = _state.update { it.copy(username = value.take(64), error = null) }
    fun setPassword(value: String) = _state.update {
        it.copy(password = limitAccountPasswordInput(value), error = null)
    }
    fun setDisplayName(value: String) = _state.update { it.copy(displayName = value.take(120), error = null) }
    fun setBootstrapToken(value: String) = _state.update { it.copy(bootstrapToken = value.take(256), error = null) }
    fun setInvitationCode(value: String) = _state.update { it.copy(invitationCode = value.take(32), error = null) }

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
            runCatching { connectionRepository.testAndSaveConnection(parsed, confirmServerChange) }
                .onSuccess { meta ->
                    _state.update {
                        it.copy(
                            isBusy = false,
                            stage = OnboardingStage.AUTH,
                            meta = meta,
                            serverUrl = parsed.baseUrl,
                            authMode = if (meta.setupRequired) AuthMode.SETUP else AuthMode.LOGIN,
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
                        snapshot.invitationCode,
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
        if (snapshot.isBusy || snapshot.authMode != AuthMode.LOGIN || oidc == null) return
        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            runCatching { connectionRepository.authentikAuthorizationIntent(oidc) }
                .onSuccess { intent ->
                    _state.update { it.copy(isBusy = false) }
                    onReady(intent)
                }
                .onFailure {
                    _state.update { state -> state.copy(isBusy = false, error = OnboardingError.OIDC_FAILED) }
                }
        }
    }

    fun completeAuthentikLogin(result: Intent?) {
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            runCatching { connectionRepository.authentikLogin(result) }
                .onSuccess { resetForNewSession() }
                .onFailure { failure ->
                    _state.update {
                        it.copy(isBusy = false, error = mapAuthentikError(failure))
                    }
                }
        }
    }

    private fun mapAuthentikError(error: Throwable): OnboardingError = when {
        error is AuthentikAuthorizationException &&
            error.message?.contains("cancel", ignoreCase = true) == true -> OnboardingError.OIDC_CANCELLED
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
            if (error.status == 409) {
                return if (error.message?.contains("username", ignoreCase = true) == true) {
                    OnboardingError.USERNAME_IN_USE
                } else {
                    OnboardingError.PARTNER_ALREADY_CONNECTED
                }
            }
        }
        return mapError(error)
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
