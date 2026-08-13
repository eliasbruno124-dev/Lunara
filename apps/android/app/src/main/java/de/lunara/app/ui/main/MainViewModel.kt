package de.lunara.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lunara.app.core.model.UserRole
import de.lunara.app.data.local.PeriodEntity
import de.lunara.app.data.preferences.AppPreferences
import de.lunara.app.data.remote.dto.PartnerDashboardDto
import de.lunara.app.data.remote.dto.InvitationDto
import de.lunara.app.data.remote.dto.SharingSettingsDto
import de.lunara.app.data.remote.dto.UpdateSharingRequest
import de.lunara.app.data.remote.dto.PredictionDto
import de.lunara.app.data.repository.DashboardRepository
import de.lunara.app.data.repository.PeriodRepository
import de.lunara.app.data.repository.HouseholdRepository
import de.lunara.app.data.repository.ApiException
import de.lunara.app.data.repository.PasswordRepository
import de.lunara.app.data.repository.SharingRepository
import de.lunara.app.data.sync.SyncScheduler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val sessionUserId: String? = null,
    val periods: List<PeriodEntity> = emptyList(),
    val dailyLogs: List<de.lunara.app.data.local.DailyLogEntity> = emptyList(),
    val pendingMutations: Int = 0,
    val lastSyncAt: String? = null,
    val privacyMode: Boolean = true,
    val useSystemTheme: Boolean = true,
    val darkMode: Boolean = false,
    val partnerDashboard: PartnerDashboardDto? = null,
    val partnerDashboardUnavailable: Boolean = false,
    val checkInSent: Boolean = false,
    val checkInError: Boolean = false,
    val invitation: InvitationDto? = null,
    val sharing: SharingSettingsDto? = null,
    val settingsBusy: Boolean = false,
    val settingsError: Boolean = false,
    val trackerPrediction: PredictionDto? = null,
    val passwordChangeStatus: PasswordChangeStatus = PasswordChangeStatus.IDLE,
)

sealed interface MainEvent {
    data object PeriodSaved : MainEvent
    data object DailyLogSaved : MainEvent
    data object PasswordChanged : MainEvent
}

private data class SessionUiExtras(
    val checkInSent: Boolean,
    val checkInError: Boolean,
    val invitation: InvitationDto?,
    val sharing: SharingSettingsDto?,
    val settingsBusy: Boolean,
    val settingsError: Boolean,
    val trackerPrediction: PredictionDto?,
    val passwordChangeStatus: PasswordChangeStatus,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val periodRepository: PeriodRepository,
    private val preferences: AppPreferences,
    private val syncScheduler: SyncScheduler,
    private val dashboardRepository: DashboardRepository,
    private val householdRepository: HouseholdRepository,
    private val sharingRepository: SharingRepository,
    private val passwordRepository: PasswordRepository,
) : ViewModel() {
    private val partnerDashboard = MutableStateFlow<PartnerDashboardDto?>(null)
    private val partnerUnavailable = MutableStateFlow(false)
    private val checkInSent = MutableStateFlow(false)
    private val checkInError = MutableStateFlow(false)
    private val invitation = MutableStateFlow<InvitationDto?>(null)
    private val sharing = MutableStateFlow<SharingSettingsDto?>(null)
    private val settingsBusy = MutableStateFlow(false)
    private val settingsError = MutableStateFlow(false)
    private val trackerPrediction = MutableStateFlow<PredictionDto?>(null)
    private val passwordChangeStatus = MutableStateFlow(PasswordChangeStatus.IDLE)
    private val sessionOwnerId = MutableStateFlow<String?>(null)
    private var sessionOperations: CoroutineScope? = null
    private val _events = MutableSharedFlow<MainEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private val localState = combine(
        preferences.state,
        periodRepository.observePeriods(),
        periodRepository.observePendingCount(),
        periodRepository.observeDailyLogs(),
    ) { prefs, periods, pending, dailyLogs ->
        MainUiState(
            sessionUserId = prefs.session?.userId,
            periods = periods.filter { it.userId == prefs.session?.userId },
            pendingMutations = pending,
            dailyLogs = dailyLogs.filter { it.userId == prefs.session?.userId },
            lastSyncAt = prefs.lastSyncAt,
            privacyMode = prefs.privacyMode,
            useSystemTheme = prefs.useSystemTheme,
            darkMode = prefs.darkMode,
        )
    }
    private val dashboardState = combine(
        partnerDashboard,
        partnerUnavailable,
    ) { partner, unavailable -> partner to unavailable }
    private val sessionExtras = combine(
        checkInSent, checkInError, invitation, sharing, settingsBusy, settingsError, trackerPrediction,
        passwordChangeStatus,
    ) { values ->
        SessionUiExtras(
            checkInSent = values[0] as Boolean,
            checkInError = values[1] as Boolean,
            invitation = values[2] as InvitationDto?,
            sharing = values[3] as SharingSettingsDto?,
            settingsBusy = values[4] as Boolean,
            settingsError = values[5] as Boolean,
            trackerPrediction = values[6] as PredictionDto?,
            passwordChangeStatus = values[7] as PasswordChangeStatus,
        )
    }
    val state: StateFlow<MainUiState> = combine(
        localState,
        dashboardState,
        sessionOwnerId,
        sessionExtras,
    ) { local, dashboard, ownerId, extra ->
        // Activity-scoped ViewModels survive logout. Never project session-bound fields until
        // the observer below has reset and rebound them to the currently rendered account.
        if (!SessionUiIsolation.canExpose(local.sessionUserId, ownerId)) {
            local
        } else {
            local.copy(
                partnerDashboard = dashboard.first,
                partnerDashboardUnavailable = dashboard.second,
                checkInSent = extra.checkInSent,
                checkInError = extra.checkInError,
                invitation = extra.invitation,
                sharing = extra.sharing,
                settingsBusy = extra.settingsBusy,
                settingsError = extra.settingsError,
                trackerPrediction = extra.trackerPrediction,
                passwordChangeStatus = extra.passwordChangeStatus,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        viewModelScope.launch {
            preferences.state
                .map { it.session }
                .distinctUntilChanged()
                .collectLatest { session ->
                    sessionOperations?.cancel()
                    sessionOperations = null
                    resetSessionState()
                    if (session == null) return@collectLatest
                    sessionOwnerId.value = session.userId
                    sessionOperations = CoroutineScope(
                        viewModelScope.coroutineContext +
                            SupervisorJob(viewModelScope.coroutineContext[Job]),
                    )

                    if (session.role == UserRole.PARTNER) {
                        syncScheduler.requestImmediate()
                        coroutineScope {
                            launch {
                                dashboardRepository.observeCachedPartnerDashboard().collectLatest { cached ->
                                    ifCurrentSession(session.userId) {
                                        partnerDashboard.value = cached
                                        if (cached != null) partnerUnavailable.value = false
                                    }
                                }
                            }
                            launch { refreshPartnerDashboardNow(session.userId) }
                            launch {
                                preferences.state
                                    .map { it.lastSyncAt }
                                    .drop(1)
                                    .filterNotNull()
                                    .distinctUntilChanged()
                                    .collectLatest { refreshPartnerDashboardNow(session.userId) }
                            }
                        }
                    } else {
                        coroutineScope {
                            launch { loadSharingNow(session.userId) }
                            launch { refreshTrackerPredictionNow(session.userId) }
                            launch {
                                preferences.state
                                    .map { it.lastSyncAt }
                                    .drop(1)
                                    .filterNotNull()
                                    .distinctUntilChanged()
                                    .collectLatest { refreshTrackerPredictionNow(session.userId) }
                            }
                        }
                    }
                }
        }
    }

    private fun resetSessionState() {
        sessionOwnerId.value = null
        partnerDashboard.value = null
        partnerUnavailable.value = false
        checkInSent.value = false
        checkInError.value = false
        invitation.value = null
        sharing.value = null
        settingsBusy.value = false
        settingsError.value = false
        trackerPrediction.value = null
        passwordChangeStatus.value = PasswordChangeStatus.IDLE
    }

    fun startPeriod(userId: String) {
        if (sessionOwnerId.value != userId) return
        launchForSession(userId) {
            runSessionOperation { periodRepository.startPeriod(userId) }
                .onSuccess { ifCurrentSession(userId) { _events.tryEmit(MainEvent.PeriodSaved) } }
        }
    }

    fun logToday(
        userId: String,
        flowLevel: String?,
        painLevel: Int?,
        mood: String?,
        energy: Int?,
        sleepQuality: Int?,
        symptoms: List<String>,
    ) {
        if (sessionOwnerId.value != userId) return
        launchForSession(userId) {
            runSessionOperation {
                periodRepository.logToday(userId, flowLevel, painLevel, mood, energy, sleepQuality, symptoms)
            }.onSuccess { ifCurrentSession(userId) { _events.tryEmit(MainEvent.DailyLogSaved) } }
        }
    }

    fun syncNow() {
        val ownerId = sessionOwnerId.value ?: return
        launchForSession(ownerId) { periodRepository.retryFailedMutations() }
    }

    fun refreshPartnerDashboard() {
        val ownerId = sessionOwnerId.value ?: return
        launchForSession(ownerId) { refreshPartnerDashboardNow(ownerId) }
    }

    private suspend fun refreshPartnerDashboardNow(ownerId: String) {
        runSessionOperation { dashboardRepository.partnerDashboard(ownerId) }
            .onSuccess {
                ifCurrentSession(ownerId) {
                    partnerDashboard.value = it
                    partnerUnavailable.value = false
                }
            }
            .onFailure {
                ifCurrentSession(ownerId) {
                    if (partnerDashboard.value == null) partnerUnavailable.value = true
                }
            }
    }

    fun refreshTrackerPrediction() {
        val ownerId = sessionOwnerId.value ?: return
        launchForSession(ownerId) { refreshTrackerPredictionNow(ownerId) }
    }

    private suspend fun refreshTrackerPredictionNow(ownerId: String) {
        runSessionOperation { dashboardRepository.trackerPrediction() }
            .onSuccess { prediction -> ifCurrentSession(ownerId) { trackerPrediction.value = prediction } }
    }

    fun sendCheckIn(prompt: String) {
        val ownerId = sessionOwnerId.value ?: return
        if (checkInSent.value) return
        launchForSession(ownerId) {
            runSessionOperation { dashboardRepository.sendCheckIn(prompt) }
                .onSuccess { ifCurrentSession(ownerId) { checkInSent.value = true; checkInError.value = false } }
                .onFailure { ifCurrentSession(ownerId) { checkInError.value = true } }
        }
    }

    fun createInvitation() {
        val ownerId = sessionOwnerId.value ?: return
        launchForSession(ownerId) {
            ifCurrentSession(ownerId) { settingsBusy.value = true }
            runSessionOperation { householdRepository.createInvitation() }
                .onSuccess { result -> ifCurrentSession(ownerId) { invitation.value = result; settingsError.value = false } }
                .onFailure { ifCurrentSession(ownerId) { settingsError.value = true } }
            ifCurrentSession(ownerId) { settingsBusy.value = false }
        }
    }

    fun loadSharing() {
        val ownerId = sessionOwnerId.value ?: return
        launchForSession(ownerId) { loadSharingNow(ownerId) }
    }

    private suspend fun loadSharingNow(ownerId: String) {
        runSessionOperation { sharingRepository.load() }
            .onSuccess { result -> ifCurrentSession(ownerId) { sharing.value = result; settingsError.value = false } }
            .onFailure { ifCurrentSession(ownerId) { settingsError.value = true } }
    }

    fun updateSharing(request: UpdateSharingRequest) {
        val ownerId = sessionOwnerId.value ?: return
        launchForSession(ownerId) {
            ifCurrentSession(ownerId) { settingsBusy.value = true }
            runSessionOperation { sharingRepository.update(request) }
                .onSuccess { result -> ifCurrentSession(ownerId) { sharing.value = result; settingsError.value = false } }
                .onFailure { ifCurrentSession(ownerId) { settingsError.value = true } }
            ifCurrentSession(ownerId) { settingsBusy.value = false }
        }
    }

    fun setPrivacyMode(enabled: Boolean) {
        viewModelScope.launch { preferences.setPrivacyMode(enabled) }
    }

    fun setUseSystemTheme(enabled: Boolean) {
        viewModelScope.launch { preferences.setTheme(enabled, state.value.darkMode) }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { preferences.setTheme(false, enabled) }
    }

    fun changePassword(currentPassword: String, newPassword: String, confirmation: String) {
        val ownerId = sessionOwnerId.value ?: return
        if (passwordChangeStatus.value == PasswordChangeStatus.BUSY) return
        val validation = validatePasswordChange(currentPassword, newPassword, confirmation)
        if (validation != null) {
            passwordChangeStatus.value = when (validation) {
                PasswordChangeValidation.CURRENT_REQUIRED -> PasswordChangeStatus.CURRENT_REQUIRED
                PasswordChangeValidation.NEW_LENGTH -> PasswordChangeStatus.NEW_LENGTH
                PasswordChangeValidation.SAME_PASSWORD -> PasswordChangeStatus.SAME_PASSWORD
                PasswordChangeValidation.CONFIRMATION_MISMATCH -> PasswordChangeStatus.CONFIRMATION_MISMATCH
            }
            return
        }
        passwordChangeStatus.value = PasswordChangeStatus.BUSY
        launchForSession(ownerId) {
            val result = runSessionOperation { passwordRepository.changePassword(currentPassword, newPassword) }
            if (sessionOwnerId.value == ownerId) {
                if (result.isSuccess) {
                    passwordChangeStatus.value = PasswordChangeStatus.SUCCESS
                    _events.emit(MainEvent.PasswordChanged)
                } else {
                    val failure = result.exceptionOrNull()
                    passwordChangeStatus.value = if (failure is ApiException && failure.status == 403) {
                        PasswordChangeStatus.CURRENT_REJECTED
                    } else {
                        PasswordChangeStatus.FAILED
                    }
                }
            }
        }
    }

    fun clearPasswordChangeStatus() {
        if (passwordChangeStatus.value != PasswordChangeStatus.BUSY) {
            passwordChangeStatus.value = PasswordChangeStatus.IDLE
        }
    }

    private inline fun ifCurrentSession(ownerId: String, action: () -> Unit) {
        if (sessionOwnerId.value == ownerId) action()
    }

    private fun launchForSession(ownerId: String, action: suspend () -> Unit) {
        val scope = sessionOperations ?: return
        scope.launch {
            if (sessionOwnerId.value == ownerId) action()
        }
    }

    private suspend fun <T> runSessionOperation(action: suspend () -> T): Result<T> = try {
        Result.success(action())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        Result.failure(failure)
    }
}
