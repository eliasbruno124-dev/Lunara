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
import de.lunara.app.data.repository.SharingRepository
import de.lunara.app.data.sync.SyncScheduler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
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
)

sealed interface MainEvent {
    data object PeriodSaved : MainEvent
    data object DailyLogSaved : MainEvent
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val periodRepository: PeriodRepository,
    preferences: AppPreferences,
    private val mutablePreferences: AppPreferences,
    private val syncScheduler: SyncScheduler,
    private val dashboardRepository: DashboardRepository,
    private val householdRepository: HouseholdRepository,
    private val sharingRepository: SharingRepository,
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
    private val _events = MutableSharedFlow<MainEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private val localState = combine(
        preferences.state,
        periodRepository.observePeriods(),
        periodRepository.observePendingCount(),
        periodRepository.observeDailyLogs(),
    ) { prefs, periods, pending, dailyLogs ->
        MainUiState(
            periods = periods,
            pendingMutations = pending,
            dailyLogs = dailyLogs,
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
    private val baseState = combine(localState, dashboardState) { local, dashboard ->
        local.copy(partnerDashboard = dashboard.first, partnerDashboardUnavailable = dashboard.second)
    }
    private val settingsState = combine(
        checkInSent, checkInError, invitation, sharing, settingsBusy, settingsError, trackerPrediction,
    ) { values -> values }
    val state: StateFlow<MainUiState> = combine(baseState, settingsState) { base, extra ->
        base.copy(
            checkInSent = extra[0] as Boolean,
            checkInError = extra[1] as Boolean,
            invitation = extra[2] as InvitationDto?,
            sharing = extra[3] as SharingSettingsDto?,
            settingsBusy = extra[4] as Boolean,
            settingsError = extra[5] as Boolean,
            trackerPrediction = extra[6] as PredictionDto?,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        viewModelScope.launch {
            val session = preferences.state.filterNotNull().first().session
            if (session?.role == UserRole.PARTNER) {
                partnerDashboard.value = dashboardRepository.cachedPartnerDashboard()
                refreshPartnerDashboard()
            } else {
                loadSharing()
                refreshTrackerPrediction()
            }
        }
    }

    fun startPeriod(userId: String) {
        viewModelScope.launch {
            periodRepository.startPeriod(userId)
            _events.emit(MainEvent.PeriodSaved)
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
        viewModelScope.launch {
            periodRepository.logToday(userId, flowLevel, painLevel, mood, energy, sleepQuality, symptoms)
            _events.emit(MainEvent.DailyLogSaved)
        }
    }

    fun syncNow() = syncScheduler.requestImmediate()

    fun refreshPartnerDashboard() {
        viewModelScope.launch {
            runCatching { dashboardRepository.partnerDashboard() }
                .onSuccess {
                    partnerDashboard.value = it
                    partnerUnavailable.value = false
                }
                .onFailure { partnerUnavailable.value = true }
        }
    }

    fun refreshTrackerPrediction() {
        viewModelScope.launch {
            runCatching { dashboardRepository.trackerPrediction() }
                .onSuccess { trackerPrediction.value = it }
        }
    }

    fun sendCheckIn(prompt: String) {
        if (checkInSent.value) return
        viewModelScope.launch {
            runCatching { dashboardRepository.sendCheckIn(prompt) }
                .onSuccess { checkInSent.value = true; checkInError.value = false }
                .onFailure { checkInError.value = true }
        }
    }

    fun createInvitation() {
        viewModelScope.launch {
            settingsBusy.value = true
            runCatching { householdRepository.createInvitation() }
                .onSuccess { invitation.value = it; settingsError.value = false }
                .onFailure { settingsError.value = true }
            settingsBusy.value = false
        }
    }

    fun loadSharing() {
        viewModelScope.launch {
            runCatching { sharingRepository.load() }
                .onSuccess { sharing.value = it; settingsError.value = false }
                .onFailure { settingsError.value = true }
        }
    }

    fun updateSharing(request: UpdateSharingRequest) {
        viewModelScope.launch {
            settingsBusy.value = true
            runCatching { sharingRepository.update(request) }
                .onSuccess { sharing.value = it; settingsError.value = false }
                .onFailure { settingsError.value = true }
            settingsBusy.value = false
        }
    }

    fun setPrivacyMode(enabled: Boolean) {
        viewModelScope.launch { mutablePreferences.setPrivacyMode(enabled) }
    }

    fun setUseSystemTheme(enabled: Boolean) {
        viewModelScope.launch { mutablePreferences.setTheme(enabled, state.value.darkMode) }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { mutablePreferences.setTheme(false, enabled) }
    }
}
