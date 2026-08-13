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
import de.lunara.app.data.remote.dto.CycleProfileDto
import de.lunara.app.data.remote.dto.CarePreferencesDto
import de.lunara.app.data.remote.dto.UpdateCarePreferencesRequest
import de.lunara.app.data.repository.DashboardRepository
import de.lunara.app.data.repository.PeriodRepository
import de.lunara.app.data.repository.HouseholdRepository
import de.lunara.app.data.repository.ApiException
import de.lunara.app.data.repository.PasswordRepository
import de.lunara.app.data.repository.SharingRepository
import de.lunara.app.data.repository.CarePreferencesRepository
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
import java.time.LocalDate

data class MainUiState(
    val sessionUserId: String? = null,
    val periods: List<PeriodEntity> = emptyList(),
    val dailyLogs: List<de.lunara.app.data.local.DailyLogEntity> = emptyList(),
    val pendingMutations: Int = 0,
    val conflictMutations: Int = 0,
    val lastSyncAt: String? = null,
    val privacyMode: Boolean = true,
    val useSystemTheme: Boolean = true,
    val darkMode: Boolean = false,
    val partnerDashboard: PartnerDashboardDto? = null,
    val partnerDashboardUnavailable: Boolean = false,
    val checkInSent: Boolean = false,
    val checkInError: Boolean = false,
    val invitation: InvitationDto? = null,
    val partnerConnected: Boolean = false,
    val sharing: SharingSettingsDto? = null,
    val settingsBusy: Boolean = false,
    val settingsError: Boolean = false,
    val trackerPrediction: PredictionDto? = null,
    val cycleProfile: CycleProfileDto? = null,
    val carePreferences: CarePreferencesDto? = null,
    val passwordChangeStatus: PasswordChangeStatus = PasswordChangeStatus.IDLE,
)

sealed interface MainEvent {
    data object PeriodSaved : MainEvent
    data object PeriodUpdated : MainEvent
    data object PeriodDeleted : MainEvent
    data object PeriodChangeFailed : MainEvent
    data object DailyLogSaved : MainEvent
    data object DailyLogFailed : MainEvent
    data object PasswordChanged : MainEvent
}

private data class SessionUiExtras(
    val checkInSent: Boolean,
    val checkInError: Boolean,
    val invitation: InvitationDto?,
    val partnerConnected: Boolean,
    val sharing: SharingSettingsDto?,
    val settingsBusy: Boolean,
    val settingsError: Boolean,
    val trackerPrediction: PredictionDto?,
    val cycleProfile: CycleProfileDto?,
    val carePreferences: CarePreferencesDto?,
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
    private val carePreferencesRepository: CarePreferencesRepository,
    private val passwordRepository: PasswordRepository,
) : ViewModel() {
    private val partnerDashboard = MutableStateFlow<PartnerDashboardDto?>(null)
    private val partnerUnavailable = MutableStateFlow(false)
    private val checkInSent = MutableStateFlow(false)
    private val checkInError = MutableStateFlow(false)
    private val invitation = MutableStateFlow<InvitationDto?>(null)
    private val partnerConnected = MutableStateFlow(false)
    private val sharing = MutableStateFlow<SharingSettingsDto?>(null)
    private val settingsBusy = MutableStateFlow(false)
    private val settingsError = MutableStateFlow(false)
    private val trackerPrediction = MutableStateFlow<PredictionDto?>(null)
    private val cycleProfile = MutableStateFlow<CycleProfileDto?>(null)
    private val carePreferences = MutableStateFlow<CarePreferencesDto?>(null)
    private val passwordChangeStatus = MutableStateFlow(PasswordChangeStatus.IDLE)
    private val sessionOwnerId = MutableStateFlow<String?>(null)
    private var sessionOperations: CoroutineScope? = null
    private var invitationRevision = 0L
    private var carePreferencesRevision = 0L
    private val _events = MutableSharedFlow<MainEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private val localState = combine(
        preferences.state,
        periodRepository.observePeriods(),
        periodRepository.observePendingCount(),
        periodRepository.observeConflictCount(),
        periodRepository.observeDailyLogs(),
    ) { prefs, periods, pending, conflicts, dailyLogs ->
        MainUiState(
            sessionUserId = prefs.session?.userId,
            periods = periods.filter { it.userId == prefs.session?.userId },
            pendingMutations = pending,
            conflictMutations = conflicts,
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
        checkInSent, checkInError, invitation, partnerConnected, sharing, settingsBusy, settingsError,
        trackerPrediction, cycleProfile, carePreferences, passwordChangeStatus,
    ) { values ->
        SessionUiExtras(
            checkInSent = values[0] as Boolean,
            checkInError = values[1] as Boolean,
            invitation = values[2] as InvitationDto?,
            partnerConnected = values[3] as Boolean,
            sharing = values[4] as SharingSettingsDto?,
            settingsBusy = values[5] as Boolean,
            settingsError = values[6] as Boolean,
            trackerPrediction = values[7] as PredictionDto?,
            cycleProfile = values[8] as CycleProfileDto?,
            carePreferences = values[9] as CarePreferencesDto?,
            passwordChangeStatus = values[10] as PasswordChangeStatus,
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
                partnerConnected = extra.partnerConnected,
                sharing = extra.sharing,
                settingsBusy = extra.settingsBusy,
                settingsError = extra.settingsError,
                trackerPrediction = extra.trackerPrediction,
                cycleProfile = extra.cycleProfile,
                carePreferences = extra.carePreferences,
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
                    preferencesStateRole = session.role
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
                            launch { loadInvitationNow(session.userId) }
                            launch { loadSharingNow(session.userId) }
                            launch { refreshTrackerPredictionNow(session.userId) }
                            launch { loadCycleProfileNow(session.userId) }
                            launch { loadCarePreferencesNow(session.userId) }
                            launch {
                                preferences.state
                                    .map { it.lastSyncAt }
                                    .drop(1)
                                    .filterNotNull()
                                    .distinctUntilChanged()
                                    .collectLatest {
                                        refreshTrackerPredictionNow(session.userId)
                                        loadCycleProfileNow(session.userId)
                                        loadCarePreferencesNow(session.userId)
                                        loadInvitationNow(session.userId)
                                    }
                            }
                        }
                    }
                }
        }
    }

    private fun resetSessionState() {
        sessionOwnerId.value = null
        preferencesStateRole = null
        partnerDashboard.value = null
        partnerUnavailable.value = false
        checkInSent.value = false
        checkInError.value = false
        invitation.value = null
        partnerConnected.value = false
        invitationRevision += 1
        sharing.value = null
        settingsBusy.value = false
        settingsError.value = false
        trackerPrediction.value = null
        cycleProfile.value = null
        carePreferences.value = null
        carePreferencesRevision += 1
        passwordChangeStatus.value = PasswordChangeStatus.IDLE
    }

    fun startPeriod(userId: String) {
        if (!canManagePeriod(userId)) return
        launchForSession(userId) {
            runSessionOperation { periodRepository.startPeriod(userId) }
                .onSuccess { ifCurrentSession(userId) { _events.tryEmit(MainEvent.PeriodSaved) } }
                .onFailure { ifCurrentSession(userId) { _events.tryEmit(MainEvent.PeriodChangeFailed) } }
        }
    }

    fun updatePeriodDates(
        userId: String,
        periodId: String,
        startDate: LocalDate,
        endDate: LocalDate?,
    ) {
        if (!canManagePeriod(userId)) return
        launchForSession(userId) {
            runSessionOperation { periodRepository.updatePeriodDates(userId, periodId, startDate, endDate) }
                .onSuccess { ifCurrentSession(userId) { _events.tryEmit(MainEvent.PeriodUpdated) } }
                .onFailure { ifCurrentSession(userId) { _events.tryEmit(MainEvent.PeriodChangeFailed) } }
        }
    }

    fun deletePeriod(userId: String, periodId: String) {
        if (!canManagePeriod(userId)) return
        launchForSession(userId) {
            runSessionOperation { periodRepository.deletePeriod(userId, periodId) }
                .onSuccess { ifCurrentSession(userId) { _events.tryEmit(MainEvent.PeriodDeleted) } }
                .onFailure { ifCurrentSession(userId) { _events.tryEmit(MainEvent.PeriodChangeFailed) } }
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
        if (!canManagePeriod(userId)) return
        launchForSession(userId) {
            runSessionOperation {
                periodRepository.logToday(userId, flowLevel, painLevel, mood, energy, sleepQuality, symptoms)
            }
                .onSuccess { ifCurrentSession(userId) { _events.tryEmit(MainEvent.DailyLogSaved) } }
                .onFailure { ifCurrentSession(userId) { _events.tryEmit(MainEvent.DailyLogFailed) } }
        }
    }

    fun syncNow() {
        val ownerId = sessionOwnerId.value ?: return
        launchForSession(ownerId) { periodRepository.retryFailedMutations() }
    }

    fun discardConflictsAndReload() {
        val ownerId = sessionOwnerId.value ?: return
        if (!canManagePeriod(ownerId)) return
        launchForSession(ownerId) { periodRepository.discardConflictsAndReload() }
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

    private suspend fun loadCycleProfileNow(ownerId: String) {
        runSessionOperation { dashboardRepository.cycleProfile() }
            .onSuccess { profile -> ifCurrentSession(ownerId) { cycleProfile.value = profile } }
    }

    fun setFertilityPredictionEnabled(enabled: Boolean) {
        val ownerId = sessionOwnerId.value ?: return
        if (preferencesStateRole != UserRole.TRACKER || settingsBusy.value) return
        launchForSession(ownerId) {
            ifCurrentSession(ownerId) { settingsBusy.value = true }
            runSessionOperation { dashboardRepository.setFertilityPredictionEnabled(enabled) }
                .onSuccess { profile ->
                    ifCurrentSession(ownerId) {
                        cycleProfile.value = profile
                        settingsError.value = false
                        refreshTrackerPredictionNow(ownerId)
                    }
                }
                .onFailure { ifCurrentSession(ownerId) { settingsError.value = true } }
            ifCurrentSession(ownerId) { settingsBusy.value = false }
        }
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
        if (settingsBusy.value || invitation.value != null || partnerConnected.value) return
        val revision = ++invitationRevision
        launchForSession(ownerId) {
            ifCurrentSession(ownerId) { settingsBusy.value = true }
            runSessionOperation { householdRepository.createInvitation() }
                .onSuccess { result ->
                    ifCurrentSession(ownerId) {
                        if (invitationRevision == revision) invitation.value = result
                        settingsError.value = false
                    }
                }
                .onFailure {
                    ifCurrentSession(ownerId) {
                        if (invitationRevision == revision) settingsError.value = true
                    }
                }
            ifCurrentSession(ownerId) { settingsBusy.value = false }
        }
    }

    private suspend fun loadInvitationNow(ownerId: String) {
        val revision = invitationRevision
        runSessionOperation { householdRepository.invitationOverview() }
            .onSuccess { result ->
                ifCurrentSession(ownerId) {
                    if (invitationRevision == revision) {
                        invitation.value = result.invitation
                        partnerConnected.value = result.partnerConnected
                        settingsError.value = false
                    }
                }
            }
            .onFailure {
                ifCurrentSession(ownerId) {
                    if (invitationRevision == revision) settingsError.value = true
                }
            }
    }

    fun refreshInvitation() {
        val ownerId = sessionOwnerId.value ?: return
        if (preferencesStateRole != UserRole.TRACKER || settingsBusy.value) return
        launchForSession(ownerId) { loadInvitationNow(ownerId) }
    }

    fun revokeInvitation() {
        val ownerId = sessionOwnerId.value ?: return
        val currentInvitation = invitation.value ?: return
        if (settingsBusy.value) return
        val revision = ++invitationRevision
        launchForSession(ownerId) {
            ifCurrentSession(ownerId) { settingsBusy.value = true }
            runSessionOperation { householdRepository.revokeInvitation(currentInvitation.id) }
                .onSuccess {
                    ifCurrentSession(ownerId) {
                        if (invitationRevision == revision) invitation.value = null
                        settingsError.value = false
                    }
                }
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

    private suspend fun loadCarePreferencesNow(ownerId: String) {
        if (settingsBusy.value) return
        val revision = carePreferencesRevision
        runSessionOperation { carePreferencesRepository.load() }
            .onSuccess { result ->
                ifCurrentSession(ownerId) {
                    if (carePreferencesRevision == revision) {
                        carePreferences.value = result
                        settingsError.value = false
                    }
                }
            }
            .onFailure {
                ifCurrentSession(ownerId) {
                    if (carePreferencesRevision == revision) settingsError.value = true
                }
            }
    }

    fun updateCarePreferences(request: UpdateCarePreferencesRequest) {
        val ownerId = sessionOwnerId.value ?: return
        if (preferencesStateRole != UserRole.TRACKER || settingsBusy.value) return
        val revision = ++carePreferencesRevision
        launchForSession(ownerId) {
            ifCurrentSession(ownerId) { settingsBusy.value = true }
            runSessionOperation { carePreferencesRepository.update(request) }
                .onSuccess { result ->
                    ifCurrentSession(ownerId) {
                        if (carePreferencesRevision == revision) {
                            carePreferences.value = result
                            settingsError.value = false
                        }
                    }
                }
                .onFailure {
                    ifCurrentSession(ownerId) {
                        if (carePreferencesRevision == revision) settingsError.value = true
                    }
                }
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

    private fun canManagePeriod(userId: String): Boolean =
        sessionOwnerId.value == userId && preferencesStateRole?.let(de.lunara.app.core.model.RoleNavigation::canManagePeriods) == true

    private var preferencesStateRole: UserRole? = null

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
