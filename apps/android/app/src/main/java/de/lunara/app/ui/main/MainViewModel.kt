package de.lunara.app.ui.main

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lunara.app.core.model.UserRole
import de.lunara.app.data.local.PeriodEntity
import de.lunara.app.data.preferences.AppPreferences
import de.lunara.app.data.preferences.AppMode
import de.lunara.app.data.remote.dto.PartnerDashboardDto
import de.lunara.app.data.remote.dto.InvitationDto
import de.lunara.app.data.remote.dto.SharingSettingsDto
import de.lunara.app.data.remote.dto.UpdateSharingRequest
import de.lunara.app.data.remote.dto.PredictionDto
import de.lunara.app.data.remote.dto.CycleProfileDto
import de.lunara.app.data.remote.dto.CarePreferencesDto
import de.lunara.app.data.remote.dto.UpdateCarePreferencesRequest
import de.lunara.app.data.remote.dto.OidcStatusDto
import de.lunara.app.data.repository.DashboardRepository
import de.lunara.app.data.repository.PartnerRefreshPolicy
import de.lunara.app.data.repository.PartnerRefreshState
import de.lunara.app.data.repository.PeriodRepository
import de.lunara.app.data.repository.PeriodExportRepository
import de.lunara.app.data.repository.PeriodFileFormat
import de.lunara.app.data.repository.PeriodFileException
import de.lunara.app.data.repository.PeriodFileProblem
import de.lunara.app.data.repository.PeriodImportDraft
import de.lunara.app.data.repository.PeriodImportSummary
import de.lunara.app.data.repository.PeriodTransferIdentity
import de.lunara.app.data.repository.PeriodTransferCompletion
import de.lunara.app.data.repository.PeriodTransferCompletionPolicy
import de.lunara.app.data.repository.SessionCredentialGate
import de.lunara.app.data.repository.HouseholdRepository
import de.lunara.app.data.repository.RelationshipState
import de.lunara.app.data.repository.RelationshipPolicy
import de.lunara.app.data.repository.ApiException
import de.lunara.app.data.repository.PasswordRepository
import de.lunara.app.data.repository.ConnectionRepository
import de.lunara.app.data.repository.SharingRepository
import de.lunara.app.data.repository.CarePreferencesRepository
import de.lunara.app.data.backup.IncorrectBackupPassphraseException
import de.lunara.app.data.backup.InvalidPortableBackupException
import de.lunara.app.data.backup.PortableLocalBackupRepository
import de.lunara.app.data.sync.SyncScheduler
import de.lunara.app.notifications.NotificationPreferences
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
import kotlinx.coroutines.flow.first
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
import java.time.Instant

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
    val partnerDashboardRefreshing: Boolean = false,
    val partnerDashboardLastSyncAt: String? = null,
    val recoveryStatus: SessionRecoveryStatus = SessionRecoveryStatus.IDLE,
    val checkInSent: Boolean = false,
    val checkInError: Boolean = false,
    val invitation: InvitationDto? = null,
    val relationship: RelationshipState = RelationshipState(),
    val sharing: SharingSettingsDto? = null,
    val settingsBusy: Boolean = false,
    val settingsError: Boolean = false,
    val trackerPrediction: PredictionDto? = null,
    val predictionReferenceDate: LocalDate = LocalDate.now(),
    val cycleProfile: CycleProfileDto? = null,
    val carePreferences: CarePreferencesDto? = null,
    val passwordChangeStatus: PasswordChangeStatus = PasswordChangeStatus.IDLE,
    val oidc: OidcLinkUiState = OidcLinkUiState(),
    val backupStatus: LocalBackupStatus = LocalBackupStatus.IDLE,
    val periodTransfer: PeriodTransferUiState = PeriodTransferUiState(),
    val isLocalMode: Boolean = false,
    /** Advances after a period mutation fails so an open editor can safely unlock. */
    val periodFailureRevision: Long = 0L,
)

sealed interface MainEvent {
    /** Kept for compatibility with older UI collectors; new creates emit [PeriodAdded]. */
    data object PeriodSaved : MainEvent
    data object PeriodAdded : MainEvent
    data object PeriodEnded : MainEvent
    data object PeriodUpdated : MainEvent
    data object PeriodDeleted : MainEvent
    data object PeriodChangeFailed : MainEvent
    data object DailyLogSaved : MainEvent
    data object DailyLogFailed : MainEvent
    data object PasswordChanged : MainEvent
    data object SessionRecovered : MainEvent
}

data class OidcLinkUiState(
    val status: OidcStatusDto? = null,
    val busy: Boolean = false,
    val error: Boolean = false,
)

enum class LocalBackupStatus { IDLE, BUSY, EXPORTED, IMPORTED, BAD_PASSPHRASE, INVALID_BACKUP, FAILED }

enum class PeriodTransferStatus { IDLE, BUSY, PREVIEW, EXPORTED, IMPORTED, FAILED }
data class PeriodTransferUiState(
    val status: PeriodTransferStatus = PeriodTransferStatus.IDLE,
    val summary: PeriodImportSummary? = null,
    val completedCount: Int = 0,
    val problem: PeriodFileProblem? = null,
)

private data class SessionUiExtras(
    val checkInSent: Boolean,
    val checkInError: Boolean,
    val invitation: InvitationDto?,
    val relationship: RelationshipState,
    val sharing: SharingSettingsDto?,
    val settingsBusy: Boolean,
    val settingsError: Boolean,
    val trackerPrediction: PredictionDto?,
    val cycleProfile: CycleProfileDto?,
    val carePreferences: CarePreferencesDto?,
    val passwordChangeStatus: PasswordChangeStatus,
    val oidc: OidcLinkUiState,
    val backupStatus: LocalBackupStatus,
    val periodFailureRevision: Long,
    val recoveryStatus: SessionRecoveryStatus,
    val periodTransfer: PeriodTransferUiState,
)

private data class SessionBinding(
    val userId: String?,
    val role: UserRole?,
    val appMode: AppMode,
    val baseUrl: String?,
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
    private val connectionRepository: ConnectionRepository,
    private val portableBackupRepository: PortableLocalBackupRepository,
    private val periodExportRepository: PeriodExportRepository,
    private val credentialGate: SessionCredentialGate,
) : ViewModel() {
    private val partnerRefresh = MutableStateFlow(PartnerRefreshState())
    private val recoveryStatus = MutableStateFlow(SessionRecoveryStatus.IDLE)
    private val checkInSent = MutableStateFlow(false)
    private val checkInError = MutableStateFlow(false)
    private val invitation = MutableStateFlow<InvitationDto?>(null)
    private val relationship = MutableStateFlow(RelationshipState())
    private var tipVariant = 0
    private val sharing = MutableStateFlow<SharingSettingsDto?>(null)
    private val settingsBusy = MutableStateFlow(false)
    private val settingsError = MutableStateFlow(false)
    private val trackerPrediction = MutableStateFlow<PredictionDto?>(null)
    private val predictionClock = MutableStateFlow(Instant.now())
    private val cycleProfile = MutableStateFlow<CycleProfileDto?>(null)
    private val carePreferences = MutableStateFlow<CarePreferencesDto?>(null)
    private val passwordChangeStatus = MutableStateFlow(PasswordChangeStatus.IDLE)
    private val oidc = MutableStateFlow(OidcLinkUiState())
    private val backupStatus = MutableStateFlow(LocalBackupStatus.IDLE)
    private val periodTransfer = MutableStateFlow(PeriodTransferUiState())
    private var pendingPeriodImport: PeriodImportDraft? = null
    private var activePeriodTransfer: PeriodTransferIdentity? = null
    private var currentSessionBinding: SessionBinding? = null
    private val periodFailureRevision = MutableStateFlow(0L)
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
            isLocalMode = prefs.appMode == AppMode.LOCAL,
        )
    }
    private val sessionExtras = combine(
        checkInSent, checkInError, invitation, relationship, sharing, settingsBusy, settingsError,
        trackerPrediction, cycleProfile, carePreferences, passwordChangeStatus, oidc, backupStatus,
        periodFailureRevision, recoveryStatus, periodTransfer,
    ) { values ->
        SessionUiExtras(
            checkInSent = values[0] as Boolean,
            checkInError = values[1] as Boolean,
            invitation = values[2] as InvitationDto?,
            relationship = values[3] as RelationshipState,
            sharing = values[4] as SharingSettingsDto?,
            settingsBusy = values[5] as Boolean,
            settingsError = values[6] as Boolean,
            trackerPrediction = values[7] as PredictionDto?,
            cycleProfile = values[8] as CycleProfileDto?,
            carePreferences = values[9] as CarePreferencesDto?,
            passwordChangeStatus = values[10] as PasswordChangeStatus,
            oidc = values[11] as OidcLinkUiState,
            backupStatus = values[12] as LocalBackupStatus,
            periodFailureRevision = values[13] as Long,
            recoveryStatus = values[14] as SessionRecoveryStatus,
            periodTransfer = values[15] as PeriodTransferUiState,
        )
    }
    val state: StateFlow<MainUiState> = combine(
        localState,
        partnerRefresh,
        sessionOwnerId,
        sessionExtras,
        predictionClock,
    ) { local, dashboard, ownerId, extra, now ->
        // Activity-scoped ViewModels survive logout. Never project session-bound fields until
        // the observer below has reset and rebound them to the currently rendered account.
        if (!SessionUiIsolation.canExpose(local.sessionUserId, ownerId)) {
            local
        } else {
            local.copy(
                partnerDashboard = dashboard.snapshot?.dashboard,
                partnerDashboardUnavailable = dashboard.unavailable,
                partnerDashboardRefreshing = dashboard.refreshing,
                partnerDashboardLastSyncAt = dashboard.snapshot?.updatedAt,
                recoveryStatus = extra.recoveryStatus,
                checkInSent = extra.checkInSent,
                checkInError = extra.checkInError,
                invitation = extra.invitation,
                relationship = extra.relationship,
                sharing = extra.sharing,
                settingsBusy = extra.settingsBusy,
                settingsError = extra.settingsError,
                trackerPrediction = extra.trackerPrediction,
                predictionReferenceDate = PredictionFreshnessPolicy.referenceDate(now, extra.cycleProfile?.timezone.takeUnless { local.isLocalMode }),
                cycleProfile = extra.cycleProfile,
                carePreferences = extra.carePreferences,
                passwordChangeStatus = extra.passwordChangeStatus,
                oidc = extra.oidc,
                backupStatus = extra.backupStatus,
                periodTransfer = extra.periodTransfer,
                periodFailureRevision = extra.periodFailureRevision,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        viewModelScope.launch {
            preferences.state
                .map { state -> SessionBinding(state.session?.userId, state.session?.role, state.appMode, state.baseUrl) }
                .distinctUntilChanged()
                .collectLatest { binding ->
                    sessionOperations?.cancel()
                    sessionOperations = null
                    resetSessionState()
                    val session = preferences.state.first().session
                        ?.takeIf { it.userId == binding.userId && it.role == binding.role }
                        ?: return@collectLatest
                    val appMode = binding.appMode
                    sessionOwnerId.value = session.userId
                    preferencesStateRole = session.role
                    preferencesStateMode = appMode
                    currentSessionBinding = binding
                    sessionOperations = CoroutineScope(
                        viewModelScope.coroutineContext +
                            SupervisorJob(viewModelScope.coroutineContext[Job]),
                    )
                    if (appMode == AppMode.SERVER) {
                        sessionOperations?.launch { loadOidcStatusNow(session.userId) }
                        sessionOperations?.launch {
                            runSessionOperation { householdRepository.cachedRelationship(session.userId) }
                                .onSuccess { cached -> ifCurrentSession(session.userId) { cached?.let { relationship.value = it } } }
                            loadRelationshipNow(session.userId)
                            preferences.state.map { it.lastSyncAt }.drop(1).filterNotNull()
                                .distinctUntilChanged().collectLatest { loadRelationshipNow(session.userId) }
                        }
                    }

                    if (session.role == UserRole.PARTNER) {
                        if (appMode == AppMode.LOCAL) return@collectLatest
                        syncScheduler.requestImmediate()
                        coroutineScope {
                            launch {
                                dashboardRepository.observePartnerDashboardSnapshot().collectLatest { cached ->
                                    ifCurrentSession(session.userId) {
                                        partnerRefresh.value = PartnerRefreshPolicy.cached(partnerRefresh.value, cached)
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
                            launch { refreshTrackerPredictionNow(session.userId) }
                            launch { loadCycleProfileNow(session.userId) }
                            if (appMode == AppMode.SERVER) {
                                launch { loadInvitationNow(session.userId) }
                                launch { loadSharingNow(session.userId) }
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
    }

    private fun resetSessionState() {
        sessionOwnerId.value = null
        preferencesStateRole = null
        preferencesStateMode = null
        partnerRefresh.value = PartnerRefreshState()
        recoveryStatus.value = SessionRecoveryStatus.IDLE
        checkInSent.value = false
        checkInError.value = false
        invitation.value = null
        relationship.value = RelationshipState()
        tipVariant = 0
        invitationRevision += 1
        sharing.value = null
        settingsBusy.value = false
        settingsError.value = false
        trackerPrediction.value = null
        predictionClock.value = Instant.now()
        cycleProfile.value = null
        carePreferences.value = null
        carePreferencesRevision += 1
        passwordChangeStatus.value = PasswordChangeStatus.IDLE
        oidc.value = OidcLinkUiState()
        backupStatus.value = LocalBackupStatus.IDLE
        periodTransfer.value = PeriodTransferUiState()
        pendingPeriodImport = null
        activePeriodTransfer = null
        currentSessionBinding = null
        periodFailureRevision.value = 0L
    }

    fun startPeriod(userId: String) {
        if (!canManagePeriod(userId)) return
        launchForSession(userId) {
            runSessionOperation {
                val today = state.value.predictionReferenceDate
                periodRepository.startPeriod(userId, today, today)
            }
                .onSuccess {
                    ifCurrentSession(userId) { _events.tryEmit(MainEvent.PeriodSaved) }
                    refreshPredictionAfterLocalPeriodChange(userId)
                }
                .onFailure { reportPeriodFailure(userId) }
        }
    }

    fun createPeriod(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate? = null,
    ) {
        if (!canManagePeriod(userId)) return
        launchForSession(userId) {
            runSessionOperation { periodRepository.createPeriod(userId, startDate, endDate, state.value.predictionReferenceDate) }
                .onSuccess {
                    ifCurrentSession(userId) { _events.tryEmit(MainEvent.PeriodAdded) }
                    refreshPredictionAfterLocalPeriodChange(userId)
                }
                .onFailure { reportPeriodFailure(userId) }
        }
    }

    fun endPeriod(userId: String, periodId: String) {
        if (!canManagePeriod(userId)) return
        launchForSession(userId) {
            runSessionOperation {
                val today = state.value.predictionReferenceDate
                periodRepository.endPeriod(userId, periodId, today, today)
            }
                .onSuccess { changed ->
                    if (changed) {
                        ifCurrentSession(userId) { _events.tryEmit(MainEvent.PeriodEnded) }
                        refreshPredictionAfterLocalPeriodChange(userId)
                    }
                }
                .onFailure { reportPeriodFailure(userId) }
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
            runSessionOperation { periodRepository.updatePeriodDates(userId, periodId, startDate, endDate, state.value.predictionReferenceDate) }
                .onSuccess {
                    ifCurrentSession(userId) { _events.tryEmit(MainEvent.PeriodUpdated) }
                    refreshPredictionAfterLocalPeriodChange(userId)
                }
                .onFailure { reportPeriodFailure(userId) }
        }
    }

    fun deletePeriod(userId: String, periodId: String) {
        if (!canManagePeriod(userId)) return
        launchForSession(userId) {
            runSessionOperation { periodRepository.deletePeriod(userId, periodId) }
                .onSuccess {
                    ifCurrentSession(userId) { _events.tryEmit(MainEvent.PeriodDeleted) }
                    refreshPredictionAfterLocalPeriodChange(userId)
                }
                .onFailure { reportPeriodFailure(userId) }
        }
    }

    private fun reportPeriodFailure(userId: String) {
        ifCurrentSession(userId) {
            periodFailureRevision.value += 1
            _events.tryEmit(MainEvent.PeriodChangeFailed)
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
                periodRepository.logToday(userId, flowLevel, painLevel, mood, energy, sleepQuality, symptoms, state.value.predictionReferenceDate)
            }
                .onSuccess { ifCurrentSession(userId) { _events.tryEmit(MainEvent.DailyLogSaved) } }
                .onFailure { ifCurrentSession(userId) { _events.tryEmit(MainEvent.DailyLogFailed) } }
        }
    }

    fun syncNow() {
        if (preferencesStateMode == AppMode.LOCAL) return
        val ownerId = sessionOwnerId.value ?: return
        launchForSession(ownerId) { periodRepository.retryFailedMutations() }
    }

    fun discardConflictsAndReload() {
        if (preferencesStateMode == AppMode.LOCAL) return
        val ownerId = sessionOwnerId.value ?: return
        if (!canManagePeriod(ownerId)) return
        launchForSession(ownerId) { periodRepository.discardConflictsAndReload() }
    }

    fun refreshPartnerDashboard() {
        if (preferencesStateMode == AppMode.LOCAL || preferencesStateRole != UserRole.PARTNER) return
        val ownerId = sessionOwnerId.value ?: return
        launchForSession(ownerId) {
            coroutineScope {
                launch { loadRelationshipNow(ownerId) }
                refreshPartnerDashboardNow(ownerId)
            }
        }
    }

    private suspend fun refreshPartnerDashboardNow(ownerId: String) {
        if (partnerRefresh.value.refreshing) return
        ifCurrentSession(ownerId) { partnerRefresh.value = partnerRefresh.value.copy(refreshing = true) }
        try {
            runSessionOperation { dashboardRepository.partnerDashboard(ownerId, tipVariant) }
                .onSuccess {
                    ifCurrentSession(ownerId) {
                        partnerRefresh.value = PartnerRefreshPolicy.succeeded(partnerRefresh.value, it, Instant.now().toString())
                    }
                }
                .onFailure {
                    ifCurrentSession(ownerId) { partnerRefresh.value = PartnerRefreshPolicy.failed(partnerRefresh.value) }
                }
        } finally {
            ifCurrentSession(ownerId) { partnerRefresh.value = partnerRefresh.value.copy(refreshing = false) }
        }
    }

    fun nextPartnerTip() {
        if (preferencesStateMode != AppMode.SERVER || preferencesStateRole != UserRole.PARTNER || partnerRefresh.value.refreshing) return
        tipVariant = (tipVariant + 1) % 101
        refreshPartnerDashboard()
    }

    fun beginSessionRecovery(onReady: (Intent) -> Unit) {
        val ownerId = sessionOwnerId.value ?: return
        val mode = preferencesStateMode ?: return
        if (!SessionRecoveryUiPolicy.canStart(mode, ownerId, recoveryStatus.value)) return
        recoveryStatus.value = SessionRecoveryStatus.PREPARING
        launchForSession(ownerId) {
            try {
                runSessionOperation { connectionRepository.reauthenticationIntent() }
                    .onSuccess { intent ->
                        ifCurrentSession(ownerId) {
                            recoveryStatus.value = SessionRecoveryStatus.WAITING_BROWSER
                            try {
                                onReady(intent)
                            } catch (_: Exception) {
                                recoveryStatus.value = SessionRecoveryStatus.FAILED
                            }
                        }
                    }
                    .onFailure { error -> ifCurrentSession(ownerId) { recoveryStatus.value = SessionRecoveryUiPolicy.failure(error) } }
            } finally {
                ifCurrentSession(ownerId) {
                    if (recoveryStatus.value == SessionRecoveryStatus.PREPARING) recoveryStatus.value = SessionRecoveryStatus.IDLE
                }
            }
        }
    }

    fun completeSessionRecovery(result: Intent?) {
        if (preferencesStateMode != AppMode.SERVER || !SessionRecoveryUiPolicy.canCompleteBrowser(recoveryStatus.value)) return
        val ownerId = sessionOwnerId.value ?: return
        submitSessionRecovery(ownerId) { connectionRepository.reauthenticateWithAuthentik(result) }
    }

    fun recoverWithPassword(username: String, password: String) {
        val ownerId = sessionOwnerId.value ?: return
        val mode = preferencesStateMode ?: return
        if (!SessionRecoveryUiPolicy.canStart(mode, ownerId, recoveryStatus.value) || username.isBlank() || password.isEmpty()) return
        submitSessionRecovery(ownerId) { connectionRepository.reauthenticateWithPassword(username, password) }
    }

    private fun submitSessionRecovery(ownerId: String, authenticate: suspend () -> Unit) {
        recoveryStatus.value = SessionRecoveryStatus.SUBMITTING
        launchForSession(ownerId) {
            try {
                runSessionOperation { authenticate() }
                    .onSuccess {
                        ifCurrentSession(ownerId) {
                            recoveryStatus.value = SessionRecoveryStatus.SUCCESS
                            _events.tryEmit(MainEvent.SessionRecovered)
                        }
                        if (preferencesStateRole == UserRole.PARTNER) refreshPartnerDashboardNow(ownerId)
                        loadRelationshipNow(ownerId)
                    }
                    .onFailure { error -> ifCurrentSession(ownerId) { recoveryStatus.value = SessionRecoveryUiPolicy.failure(error) } }
            } finally {
                ifCurrentSession(ownerId) {
                    if (recoveryStatus.value == SessionRecoveryStatus.SUBMITTING) recoveryStatus.value = SessionRecoveryStatus.IDLE
                }
            }
        }
    }

    fun dismissSessionRecovery() {
        recoveryStatus.value = SessionRecoveryUiPolicy.dismissed(recoveryStatus.value)
    }

    fun refreshTrackerPrediction() {
        val ownerId = sessionOwnerId.value ?: return
        launchForSession(ownerId) { refreshTrackerPredictionNow(ownerId) }
    }

    /** Called on foreground entry and once at the next local/profile day boundary. */
    fun refreshPredictionDate() {
        predictionClock.value = Instant.now()
        // Local inputs have not changed: expire only the summary, keeping its calendar band.
        if (preferencesStateMode == AppMode.LOCAL) return
        val ownerId = sessionOwnerId.value ?: return
        launchForSession(ownerId) {
            if (preferencesStateRole == UserRole.TRACKER) refreshTrackerPredictionNow(ownerId)
            else if (preferencesStateMode == AppMode.SERVER) refreshPartnerDashboardNow(ownerId)
        }
    }

    private suspend fun refreshTrackerPredictionNow(ownerId: String) {
        try {
            runSessionOperation { dashboardRepository.trackerPrediction(ownerId) }
                .onSuccess { prediction -> ifCurrentSession(ownerId) { trackerPrediction.value = prediction } }
        } finally {
            ifCurrentSession(ownerId) {
                // A failed offline refresh must still advance the current-summary cutoff.
                predictionClock.value = Instant.now()
            }
        }
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
        if (preferencesStateMode == AppMode.LOCAL) return
        val ownerId = sessionOwnerId.value ?: return
        if (checkInSent.value) return
        launchForSession(ownerId) {
            runSessionOperation { dashboardRepository.sendCheckIn(prompt) }
                .onSuccess { ifCurrentSession(ownerId) { checkInSent.value = true; checkInError.value = false } }
                .onFailure { ifCurrentSession(ownerId) { checkInError.value = true } }
        }
    }

    fun createInvitation() {
        if (preferencesStateMode == AppMode.LOCAL) return
        val ownerId = sessionOwnerId.value ?: return
        if (settingsBusy.value || invitation.value != null || !relationship.value.known || relationship.value.connected || relationship.value.unavailable) return
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

    /** This is deliberately separate from sign-in: the UI has already confirmed the move. */
    fun acceptInvitation(invitationCode: String) {
        if (preferencesStateMode == AppMode.LOCAL || preferencesStateRole != UserRole.TRACKER) return
        val ownerId = sessionOwnerId.value ?: return
        if (settingsBusy.value) return
        launchForSession(ownerId) {
            ifCurrentSession(ownerId) {
                settingsBusy.value = true
                settingsError.value = false
            }
            runSessionOperation { connectionRepository.acceptInvitation(invitationCode) }
                .onFailure { ifCurrentSession(ownerId) { settingsError.value = true } }
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
        if (preferencesStateMode == AppMode.LOCAL) return
        val ownerId = sessionOwnerId.value ?: return
        if (settingsBusy.value) return
        launchForSession(ownerId) {
            coroutineScope {
                launch { loadRelationshipNow(ownerId) }
                if (preferencesStateRole == UserRole.TRACKER) loadInvitationNow(ownerId)
            }
        }
    }

    private suspend fun loadRelationshipNow(ownerId: String) {
        ifCurrentSession(ownerId) { relationship.value = relationship.value.copy(refreshing = true) }
        runSessionOperation { householdRepository.relationship(ownerId) }
            .onSuccess { result -> ifCurrentSession(ownerId) { relationship.value = result } }
            .onFailure { ifCurrentSession(ownerId) { relationship.value = RelationshipPolicy.failed(relationship.value) } }
    }

    fun revokeInvitation() {
        if (preferencesStateMode == AppMode.LOCAL) return
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
        if (preferencesStateMode == AppMode.LOCAL) return
        val ownerId = sessionOwnerId.value ?: return
        launchForSession(ownerId) { loadSharingNow(ownerId) }
    }

    private suspend fun loadSharingNow(ownerId: String) {
        runSessionOperation { sharingRepository.load() }
            .onSuccess { result -> ifCurrentSession(ownerId) { sharing.value = result; settingsError.value = false } }
            .onFailure { ifCurrentSession(ownerId) { settingsError.value = true } }
    }

    fun updateSharing(request: UpdateSharingRequest) {
        if (preferencesStateMode == AppMode.LOCAL) return
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
        if (preferencesStateMode == AppMode.LOCAL) return
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

    fun setNotificationPreferences(value: NotificationPreferences) {
        val ownerId = sessionOwnerId.value ?: return
        launchForSession(ownerId) {
            preferences.setNotificationPreferences(ownerId, value)
        }
    }

    fun changePassword(currentPassword: String, newPassword: String, confirmation: String) {
        if (preferencesStateMode == AppMode.LOCAL) return
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

    fun beginAuthentikLink(onReady: (Intent) -> Unit) {
        if (preferencesStateMode == AppMode.LOCAL) return
        val ownerId = sessionOwnerId.value ?: return
        if (oidc.value.busy || oidc.value.status?.enabled != true || oidc.value.status?.linked == true) return
        oidc.value = oidc.value.copy(busy = true, error = false)
        launchForSession(ownerId) {
            runCatching { connectionRepository.authentikAuthorizationIntent() }
                .onSuccess { intent ->
                    ifCurrentSession(ownerId) {
                        oidc.value = oidc.value.copy(busy = false)
                        onReady(intent)
                    }
                }
                .onFailure {
                    ifCurrentSession(ownerId) { oidc.value = oidc.value.copy(busy = false, error = true) }
                }
        }
    }

    fun completeAuthentikLink(result: Intent?, currentPassword: String) {
        if (preferencesStateMode == AppMode.LOCAL) return
        val ownerId = sessionOwnerId.value ?: return
        if (oidc.value.busy) return
        oidc.value = oidc.value.copy(busy = true, error = false)
        launchForSession(ownerId) {
            runCatching { connectionRepository.linkAuthentik(result, currentPassword) }
                .onSuccess { status ->
                    ifCurrentSession(ownerId) { oidc.value = OidcLinkUiState(status = status) }
                }
                .onFailure {
                    ifCurrentSession(ownerId) { oidc.value = oidc.value.copy(busy = false, error = true) }
                }
        }
    }

    fun clearAuthentikError() {
        oidc.value = oidc.value.copy(error = false)
    }

    fun exportLocalBackup(uri: Uri, passphrase: CharArray) {
        val ownerId = sessionOwnerId.value ?: return passphrase.fill('\u0000')
        if (preferencesStateMode != AppMode.LOCAL || backupStatus.value == LocalBackupStatus.BUSY) {
            passphrase.fill('\u0000')
            return
        }
        backupStatus.value = LocalBackupStatus.BUSY
        launchForSession(ownerId) {
            val result = runSessionOperation { portableBackupRepository.exportTo(uri, passphrase) }
            ifCurrentSession(ownerId) {
                backupStatus.value = if (result.isSuccess) LocalBackupStatus.EXPORTED else LocalBackupStatus.FAILED
            }
        }
    }

    fun importLocalBackup(uri: Uri, passphrase: CharArray) {
        val ownerId = sessionOwnerId.value ?: return passphrase.fill('\u0000')
        if (preferencesStateMode != AppMode.LOCAL || backupStatus.value == LocalBackupStatus.BUSY) {
            passphrase.fill('\u0000')
            return
        }
        backupStatus.value = LocalBackupStatus.BUSY
        launchForSession(ownerId) {
            val result = runSessionOperation { portableBackupRepository.importFrom(uri, passphrase) }
            if (result.isSuccess) {
                refreshTrackerPredictionNow(ownerId)
                loadCycleProfileNow(ownerId)
            }
            ifCurrentSession(ownerId) {
                backupStatus.value = when (result.exceptionOrNull()) {
                    null -> LocalBackupStatus.IMPORTED
                    is IncorrectBackupPassphraseException -> LocalBackupStatus.BAD_PASSPHRASE
                    is InvalidPortableBackupException -> LocalBackupStatus.INVALID_BACKUP
                    else -> LocalBackupStatus.FAILED
                }
            }
        }
    }

    fun clearLocalBackupStatus() {
        if (backupStatus.value != LocalBackupStatus.BUSY) backupStatus.value = LocalBackupStatus.IDLE
    }

    /** Called before launching a SAF picker; a stale callback can never select the new account. */
    fun beginPeriodTransfer(): PeriodTransferIdentity? {
        val identity = currentPeriodTransferIdentity() ?: return null
        if (periodTransfer.value.status == PeriodTransferStatus.BUSY) return null
        clearPeriodTransfer()
        return identity
    }

    fun exportPeriods(uri: Uri, format: PeriodFileFormat, identity: PeriodTransferIdentity) {
        if (!isPeriodTransferCurrent(identity) || periodTransfer.value.status == PeriodTransferStatus.BUSY) return
        activePeriodTransfer = identity
        periodTransfer.value = PeriodTransferUiState(status = PeriodTransferStatus.BUSY)
        launchForSession(identity.userId) {
            val result = runSessionOperation { periodExportRepository.exportTo(uri, format, identity, state.value.predictionReferenceDate) }
            finishPeriodTransfer(identity) {
                periodTransfer.value = result.fold(
                    onSuccess = { PeriodTransferUiState(PeriodTransferStatus.EXPORTED, completedCount = it) },
                    onFailure = ::periodTransferFailure,
                )
            }
        }
    }

    fun previewPeriodImport(uri: Uri, identity: PeriodTransferIdentity) {
        if (!isPeriodTransferCurrent(identity) || periodTransfer.value.status == PeriodTransferStatus.BUSY) return
        pendingPeriodImport = null
        activePeriodTransfer = identity
        periodTransfer.value = PeriodTransferUiState(status = PeriodTransferStatus.BUSY)
        launchForSession(identity.userId) {
            val result = runSessionOperation { periodExportRepository.preview(uri, identity, state.value.predictionReferenceDate) }
            finishPeriodTransfer(identity) {
                pendingPeriodImport = result.getOrNull()
                periodTransfer.value = result.fold(
                    onSuccess = { PeriodTransferUiState(PeriodTransferStatus.PREVIEW, summary = it.plan.summary) },
                    onFailure = ::periodTransferFailure,
                )
            }
        }
    }

    fun confirmPeriodImport() {
        val draft = pendingPeriodImport ?: return
        if (!isPeriodTransferCurrent(draft.identity)) {
            if (PeriodTransferCompletionPolicy.evaluate(draft.identity, draft.identity, currentPeriodTransferIdentity()) == PeriodTransferCompletion.ACCOUNT_CHANGED) {
                pendingPeriodImport = null
                periodTransfer.value = periodTransferFailure(PeriodFileException(PeriodFileProblem.ACCOUNT_CHANGED))
            }
            return
        }
        if (periodTransfer.value.status != PeriodTransferStatus.PREVIEW || !draft.plan.summary.canImport) return
        activePeriodTransfer = draft.identity
        periodTransfer.value = periodTransfer.value.copy(status = PeriodTransferStatus.BUSY)
        launchForSession(draft.identity.userId) {
            val result = runSessionOperation { periodRepository.importPeriodRanges(draft, state.value.predictionReferenceDate) }
            finishPeriodTransfer(draft.identity) {
                pendingPeriodImport = null
                periodTransfer.value = result.fold(
                    onSuccess = { PeriodTransferUiState(PeriodTransferStatus.IMPORTED, completedCount = it) },
                    onFailure = ::periodTransferFailure,
                )
            }
            if (result.isSuccess && isPeriodTransferCurrent(draft.identity)) refreshPredictionAfterLocalPeriodChange(draft.identity.userId)
        }
    }

    fun clearPeriodTransfer() {
        if (periodTransfer.value.status != PeriodTransferStatus.BUSY) {
            periodTransfer.value = PeriodTransferUiState()
            pendingPeriodImport = null
            activePeriodTransfer = null
        }
    }

    private fun currentPeriodTransferIdentity(): PeriodTransferIdentity? {
        val binding = currentSessionBinding ?: return null
        val owner = binding.userId ?: return null
        if (!canManagePeriod(owner)) return null
        return PeriodTransferIdentity(owner, binding.appMode, binding.baseUrl, credentialGate.snapshot())
    }

    private fun isPeriodTransferCurrent(identity: PeriodTransferIdentity): Boolean = currentPeriodTransferIdentity() == identity

    private fun finishPeriodTransfer(identity: PeriodTransferIdentity, apply: () -> Unit) {
        when (PeriodTransferCompletionPolicy.evaluate(identity, activePeriodTransfer, currentPeriodTransferIdentity())) {
            PeriodTransferCompletion.IGNORE -> return
            PeriodTransferCompletion.ACCOUNT_CHANGED -> {
                pendingPeriodImport = null
                periodTransfer.value = periodTransferFailure(PeriodFileException(PeriodFileProblem.ACCOUNT_CHANGED))
            }
            PeriodTransferCompletion.APPLY -> apply()
        }
        activePeriodTransfer = null
    }

    private fun periodTransferFailure(error: Throwable) = PeriodTransferUiState(
        status = PeriodTransferStatus.FAILED,
        problem = (error as? PeriodFileException)?.problem,
    )

    private suspend fun loadOidcStatusNow(ownerId: String) {
        val result = runSessionOperation { connectionRepository.oidcStatus() }
        ifCurrentSession(ownerId) {
            oidc.value = when {
                result.isSuccess -> OidcLinkUiState(status = result.getOrNull())
                (result.exceptionOrNull() as? ApiException)?.status == 404 -> OidcLinkUiState()
                else -> oidc.value.copy(error = true)
            }
        }
    }

    private inline fun ifCurrentSession(ownerId: String, action: () -> Unit) {
        if (sessionOwnerId.value == ownerId) action()
    }

    private fun canManagePeriod(userId: String): Boolean =
        sessionOwnerId.value == userId && preferencesStateRole?.let(de.lunara.app.core.model.RoleNavigation::canManagePeriods) == true

    private var preferencesStateRole: UserRole? = null
    private var preferencesStateMode: AppMode? = null

    private suspend fun refreshPredictionAfterLocalPeriodChange(ownerId: String) {
        if (preferencesStateMode == AppMode.LOCAL) refreshTrackerPredictionNow(ownerId)
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
