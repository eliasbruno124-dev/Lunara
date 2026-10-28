package de.lunara.app.ui.main

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.gson.JsonParser
import de.lunara.app.BuildConfig
import de.lunara.app.R
import de.lunara.app.core.model.AppSession
import de.lunara.app.core.model.MainDestination
import de.lunara.app.core.model.RoleNavigation
import de.lunara.app.core.model.UserRole
import de.lunara.app.data.local.PeriodEntity
import de.lunara.app.data.local.DailyLogEntity
import de.lunara.app.data.preferences.AppPreferencesState
import de.lunara.app.data.preferences.AppMode
import de.lunara.app.data.remote.dto.PartnerDashboardDto
import de.lunara.app.data.remote.dto.PartnerTipDto
import de.lunara.app.data.remote.dto.PredictionDto
import de.lunara.app.data.remote.dto.CycleProfileDto
import de.lunara.app.data.remote.dto.CarePreferencesDto
import de.lunara.app.data.remote.dto.SharingSettingsDto
import de.lunara.app.data.remote.dto.UpdateCarePreferencesRequest
import de.lunara.app.data.remote.dto.UpdateSharingRequest
import de.lunara.app.data.repository.ApiPayloadPolicy
import de.lunara.app.data.repository.SyncEditPolicy
import de.lunara.app.data.repository.PeriodFileFormat
import de.lunara.app.data.repository.PeriodFileProblem
import de.lunara.app.data.repository.PeriodTransferIdentity
import de.lunara.app.security.limitAccountPasswordInput
import de.lunara.app.notifications.NotificationPreferences
import de.lunara.app.ui.components.InsightCard
import de.lunara.app.ui.components.LunaraHeader
import de.lunara.app.ui.components.LunaraWordmark
import de.lunara.app.ui.components.LunaraStatusNotice
import de.lunara.app.ui.components.StatusPill
import de.lunara.app.ui.components.QrCode
import de.lunara.app.ui.onboarding.canonicalInvitationLink
import de.lunara.app.ui.onboarding.parseInvitationQrPayload
import de.lunara.app.ui.scanner.lunaraQrScanOptions
import de.lunara.app.ui.theme.LunaraTheme
import de.lunara.app.ui.theme.CalendarCategoryColors
import de.lunara.app.ui.theme.LocalCalendarColors
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDate
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(
    session: AppSession,
    preferences: AppPreferencesState,
    onLogout: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val isLocalMode = preferences.appMode == AppMode.LOCAL
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val predictionTimeZone = state.cycleProfile?.timezone.takeUnless { isLocalMode }
    LaunchedEffect(session.userId, lifecycleOwner, predictionTimeZone) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshPredictionDate()
            while (isActive) {
                delay(PredictionFreshnessPolicy.millisecondsUntilNextDay(Instant.now(), predictionTimeZone))
                viewModel.refreshPredictionDate()
            }
        }
    }
    val navController = rememberNavController()
    val destinations = remember(session.role) { RoleNavigation.destinations(session.role) }
    val backStack by navController.currentBackStackEntryAsState()
    val selected = backStack?.destination?.route ?: MainDestination.TODAY.route
    val snackbar = remember { SnackbarHostState() }
    val periodSavedMessage = stringResource(if (isLocalMode) R.string.period_saved_local else R.string.period_saved_offline)
    val periodAddedMessage = stringResource(if (isLocalMode) R.string.period_added_local else R.string.period_added_offline)
    val periodEndedMessage = stringResource(if (isLocalMode) R.string.period_ended_local else R.string.period_ended_offline)
    val periodUpdatedMessage = stringResource(if (isLocalMode) R.string.period_updated_local else R.string.period_updated_offline)
    val periodDeletedMessage = stringResource(if (isLocalMode) R.string.period_deleted_local else R.string.period_deleted_offline)
    val periodChangeFailedMessage = stringResource(R.string.period_change_failed)
    val dailyLogSavedMessage = stringResource(if (isLocalMode) R.string.daily_log_saved_local else R.string.daily_log_saved_offline)
    val dailyLogFailedMessage = stringResource(R.string.daily_log_change_failed)
    val passwordChangedMessage = stringResource(R.string.password_change_success)
    val sessionRecoveredMessage = stringResource(R.string.session_recovery_success)
    var showSessionRecovery by rememberSaveable(session.userId) { mutableStateOf(false) }
    val recoveryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.completeSessionRecovery(result.data)
    }
    val openSessionRecovery = {
        viewModel.dismissSessionRecovery()
        showSessionRecovery = true
    }

    LaunchedEffect(state.recoveryStatus) {
        if (state.recoveryStatus == SessionRecoveryStatus.SUCCESS) {
            showSessionRecovery = false
            viewModel.dismissSessionRecovery()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            snackbar.showSnackbar(
                when (event) {
                    MainEvent.PeriodSaved -> periodSavedMessage
                    MainEvent.PeriodAdded -> periodAddedMessage
                    MainEvent.PeriodEnded -> periodEndedMessage
                    MainEvent.PeriodUpdated -> periodUpdatedMessage
                    MainEvent.PeriodDeleted -> periodDeletedMessage
                    MainEvent.PeriodChangeFailed -> periodChangeFailedMessage
                    MainEvent.DailyLogSaved -> dailyLogSavedMessage
                    MainEvent.DailyLogFailed -> dailyLogFailedMessage
                    MainEvent.PasswordChanged -> passwordChangedMessage
                    MainEvent.SessionRecovered -> sessionRecoveredMessage
                },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { LunaraWordmark(compact = true) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    SyncStatusAction(preferences, state, viewModel::syncNow, openSessionRecovery)
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = selected == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        },
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            if (SessionRecoveryUiPolicy.showBanner(preferences.appMode, session.userId, preferences.reauthenticationRequired)) {
                SessionRecoveryNotice(openSessionRecovery, Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            }
        NavHost(
            navController = navController,
            startDestination = MainDestination.TODAY.route,
            modifier = Modifier.weight(1f),
        ) {
            composable(MainDestination.TODAY.route) {
                if (session.role == UserRole.TRACKER) {
                    TrackerDashboard(
                        displayName = session.displayName,
                        periods = state.periods,
                        dailyLogs = state.dailyLogs,
                        prediction = state.trackerPrediction,
                        pendingMutations = state.pendingMutations,
                        conflictMutations = state.conflictMutations,
                        lastSyncAt = state.lastSyncAt,
                        onPeriodStarted = { viewModel.startPeriod(session.userId) },
                        onPeriodEnded = { id ->
                            viewModel.endPeriod(session.userId, id)
                        },
                        onPeriodUpdated = { id, start, end ->
                            viewModel.updatePeriodDates(session.userId, id, start, end)
                        },
                        onPeriodDeleted = { id -> viewModel.deletePeriod(session.userId, id) },
                        onDailyLog = { draft ->
                            viewModel.logToday(
                                session.userId,
                                draft.flowLevel,
                                draft.painLevel,
                                draft.mood,
                                draft.energy,
                                draft.sleepQuality,
                                draft.symptoms,
                            )
                        },
                        onSync = viewModel::syncNow,
                        onDiscardConflicts = viewModel::discardConflictsAndReload,
                        periodFailureRevision = state.periodFailureRevision,
                        showSync = !isLocalMode,
                        predictionReferenceDate = state.predictionReferenceDate,
                    )
                } else {
                    PartnerDashboard(
                        displayName = session.displayName,
                        dashboard = state.partnerDashboard,
                        unavailable = state.partnerDashboardUnavailable,
                        refreshing = state.partnerDashboardRefreshing,
                        lastSyncAt = state.partnerDashboardLastSyncAt,
                        onRefresh = viewModel::refreshPartnerDashboard,
                        checkInSent = state.checkInSent,
                        checkInError = state.checkInError,
                        onCheckIn = viewModel::sendCheckIn,
                        relationship = state.relationship,
                        onRelationshipRefresh = viewModel::refreshInvitation,
                        onNextTip = viewModel::nextPartnerTip,
                        predictionReferenceDate = state.predictionReferenceDate,
                    )
                }
            }
            composable(MainDestination.CALENDAR.route) {
                CalendarScreen(
                    periods = state.periods,
                    role = session.role,
                    prediction = if (session.role == UserRole.TRACKER) {
                        state.trackerPrediction
                    } else {
                        state.partnerDashboard?.prediction
                    },
                    sharedPeriodDates = state.partnerDashboard?.periodDates.orEmpty(),
                    partnerStatus = CalendarStatusPolicy.partner(
                        state.partnerDashboard,
                        state.partnerDashboardUnavailable,
                        state.partnerDashboardRefreshing,
                    ),
                    onPartnerRefresh = viewModel::refreshPartnerDashboard,
                    onPeriodAdded = { start, end ->
                        viewModel.createPeriod(session.userId, start, end)
                    },
                    onPeriodUpdated = { id, start, end ->
                        viewModel.updatePeriodDates(session.userId, id, start, end)
                    },
                    onPeriodDeleted = { id -> viewModel.deletePeriod(session.userId, id) },
                    periodFailureRevision = state.periodFailureRevision,
                )
            }
            composable(MainDestination.HISTORY.route) {
                HistoryScreen(
                    periods = state.periods,
                    prediction = state.trackerPrediction,
                    onPeriodUpdated = { id, start, end ->
                        viewModel.updatePeriodDates(session.userId, id, start, end)
                    },
                    onPeriodDeleted = { id -> viewModel.deletePeriod(session.userId, id) },
                    periodFailureRevision = state.periodFailureRevision,
                )
            }
            composable(MainDestination.SUPPORT.route) {
                SupportScreen(
                    state.partnerDashboard,
                    state.checkInSent,
                    state.checkInError,
                    viewModel::sendCheckIn,
                    onNextTip = viewModel::nextPartnerTip,
                    refreshing = state.partnerDashboardRefreshing,
                )
            }
            composable(MainDestination.SETTINGS.route) {
                SettingsScreen(
                    session,
                    preferences.copy(
                        privacyMode = state.privacyMode,
                        useSystemTheme = state.useSystemTheme,
                        darkMode = state.darkMode,
                    ),
                    onPrivacyMode = viewModel::setPrivacyMode,
                    onUseSystemTheme = viewModel::setUseSystemTheme,
                    onDarkMode = viewModel::setDarkMode,
                    onNotificationPreferences = viewModel::setNotificationPreferences,
                    onSync = viewModel::syncNow,
                    onRenewSession = openSessionRecovery,
                    sharing = state.sharing,
                    cycleProfile = state.cycleProfile,
                    carePreferences = state.carePreferences,
                    invitation = state.invitation,
                    relationship = state.relationship,
                    settingsBusy = state.settingsBusy,
                    settingsError = state.settingsError,
                    passwordChangeStatus = state.passwordChangeStatus,
                    oidc = state.oidc,
                    backupStatus = state.backupStatus,
                    onCreateInvitation = viewModel::createInvitation,
                    onAcceptInvitation = viewModel::acceptInvitation,
                    onRefreshInvitation = viewModel::refreshInvitation,
                    onRevokeInvitation = viewModel::revokeInvitation,
                    onUpdateSharing = viewModel::updateSharing,
                    onUpdateCarePreferences = viewModel::updateCarePreferences,
                    onFertilityPrediction = viewModel::setFertilityPredictionEnabled,
                    onChangePassword = viewModel::changePassword,
                    onClearPasswordChangeStatus = viewModel::clearPasswordChangeStatus,
                    onBeginAuthentikLink = viewModel::beginAuthentikLink,
                    onCompleteAuthentikLink = viewModel::completeAuthentikLink,
                    onClearAuthentikError = viewModel::clearAuthentikError,
                    onExportLocalBackup = viewModel::exportLocalBackup,
                    onImportLocalBackup = viewModel::importLocalBackup,
                    onClearLocalBackupStatus = viewModel::clearLocalBackupStatus,
                    onLogout = onLogout,
                    partnerCalendarStatus = CalendarStatusPolicy.partner(
                        state.partnerDashboard,
                        state.partnerDashboardUnavailable,
                        state.partnerDashboardRefreshing,
                    ),
                    onPartnerRefresh = viewModel::refreshPartnerDashboard,
                    periodTransfer = state.periodTransfer,
                    onBeginPeriodTransfer = viewModel::beginPeriodTransfer,
                    onExportPeriods = viewModel::exportPeriods,
                    onPreviewPeriodImport = viewModel::previewPeriodImport,
                    onConfirmPeriodImport = viewModel::confirmPeriodImport,
                    onClearPeriodTransfer = viewModel::clearPeriodTransfer,
                )
            }
        }
        }
    }
    if (showSessionRecovery && !isLocalMode) {
        SessionRecoveryDialog(
            displayName = session.displayName,
            status = state.recoveryStatus,
            showAuthentik = state.oidc.status?.enabled != false,
            showPassword = state.oidc.status?.passwordLoginEnabled != false,
            onAuthentik = { viewModel.beginSessionRecovery { recoveryLauncher.launch(it) } },
            onPassword = viewModel::recoverWithPassword,
            onDismiss = {
                viewModel.dismissSessionRecovery()
                showSessionRecovery = false
            },
        )
    }
}

@Composable
internal fun SessionRecoveryDialog(
    displayName: String,
    status: SessionRecoveryStatus,
    showAuthentik: Boolean,
    showPassword: Boolean,
    onAuthentik: () -> Unit,
    onPassword: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Credentials deliberately remain in memory, never SavedInstanceState or preferences.
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!status.busy) onDismiss() },
        title = { Text(stringResource(R.string.renew_login)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.session_recovery_account, displayName))
                Text(stringResource(R.string.session_recovery_preserves_data))
                if (showAuthentik) {
                    OutlinedButton(onClick = onAuthentik, enabled = !status.active, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.session_recovery_authentik))
                    }
                }
                if (showPassword) {
                    Text(stringResource(R.string.session_recovery_password_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it.take(64) },
                        label = { Text(stringResource(R.string.username)) },
                        enabled = !status.active,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = limitAccountPasswordInput(it) },
                        label = { Text(stringResource(R.string.password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !status.active,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            val submittedPassword = password
                            password = ""
                            onPassword(username.trim(), submittedPassword)
                        },
                        enabled = !status.active && username.isNotBlank() && password.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.session_recovery_password)) }
                }
                val message = when (status) {
                    SessionRecoveryStatus.PREPARING, SessionRecoveryStatus.SUBMITTING -> R.string.session_recovery_busy
                    SessionRecoveryStatus.WAITING_BROWSER -> R.string.session_recovery_browser_pending
                    SessionRecoveryStatus.CANCELLED -> R.string.session_recovery_cancelled
                    SessionRecoveryStatus.WRONG_ACCOUNT -> R.string.session_recovery_wrong_account
                    SessionRecoveryStatus.FAILED -> R.string.session_recovery_failed
                    else -> null
                }
                if (message != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (status.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            stringResource(message),
                            color = if (status == SessionRecoveryStatus.WRONG_ACCOUNT || status == SessionRecoveryStatus.FAILED) {
                                MaterialTheme.colorScheme.error
                            } else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, enabled = !status.busy) { Text(stringResource(R.string.cancel)) } },
    )
}

private val MainDestination.route: String get() = name.lowercase()
private val MainDestination.label: Int get() = when (this) {
    MainDestination.TODAY -> R.string.today
    MainDestination.CALENDAR -> R.string.calendar
    MainDestination.HISTORY -> R.string.history
    MainDestination.SUPPORT -> R.string.support
    MainDestination.SETTINGS -> R.string.settings
}
private val MainDestination.icon get() = when (this) {
    MainDestination.TODAY -> Icons.Rounded.Home
    MainDestination.CALENDAR -> Icons.Rounded.DateRange
    MainDestination.HISTORY -> Icons.AutoMirrored.Rounded.List
    MainDestination.SUPPORT -> Icons.Rounded.FavoriteBorder
    MainDestination.SETTINGS -> Icons.Rounded.Settings
}

@Composable
fun TrackerDashboard(
    displayName: String,
    periods: List<PeriodEntity>,
    dailyLogs: List<DailyLogEntity>,
    prediction: PredictionDto?,
    pendingMutations: Int,
    conflictMutations: Int,
    lastSyncAt: String?,
    onPeriodStarted: () -> Unit,
    onPeriodEnded: (String) -> Unit,
    onPeriodUpdated: (String, LocalDate, LocalDate?) -> Unit,
    onPeriodDeleted: (String) -> Unit,
    onDailyLog: (TodayLogDraft) -> Unit,
    onSync: () -> Unit,
    onDiscardConflicts: () -> Unit,
    periodFailureRevision: Long = 0L,
    showSync: Boolean = true,
    predictionReferenceDate: LocalDate = LocalDate.now(),
) {
    val haptics = LocalHapticFeedback.current
    var showTodayLog by remember { mutableStateOf(false) }
    var showDiscardConflicts by remember { mutableStateOf(false) }
    val today = LocalDate.now().toString()
    val todayLog = dailyLogs.firstOrNull { it.localDate == today }
    val periodAction = remember(periods, today) {
        PeriodPrimaryActionPolicy.resolve(periods, LocalDate.parse(today))
    }
    val dailyLogResolving = dailyLogs.any { it.localDate == today && it.syncState == "RESOLVING" }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp).testTag("tracker-dashboard"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            LunaraHeader(
                eyebrow = LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(LocalLocale.current.platformLocale)),
                title = stringResource(R.string.tracker_greeting, displayName),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        item { PredictionHero(prediction, referenceDate = predictionReferenceDate, pendingChanges = showSync && pendingMutations + conflictMutations > 0) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        when (periodAction.type) {
                            PeriodPrimaryActionType.START -> onPeriodStarted()
                            PeriodPrimaryActionType.END -> {
                                val periodId = periodAction.periodId ?: return@Button
                                onPeriodEnded(periodId)
                            }
                            PeriodPrimaryActionType.DONE -> Unit
                        }
                    },
                    enabled = periodAction.enabled && periodAction.type != PeriodPrimaryActionType.DONE,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .semantics { role = Role.Button }
                        .testTag(
                            when (periodAction.type) {
                                PeriodPrimaryActionType.START -> "period-start"
                                PeriodPrimaryActionType.END -> "period-end"
                                PeriodPrimaryActionType.DONE -> "period-done"
                            },
                        ),
                ) {
                    Icon(
                        if (periodAction.type == PeriodPrimaryActionType.START) {
                            Icons.Rounded.AddCircle
                        } else {
                            Icons.Rounded.DateRange
                        },
                        contentDescription = null,
                    )
                    Text(
                        stringResource(
                            when (periodAction.type) {
                                PeriodPrimaryActionType.START -> R.string.period_start_action
                                PeriodPrimaryActionType.END -> R.string.period_end_action
                                PeriodPrimaryActionType.DONE -> R.string.period_ended_today
                            },
                        ),
                        Modifier.padding(start = 10.dp),
                    )
                }
                when (periodAction.type) {
                    PeriodPrimaryActionType.END -> periodAction.startDate?.let { startDate ->
                        Text(
                            stringResource(R.string.period_active_since, formatDate(startDate.toString())),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    PeriodPrimaryActionType.DONE -> Text(
                        stringResource(R.string.period_ended_today_confirmation),
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    PeriodPrimaryActionType.START -> Unit
                }
                OutlinedButton(
                    onClick = { showTodayLog = true },
                    enabled = !dailyLogResolving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("today-log"),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.List, contentDescription = null)
                    Text(stringResource(R.string.today_log), Modifier.padding(start = 10.dp))
                }
            }
        }
        dailyLogs.firstOrNull()?.let { latest ->
            item { DailyLogRow(latest) }
        }
        PredictionPresentationPolicy.fertileWindow(prediction)?.let { fertileWindow ->
            item { FertilityEstimateCard(fertileWindow) }
        }
        if (showSync) {
            item {
                Column {
                    JournalSyncRow(syncDescription(pendingMutations, conflictMutations, lastSyncAt), onSync = onSync)
                    if (conflictMutations > 0) {
                        TextButton(onClick = { showDiscardConflicts = true }) {
                            Text(stringResource(R.string.resolve_sync_conflicts))
                        }
                    }
                }
            }
        }
        if (periods.isNotEmpty()) {
            item { JournalSection(stringResource(R.string.journal_recent_entries)) }
            items(periods.take(3), key = { it.id }) { period ->
                EditablePeriodRow(
                    period = period,
                    allPeriods = periods,
                    onUpdate = onPeriodUpdated,
                    onDelete = onPeriodDeleted,
                    periodFailureRevision = periodFailureRevision,
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
    if (showTodayLog) {
        TodayLogDialog(
            initial = todayLog.toTodayLogDraft(),
            onDismiss = { showTodayLog = false },
            onSave = { draft ->
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onDailyLog(draft)
                showTodayLog = false
            },
        )
    }
    if (showDiscardConflicts) {
        AlertDialog(
            onDismissRequest = { showDiscardConflicts = false },
            title = { Text(stringResource(R.string.resolve_sync_conflicts)) },
            text = { Text(stringResource(R.string.resolve_sync_conflicts_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDiscardConflicts()
                        showDiscardConflicts = false
                    },
                ) { Text(stringResource(R.string.use_server_version)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConflicts = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

data class TodayLogDraft(
    val flowLevel: String?,
    val painLevel: Int?,
    val mood: String?,
    val energy: Int?,
    val sleepQuality: Int?,
    val symptoms: List<String>,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayLogDialog(
    initial: TodayLogDraft,
    onDismiss: () -> Unit,
    onSave: (TodayLogDraft) -> Unit,
) {
    var flow by remember { mutableStateOf(initial.flowLevel) }
    var pain by remember { mutableStateOf(initial.painLevel?.toFloat()) }
    var energy by remember { mutableStateOf(initial.energy?.toFloat()) }
    var sleep by remember { mutableStateOf(initial.sleepQuality?.toFloat()) }
    var mood by remember { mutableStateOf(initial.mood.orEmpty()) }
    var symptoms by remember { mutableStateOf(initial.symptoms.joinToString(", ")) }
    val flowOptions = listOf(
        "SPOTTING" to stringResource(R.string.flow_spotting),
        "LIGHT" to stringResource(R.string.flow_light),
        "MEDIUM" to stringResource(R.string.flow_medium),
        "HEAVY" to stringResource(R.string.flow_heavy),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.today_log)) },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.flow_level), fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    flowOptions.forEach { (value, label) ->
                        FilterChip(
                            selected = flow == value,
                            onClick = { flow = value.takeUnless { flow == value } },
                            label = { Text(label) },
                        )
                    }
                }
                OptionalMetricSlider(stringResource(R.string.pain_level), pain, 0f) { pain = it }
                OptionalMetricSlider(stringResource(R.string.energy_level), energy, 5f) { energy = it }
                OptionalMetricSlider(stringResource(R.string.sleep_quality), sleep, 5f) { sleep = it }
                OutlinedTextField(
                    value = mood,
                    onValueChange = { mood = it.take(ApiPayloadPolicy.MAX_MOOD_LENGTH) },
                    label = { Text(stringResource(R.string.mood)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = symptoms,
                    onValueChange = { symptoms = it.take(500) },
                    label = { Text(stringResource(R.string.symptoms)) },
                    supportingText = { Text(stringResource(R.string.symptoms_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag("today-log-save"),
                onClick = {
                    onSave(
                        TodayLogDraft(
                            flowLevel = flow,
                            painLevel = pain?.toInt(),
                            mood = mood.trim().ifBlank { null },
                            energy = energy?.toInt(),
                            sleepQuality = sleep?.toInt(),
                            symptoms = ApiPayloadPolicy.sanitizeSymptoms(symptoms.split(',')),
                        ),
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun OptionalMetricSlider(
    label: String,
    value: Float?,
    suggestedValue: Float,
    onValueChange: (Float?) -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value?.let { stringResource(R.string.metric_value, label, it.toInt()) } ?: label,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onValueChange(if (value == null) suggestedValue else null) }) {
                Text(stringResource(if (value == null) R.string.add_metric else R.string.remove_metric))
            }
        }
        value?.let { selected ->
            Slider(
                value = selected,
                onValueChange = { onValueChange(it) },
                valueRange = 0f..10f,
                steps = 9,
                modifier = Modifier.semantics { contentDescription = label },
            )
        }
    }
}

internal fun DailyLogEntity?.toTodayLogDraft(): TodayLogDraft {
    if (this == null) return TodayLogDraft(null, null, null, null, null, emptyList())
    val symptoms = runCatching {
        JsonParser.parseString(symptomsJson).asJsonArray.mapNotNull { value ->
            value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        }
    }.getOrDefault(emptyList())
    return TodayLogDraft(
        flowLevel = flowLevel,
        painLevel = painLevel,
        mood = mood,
        energy = energy,
        sleepQuality = sleepQuality,
        symptoms = symptoms,
    )
}

@Composable
private fun DailyLogRow(log: DailyLogEntity) {
    val details = buildList {
        log.flowLevel?.let { add(localizedFlow(it)) }
        log.painLevel?.let { add(stringResource(R.string.pain_value, it)) }
        log.mood?.takeIf(String::isNotBlank)?.let(::add)
    }.joinToString(" · ").ifBlank { stringResource(R.string.daily_log_recorded) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(stringResource(R.string.daily_log_date, formatDate(log.localDate)), style = MaterialTheme.typography.titleSmall)
        Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(when (log.syncState) {
                "SYNCED" -> R.string.synced
                "LOCAL" -> R.string.stored_on_device
                "CONFLICT" -> R.string.sync_conflict
                "RESOLVING" -> R.string.sync_resolving
                else -> R.string.pending
            }),
            style = MaterialTheme.typography.labelMedium,
            color = if (log.syncState == "CONFLICT") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun localizedFlow(value: String): String = stringResource(
    when (value) {
        "SPOTTING" -> R.string.flow_spotting
        "LIGHT" -> R.string.flow_light
        "MEDIUM" -> R.string.flow_medium
        "HEAVY" -> R.string.flow_heavy
        else -> R.string.flow_level
    },
)

@Composable
fun PartnerDashboard(
    displayName: String,
    dashboard: PartnerDashboardDto?,
    unavailable: Boolean,
    onRefresh: () -> Unit,
    checkInSent: Boolean = false,
    checkInError: Boolean = false,
    onCheckIn: (String) -> Unit = {},
    refreshing: Boolean = false,
    lastSyncAt: String? = null,
    relationship: de.lunara.app.data.repository.RelationshipState = de.lunara.app.data.repository.RelationshipState(),
    onRelationshipRefresh: () -> Unit = {},
    onNextTip: () -> Unit = {},
    predictionReferenceDate: LocalDate = LocalDate.now(),
) {
    val checkInPrompt = stringResource(R.string.check_in)
    val initialLoading = dashboard == null && refreshing
    val awaitingDashboard = dashboard == null && (initialLoading || unavailable)
    val locale = LocalLocale.current.platformLocale
    val lastSyncLabel = remember(lastSyncAt, locale) {
        lastSyncAt?.let {
            runCatching {
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                    .withLocale(locale).withZone(ZoneId.systemDefault()).format(Instant.parse(it))
            }.getOrNull()
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp).testTag("partner-dashboard"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            LunaraHeader(
                eyebrow = stringResource(R.string.today),
                title = stringResource(R.string.partner_greeting, displayName),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        item { RelationshipCard(relationship, onRelationshipRefresh, compact = true) }
        if (unavailable && !initialLoading) {
            item {
                LunaraStatusNotice(
                    icon = Icons.Rounded.Info,
                    title = stringResource(if (dashboard == null) R.string.journal_connection_unavailable else R.string.journal_saved_overview),
                    body = stringResource(if (dashboard == null) R.string.partner_dashboard_unavailable else R.string.partner_dashboard_cached),
                    modifier = Modifier.testTag("partner-offline-notice"),
                    loading = refreshing,
                    action = { TextButton(onClick = onRefresh, enabled = !refreshing, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.journal_retry)) } },
                )
            }
        }
        item { JournalSection(stringResource(R.string.journal_shared_overview), subtitle = stringResource(R.string.privacy_guard)) }
        if (initialLoading) item {
            LunaraStatusNotice(
                icon = Icons.Rounded.Refresh,
                title = stringResource(R.string.partner_dashboard_refreshing),
                body = stringResource(R.string.journal_loading_shared),
                loading = true,
                modifier = Modifier.testTag("partner-loading-notice"),
            )
        }
        if (!awaitingDashboard) item {
            if (JournalPredictionPolicy.hasStartWindow(dashboard?.prediction, predictionReferenceDate) ||
                PredictionFreshnessPolicy.unavailableReason(dashboard?.prediction, predictionReferenceDate) == "STALE_HISTORY"
            ) PredictionHero(dashboard?.prediction, referenceDate = predictionReferenceDate)
            else InsightCard(Icons.Rounded.DateRange, stringResource(R.string.journal_partner_prediction_empty), stringResource(R.string.journal_partner_prediction_empty_body))
        }
        PredictionPresentationPolicy.disclosedPartnerPhase(dashboard)?.let { phase ->
            item {
                SettingText(Icons.Rounded.Info, localizedPhase(phase), stringResource(R.string.current_estimated_phase))
            }
        }
        PredictionPresentationPolicy.fertileWindow(dashboard?.prediction)?.let { fertileWindow ->
            item { FertilityEstimateCard(fertileWindow) }
        }
        if (!awaitingDashboard) item {
            val tip = dashboard?.tip
            if (tip == null) {
                InsightCard(
                    Icons.Rounded.Info,
                    stringResource(R.string.partner_tip_title),
                    stringResource(R.string.no_partner_tip_available),
                )
            } else {
                PartnerActionCard(tip, onNextTip, refreshing, PredictionPresentationPolicy.phaseAwareTipPhase(dashboard) != null)
            }
        }
        item {
            FilledTonalButton(onClick = { onCheckIn(checkInPrompt) }, enabled = !checkInSent, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(Icons.Rounded.FavoriteBorder, contentDescription = null)
                Text(stringResource(R.string.check_in), Modifier.padding(start = 10.dp))
            }
        }
        if (checkInSent) item { Text(stringResource(R.string.check_in_sent), color = MaterialTheme.colorScheme.tertiary) }
        if (checkInError) item { Text(stringResource(R.string.check_in_error), color = MaterialTheme.colorScheme.error) }
        if (!initialLoading) item {
            JournalSyncRow(
                label = if (refreshing) stringResource(R.string.partner_dashboard_refreshing)
                    else lastSyncLabel?.let { stringResource(R.string.last_sync_value, it) } ?: stringResource(R.string.last_sync_never),
                refreshing = refreshing,
                onSync = onRefresh,
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun FertilityEstimateCard(range: PredictionDateRange) {
    InsightCard(
        Icons.Rounded.Info,
        stringResource(R.string.estimated_fertile_window),
        formatRange(range.from, range.to),
        action = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill(stringResource(R.string.estimated_label))
                FertilitySafetyNotice()
            }
        },
    )
}

@Composable
private fun FertilitySafetyNotice() {
    Text(
        stringResource(R.string.fertility_safety_notice),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun PartnerCalendarConnectionNotice(status: PartnerCalendarStatus, onRefresh: () -> Unit) {
    if (status.load == CalendarLoadStatus.READY) return
    val loading = status.load == CalendarLoadStatus.LOADING
    LunaraStatusNotice(
        icon = Icons.Rounded.Info,
        title = stringResource(when (status.load) {
            CalendarLoadStatus.LOADING -> R.string.partner_dashboard_refreshing
            CalendarLoadStatus.CACHED -> R.string.journal_saved_overview
            else -> R.string.journal_connection_unavailable
        }),
        body = stringResource(when (status.load) {
            CalendarLoadStatus.LOADING -> R.string.calendar_shared_loading
            CalendarLoadStatus.CACHED -> R.string.calendar_shared_cached
            else -> R.string.calendar_shared_unavailable
        }),
        loading = status.refreshing,
        modifier = Modifier.testTag("calendar-${status.load.name.lowercase(Locale.ROOT)}-notice"),
        action = if (loading) null else {
            {
                TextButton(
                    onClick = onRefresh,
                    enabled = !status.refreshing,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("calendar-retry"),
                ) { Text(stringResource(R.string.journal_retry)) }
            }
        },
    )
}

@Composable
private fun PartnerCalendarNotices(status: PartnerCalendarStatus, onRefresh: () -> Unit) {
    val messages = listOfNotNull(
        when (status.periodNotice) {
            CalendarPeriodNotice.NOT_SHARED -> stringResource(R.string.calendar_periods_not_shared)
            CalendarPeriodNotice.EMPTY -> stringResource(R.string.calendar_periods_empty)
            CalendarPeriodNotice.UNKNOWN -> stringResource(R.string.calendar_periods_unknown)
            null -> null
        },
        when (status.fertilityNotice) {
            CalendarFertilityNotice.NOT_SHARED -> stringResource(R.string.calendar_fertility_not_shared)
            CalendarFertilityNotice.DISABLED -> stringResource(R.string.calendar_fertility_disabled)
            CalendarFertilityNotice.INSUFFICIENT_DATA -> stringResource(R.string.calendar_fertility_insufficient)
            CalendarFertilityNotice.UNRELIABLE_DATA -> stringResource(R.string.calendar_fertility_unreliable)
            CalendarFertilityNotice.UNAVAILABLE -> stringResource(R.string.calendar_fertility_unavailable)
            null -> null
        },
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PartnerCalendarConnectionNotice(status, onRefresh)
        if (status.sharingPaused || messages.isNotEmpty()) {
            LunaraStatusNotice(
                icon = Icons.Rounded.Lock,
                title = stringResource(R.string.calendar_visibility),
                body = if (status.sharingPaused) stringResource(R.string.calendar_sharing_paused)
                    else messages.joinToString("\n"),
                modifier = Modifier.testTag("calendar-sharing-notice"),
            )
        }
    }
}

@Composable
internal fun PartnerCalendarAccessSettings(status: PartnerCalendarStatus, onRefresh: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingHeader(stringResource(R.string.calendar_visibility))
        Text(stringResource(R.string.partner_calendar_access_summary), style = MaterialTheme.typography.bodySmall)
        PartnerCalendarConnectionNotice(status, onRefresh)
        if (status.sharingPaused) Text(stringResource(R.string.calendar_sharing_paused))
        PartnerCalendarAccessRow(R.string.partner_calendar_periods, status.periodAccess, "partner-period-access")
        PartnerCalendarAccessRow(R.string.estimated_fertile_window, status.fertilityAccess, "partner-fertility-access")
        if (status.load == CalendarLoadStatus.READY) {
            TextButton(onClick = onRefresh, enabled = !status.refreshing, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (status.refreshing) R.string.partner_dashboard_refreshing else R.string.journal_refresh))
            }
        }
    }
}

@Composable
private fun PartnerCalendarAccessRow(title: Int, access: CalendarAccessStatus, tag: String) {
    Column(Modifier.testTag(tag).semantics(mergeDescendants = true) {}) {
        SettingText(
            Icons.Rounded.Lock,
            stringResource(title),
            stringResource(when (access) {
                CalendarAccessStatus.SHARED -> R.string.partner_calendar_access_shared
                CalendarAccessStatus.NOT_SHARED -> R.string.partner_calendar_access_denied
                CalendarAccessStatus.LOADING -> R.string.partner_calendar_access_loading
                CalendarAccessStatus.PAUSED -> R.string.partner_calendar_access_paused
                CalendarAccessStatus.UNKNOWN -> R.string.partner_calendar_access_unknown
            }),
        )
    }
}

@Composable
internal fun CalendarScreen(
    periods: List<PeriodEntity>,
    role: UserRole,
    prediction: PredictionDto?,
    sharedPeriodDates: List<Map<String, Any?>>,
    onPeriodAdded: (LocalDate, LocalDate?) -> Unit,
    onPeriodUpdated: (String, LocalDate, LocalDate?) -> Unit,
    onPeriodDeleted: (String) -> Unit,
    periodFailureRevision: Long = 0L,
    partnerStatus: PartnerCalendarStatus? = null,
    onPartnerRefresh: () -> Unit = {},
) {
    val today = LocalDate.now()
    val currentMonth = YearMonth.from(today)
    val lastCalendarMonth = remember(currentMonth) {
        CalendarMonthPagingPolicy.mainCalendarLastMonth(currentMonth)
    }
    val calendarPageCount = remember(lastCalendarMonth) {
        CalendarMonthPagingPolicy.pageCount(lastCalendarMonth)
    }
    val currentMonthPage = remember(currentMonth, lastCalendarMonth) {
        CalendarMonthPagingPolicy.pageForMonth(currentMonth, lastCalendarMonth)
    }
    val monthPagerState = rememberPagerState(initialPage = currentMonthPage) { calendarPageCount }
    val month = CalendarMonthPagingPolicy.monthForPage(monthPagerState.currentPage, lastCalendarMonth)
    val calendarScope = rememberCoroutineScope()
    var selectedNewStart by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPeriodId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingNewStartDate by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingNewEndDate by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingNewFailureRevision by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingUpdatedStartDate by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingUpdatedEndDate by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeletePeriodId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingEditorFailureRevision by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedPeriod = periods.firstOrNull { it.id == selectedPeriodId }
    val createSubmitting = pendingNewStartDate != null
    val editorSubmitting = pendingUpdatedStartDate != null || pendingDeletePeriodId != null
    val locale = LocalLocale.current.platformLocale
    val visiblePrediction = prediction.takeUnless { role == UserRole.PARTNER && partnerStatus?.showPrediction == false }
    val predictionBand = remember(visiblePrediction) { CalendarPredictionBand.from(visiblePrediction) }
    val fertilityRange = remember(visiblePrediction, role, partnerStatus?.showFertility) {
        PredictionPresentationPolicy.fertileWindow(visiblePrediction)
            .takeUnless { role == UserRole.PARTNER && partnerStatus?.showFertility == false }
    }
    val visibleSharedDates = sharedPeriodDates.takeUnless {
        role == UserRole.PARTNER && partnerStatus?.showPeriodDates == false
    }.orEmpty()
    var showCalendarHelp by remember { mutableStateOf(false) }
    val calendarFontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    LaunchedEffect(periods, pendingNewStartDate, pendingNewEndDate) {
        val pendingStart = pendingNewStartDate ?: return@LaunchedEffect
        if (
            periods.any {
                it.deletedAt == null &&
                    it.startDate == pendingStart &&
                    it.endDate == pendingNewEndDate
            }
        ) {
            pendingNewStartDate = null
            pendingNewEndDate = null
            pendingNewFailureRevision = null
            selectedNewStart = null
        }
    }
    LaunchedEffect(selectedPeriod, pendingUpdatedStartDate, pendingUpdatedEndDate) {
        val pendingStart = pendingUpdatedStartDate ?: return@LaunchedEffect
        if (
            selectedPeriod != null &&
            selectedPeriod.startDate == pendingStart &&
            selectedPeriod.endDate == pendingUpdatedEndDate
        ) {
            pendingUpdatedStartDate = null
            pendingUpdatedEndDate = null
            pendingEditorFailureRevision = null
            selectedPeriodId = null
        }
    }
    LaunchedEffect(periods, pendingDeletePeriodId) {
        val pendingId = pendingDeletePeriodId ?: return@LaunchedEffect
        if (periods.none { it.id == pendingId && it.deletedAt == null }) {
            pendingDeletePeriodId = null
            pendingEditorFailureRevision = null
            selectedPeriodId = null
        }
    }
    LaunchedEffect(periodFailureRevision, pendingNewFailureRevision) {
        val submittedAt = pendingNewFailureRevision ?: return@LaunchedEffect
        if (periodFailureRevision != submittedAt) {
            pendingNewStartDate = null
            pendingNewEndDate = null
            pendingNewFailureRevision = null
        }
    }
    LaunchedEffect(periodFailureRevision, pendingEditorFailureRevision) {
        val submittedAt = pendingEditorFailureRevision ?: return@LaunchedEffect
        if (periodFailureRevision != submittedAt) {
            pendingUpdatedStartDate = null
            pendingUpdatedEndDate = null
            pendingDeletePeriodId = null
            pendingEditorFailureRevision = null
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp).testTag("calendar-screen"), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            LunaraHeader(
                eyebrow = stringResource(R.string.journal_calendar_eyebrow),
                title = stringResource(R.string.calendar),
                body = stringResource(if (role == UserRole.TRACKER) R.string.journal_calendar_hint else R.string.privacy_guard),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
            )
        }
        if (role == UserRole.PARTNER && partnerStatus != null) {
            item { PartnerCalendarNotices(partnerStatus, onPartnerRefresh) }
        }
        item {
            HorizontalPager(
                state = monthPagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height((432 * calendarFontScale).dp)
                    .testTag("calendar-month-pager"),
                key = { page ->
                    CalendarMonthPagingPolicy.monthForPage(page, lastCalendarMonth).toString()
                },
            ) { page ->
                val pageMonth = CalendarMonthPagingPolicy.monthForPage(page, lastCalendarMonth)
                val first = pageMonth.atDay(1)
                val leading = first.dayOfWeek.value - 1
                val populatedCells = List<LocalDate?>(leading) { null } +
                    (1..pageMonth.lengthOfMonth()).map(pageMonth::atDay)
                val cells = populatedCells + List(42 - populatedCells.size) { null }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .testTag("calendar-month-$pageMonth"),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        pageMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .semantics { heading() }
                            .testTag("calendar-month-title-$pageMonth"),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(Modifier.fillMaxWidth()) {
                        java.time.DayOfWeek.entries.forEach { day ->
                            Text(
                                day.getDisplayName(java.time.format.TextStyle.NARROW, locale),
                                Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                    cells.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            week.forEach { date ->
                                if (date == null) {
                                    Spacer(Modifier.weight(1f).height((52 * calendarFontScale).dp))
                                } else {
                                    val selectable = role == UserRole.TRACKER && !date.isAfter(today)
                                    val ownPeriod = if (role == UserRole.TRACKER) periodForDate(periods, date, today) else null
                                    val existing = if (selectable) ownPeriod else null
                                    val editable = selectable &&
                                        (existing == null || !SyncEditPolicy.isBlocked(existing.syncState))
                                    val actual = ownPeriod != null || visibleSharedDates.any {
                                        calendarRangeContains(
                                            date = date,
                                            startDate = it["startDate"] as? String,
                                            endDate = it["endDate"] as? String,
                                            today = today,
                                        )
                                    }
                                    DayCell(
                                        date = date,
                                        isToday = date == today,
                                        actual = actual,
                                        predicted = predictionBand?.contains(date) == true,
                                        fertileEstimate = fertilityRange?.let {
                                            calendarRangeContains(
                                                date = date,
                                                startDate = it.from,
                                                endDate = it.to,
                                                today = today,
                                                extendOpenRange = false,
                                            )
                                        } == true,
                                        actionDescription = if (!editable) {
                                            null
                                        } else if (existing != null) {
                                            stringResource(R.string.calendar_edit_day)
                                        } else {
                                            stringResource(R.string.calendar_add_day)
                                        },
                                        onClick = if (!editable) {
                                            null
                                        } else {
                                            {
                                                if (existing != null) {
                                                    selectedPeriodId = existing.id
                                                } else {
                                                    selectedNewStart = date.toString()
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f).testTag("calendar-day-$date"),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            CalendarLegend(showFertility = fertilityRange != null)
        }
        if (fertilityRange != null) {
            item { Column(Modifier.testTag("calendar-fertility-safety")) { FertilitySafetyNotice() } }
        }
        if (role == UserRole.TRACKER) {
            item {
                TextButton(onClick = { showCalendarHelp = true }) {
                    Icon(Icons.Rounded.Info, contentDescription = null)
                    Text(stringResource(R.string.journal_calendar_help), Modifier.padding(start = 8.dp))
                }
            }
        }
        if (month != currentMonth) {
            item {
                TextButton(
                    onClick = {
                        calendarScope.launch { monthPagerState.animateScrollToPage(currentMonthPage) }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("calendar-current-month"),
                ) { Text(stringResource(R.string.current_month)) }
            }
        }
    }

    if (showCalendarHelp) AlertDialog(
        onDismissRequest = { showCalendarHelp = false },
        title = { Text(stringResource(R.string.journal_calendar_help)) },
        text = { Text(stringResource(R.string.calendar_tracker_hint)) },
        confirmButton = { TextButton(onClick = { showCalendarHelp = false }) { Text(stringResource(R.string.close)) } },
    )

    selectedNewStart?.let { rawStart ->
        PeriodCreateDialog(
            initialStartDate = runCatching { LocalDate.parse(rawStart) }.getOrDefault(LocalDate.now()),
            allPeriods = periods,
            submitting = createSubmitting,
            onCreate = { start, end ->
                if (pendingNewStartDate == null) {
                    pendingNewStartDate = start.toString()
                    pendingNewEndDate = end?.toString()
                    pendingNewFailureRevision = periodFailureRevision
                    onPeriodAdded(start, end)
                }
            },
            onDismiss = {
                pendingNewStartDate = null
                pendingNewEndDate = null
                pendingNewFailureRevision = null
                selectedNewStart = null
            },
        )
    }
    selectedPeriod?.let { period ->
        PeriodEditorDialog(
            period = period,
            allPeriods = periods,
            submitting = editorSubmitting,
            onUpdate = { start, end ->
                if (pendingUpdatedStartDate == null && pendingDeletePeriodId == null) {
                    pendingUpdatedStartDate = start.toString()
                    pendingUpdatedEndDate = end?.toString()
                    pendingEditorFailureRevision = periodFailureRevision
                    onPeriodUpdated(period.id, start, end)
                }
            },
            onDelete = {
                if (pendingUpdatedStartDate == null && pendingDeletePeriodId == null) {
                    pendingDeletePeriodId = period.id
                    pendingEditorFailureRevision = periodFailureRevision
                    onPeriodDeleted(period.id)
                }
            },
            onDismiss = {
                pendingUpdatedStartDate = null
                pendingUpdatedEndDate = null
                pendingDeletePeriodId = null
                pendingEditorFailureRevision = null
                selectedPeriodId = null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CalendarLegend(showFertility: Boolean) {
    val colors = LocalCalendarColors.current
    FlowRow(
        modifier = Modifier.fillMaxWidth().testTag("calendar-legend"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CalendarLegendItem(R.string.calendar_actual_marker, colors.recorded, "recorded")
        CalendarLegendItem(R.string.calendar_prediction_marker, colors.predicted, "predicted", dashed = true)
        if (showFertility) {
            CalendarLegendItem(R.string.calendar_fertility_marker, colors.fertile, "fertile")
        }
    }
}

@Composable
private fun CalendarLegendItem(label: Int, colors: CalendarCategoryColors, tag: String, dashed: Boolean = false) {
    Surface(
        modifier = Modifier.testTag("calendar-legend-$tag").then(
            if (dashed) Modifier.calendarDashedOutline(colors.content) else Modifier,
        ),
        shape = RoundedCornerShape(10.dp),
        color = colors.container,
        contentColor = colors.content,
        border = if (dashed) null else BorderStroke(1.dp, colors.content),
    ) {
        Text(stringResource(label), Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
    }
}

private fun Modifier.calendarDashedOutline(color: Color): Modifier = drawWithContent {
    drawContent()
    val strokeWidth = 1.5.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
        size = Size(size.width - strokeWidth, size.height - strokeWidth),
        cornerRadius = CornerRadius(10.dp.toPx()),
        style = Stroke(
            width = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx())),
        ),
    )
}

@Composable
internal fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    actual: Boolean,
    predicted: Boolean,
    fertileEstimate: Boolean,
    modifier: Modifier = Modifier,
    actionDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val locale = LocalLocale.current.platformLocale
    val formattedDate = remember(date, locale) {
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))
    }
    val todayStatus = stringResource(R.string.calendar_day_today_status)
    val recordedStatus = stringResource(R.string.calendar_day_recorded_status)
    val predictedStatus = stringResource(R.string.calendar_day_prediction_status)
    val fertilityStatus = stringResource(R.string.calendar_day_fertility_status)
    val statusDescriptions = listOfNotNull(
        todayStatus.takeIf { isToday },
        recordedStatus.takeIf { actual },
        predictedStatus.takeIf { predicted },
        fertilityStatus.takeIf { fertileEstimate },
    )
    val description = (listOf(formattedDate) + statusDescriptions).joinToString(". ")
    val kind = CalendarDayKind.resolve(actual, predicted, fertileEstimate)
    val calendarColors = LocalCalendarColors.current
    val colors = when (kind) {
        CalendarDayKind.RECORDED -> calendarColors.recorded
        CalendarDayKind.PREDICTED -> calendarColors.predicted
        CalendarDayKind.FERTILE -> calendarColors.fertile
        CalendarDayKind.NONE -> CalendarCategoryColors(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface)
    }
    val dashedPrediction = kind == CalendarDayKind.PREDICTED
    val cardModifier = modifier
        .padding(2.dp)
        .height((52 * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp)
        .semantics { contentDescription = listOfNotNull(description, actionDescription).joinToString(". ") }
        .then(
            if (onClick != null) {
                Modifier.clickable(role = Role.Button, onClick = onClick)
            } else {
                Modifier
            },
        )
        .then(
            if (dashedPrediction) {
                Modifier.calendarDashedOutline(colors.content)
            } else {
                Modifier
            },
        )
    Card(
        cardModifier,
        shape = RoundedCornerShape(10.dp),
        border = if (kind != CalendarDayKind.NONE && !dashedPrediction) BorderStroke(1.dp, colors.content) else null,
        colors = CardDefaults.cardColors(
            containerColor = colors.container,
            contentColor = colors.content,
        ),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                textDecoration = if (isToday) TextDecoration.Underline else TextDecoration.None,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
                softWrap = false,
            )
            kind.marker?.let { marker ->
                Text(
                    marker,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

internal fun periodForDate(
    periods: List<PeriodEntity>,
    date: LocalDate,
    today: LocalDate = LocalDate.now(),
): PeriodEntity? = periods
    .asSequence()
    .mapNotNull { period ->
        if (period.deletedAt != null) return@mapNotNull null
        val start = runCatching { LocalDate.parse(period.startDate) }.getOrNull() ?: return@mapNotNull null
        val end = period.endDate
            ?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }
            ?: maxOf(start, today)
        period.takeIf { !date.isBefore(start) && !date.isAfter(end) }
    }
    .maxByOrNull { it.startDate }

/** Constant-memory calendar range check, including deliberately open period records. */
internal fun calendarRangeContains(
    date: LocalDate,
    startDate: String?,
    endDate: String?,
    today: LocalDate = LocalDate.now(),
    extendOpenRange: Boolean = true,
): Boolean {
    val start = startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
    val end = endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: if (extendOpenRange) maxOf(start, today) else return false
    if (end.isBefore(start)) return false
    return !date.isBefore(start) && !date.isAfter(end)
}

@Composable
internal fun HistoryScreen(
    periods: List<PeriodEntity>,
    prediction: PredictionDto?,
    onPeriodUpdated: (String, LocalDate, LocalDate?) -> Unit,
    onPeriodDeleted: (String) -> Unit,
    periodFailureRevision: Long = 0L,
) {
    val starts = periods.mapNotNull { runCatching { LocalDate.parse(it.startDate) }.getOrNull() }.distinct().sorted()
    val lengths = starts.zipWithNext { first, second -> java.time.temporal.ChronoUnit.DAYS.between(first, second).toInt() }
    val average = lengths.takeIf { it.isNotEmpty() }?.average()?.toInt()
    val sorted = lengths.sorted()
    val median = sorted.takeIf { it.isNotEmpty() }?.let { values ->
        if (values.size % 2 == 1) values[values.size / 2] else (values[values.size / 2 - 1] + values[values.size / 2]) / 2
    }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { LunaraHeader(eyebrow = stringResource(R.string.journal_your_history), title = stringResource(R.string.cycle_insights), body = stringResource(R.string.recorded_cycles, lengths.size)) }
        if (lengths.isEmpty()) {
            item { InsightCard(Icons.AutoMirrored.Rounded.List, stringResource(R.string.history), stringResource(R.string.single_record_history)) }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(stringResource(R.string.average_cycle), average ?: 0, Modifier.weight(1f))
                    StatCard(stringResource(R.string.median_cycle), median ?: 0, Modifier.weight(1f))
                }
            }
            item { CycleLengthJournal(starts, lengths) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(stringResource(R.string.shortest_cycle), lengths.minOrNull() ?: 0, Modifier.weight(1f))
                    StatCard(stringResource(R.string.longest_cycle), lengths.maxOrNull() ?: 0, Modifier.weight(1f))
                }
            }
        }
        prediction?.takeIf { it.available }?.let { value ->
            item {
                InsightCard(
                    Icons.Rounded.Info,
                    stringResource(R.string.estimated_period),
                    formatRange(value.predictedStartFrom, value.predictedStartTo),
                    action = { Text(stringResource(R.string.prediction_basis, value.basedOnCycleCount, localizedConfidence(value.confidence))) },
                )
            }
        }
        items(periods, key = { it.id }) { period ->
            EditablePeriodRow(
                period = period,
                allPeriods = periods,
                onUpdate = onPeriodUpdated,
                onDelete = onPeriodDeleted,
                periodFailureRevision = periodFailureRevision,
            )
        }
    }
}

@Composable
private fun StatCard(title: String, days: Int, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.days_value, days), style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
internal fun SupportScreen(
    dashboard: PartnerDashboardDto?,
    checkInSent: Boolean,
    checkInError: Boolean,
    onCheckIn: (String) -> Unit,
    onNextTip: () -> Unit = {},
    refreshing: Boolean = false,
) {
    val checkInPrompt = stringResource(R.string.check_in)
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { LunaraHeader(title = stringResource(R.string.support), body = stringResource(R.string.support_principle)) }
        item {
            val tip = dashboard?.tip
            if (tip == null) {
                InsightCard(
                    Icons.Rounded.Info,
                    stringResource(R.string.partner_tip_title),
                    stringResource(R.string.no_partner_tip_available),
                )
            } else {
                PartnerActionCard(tip, onNextTip, refreshing, PredictionPresentationPolicy.phaseAwareTipPhase(dashboard) != null)
            }
        }
        item {
            FilledTonalButton(onClick = { onCheckIn(checkInPrompt) }, enabled = !checkInSent, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(Icons.Rounded.FavoriteBorder, contentDescription = null)
                Text(stringResource(R.string.check_in), Modifier.padding(start = 10.dp))
            }
        }
        if (checkInSent) item { Text(stringResource(R.string.check_in_sent), color = MaterialTheme.colorScheme.tertiary) }
        if (checkInError) item { Text(stringResource(R.string.check_in_error), color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun EditablePeriodRow(
    period: PeriodEntity,
    allPeriods: List<PeriodEntity>,
    onUpdate: (String, LocalDate, LocalDate?) -> Unit,
    onDelete: (String) -> Unit,
    periodFailureRevision: Long = 0L,
) {
    var showEditor by rememberSaveable(period.id) { mutableStateOf(false) }
    var pendingUpdatedStartDate by rememberSaveable(period.id) { mutableStateOf<String?>(null) }
    var pendingUpdatedEndDate by rememberSaveable(period.id) { mutableStateOf<String?>(null) }
    var pendingDelete by rememberSaveable(period.id) { mutableStateOf(false) }
    var pendingFailureRevision by rememberSaveable(period.id) { mutableStateOf<Long?>(null) }
    val submitting = pendingUpdatedStartDate != null || pendingDelete
    val editable = !SyncEditPolicy.isBlocked(period.syncState)
    LaunchedEffect(period.startDate, period.endDate, pendingUpdatedStartDate, pendingUpdatedEndDate) {
        val pendingStart = pendingUpdatedStartDate ?: return@LaunchedEffect
        if (
            period.startDate == pendingStart &&
            period.endDate == pendingUpdatedEndDate
        ) {
            pendingUpdatedStartDate = null
            pendingUpdatedEndDate = null
            pendingFailureRevision = null
            showEditor = false
        }
    }
    LaunchedEffect(periodFailureRevision, pendingFailureRevision) {
        val submittedAt = pendingFailureRevision ?: return@LaunchedEffect
        if (periodFailureRevision != submittedAt) {
            pendingUpdatedStartDate = null
            pendingUpdatedEndDate = null
            pendingDelete = false
            pendingFailureRevision = null
        }
    }
    Card(
        onClick = { showEditor = true },
        enabled = editable,
        modifier = Modifier.testTag("period-manage-${period.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    period.endDate?.let { end ->
                        stringResource(
                            R.string.period_date_range,
                            formatDate(period.startDate),
                            formatDate(end),
                        )
                    } ?: stringResource(R.string.period_date_open, formatDate(period.startDate)),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    when (period.syncState) {
                        "SYNCED" -> stringResource(R.string.synced)
                        "LOCAL" -> stringResource(R.string.stored_on_device)
                        "CONFLICT" -> stringResource(R.string.sync_conflict)
                        "RESOLVING" -> stringResource(R.string.sync_resolving)
                        else -> stringResource(R.string.pending)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.edit_period_start))
        }
    }
    if (showEditor) {
        PeriodEditorDialog(
            period = period,
            allPeriods = allPeriods,
            submitting = submitting,
            onUpdate = { start, end ->
                if (pendingUpdatedStartDate == null && !pendingDelete) {
                    pendingUpdatedStartDate = start.toString()
                    pendingUpdatedEndDate = end?.toString()
                    pendingFailureRevision = periodFailureRevision
                    onUpdate(period.id, start, end)
                }
            },
            onDelete = {
                if (pendingUpdatedStartDate == null && !pendingDelete) {
                    pendingDelete = true
                    pendingFailureRevision = periodFailureRevision
                    onDelete(period.id)
                }
            },
            onDismiss = {
                pendingUpdatedStartDate = null
                pendingUpdatedEndDate = null
                pendingDelete = false
                pendingFailureRevision = null
                showEditor = false
            },
        )
    }
}

@Composable
internal fun PeriodEditorDialog(
    period: PeriodEntity,
    allPeriods: List<PeriodEntity> = emptyList(),
    onUpdate: (LocalDate, LocalDate?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    submitting: Boolean = false,
) {
    var savedStartDate by rememberSaveable(period.id) { mutableStateOf<String?>(period.startDate) }
    var savedEndDate by rememberSaveable(period.id) { mutableStateOf<String?>(period.endDate) }
    var confirmDelete by rememberSaveable(period.id) { mutableStateOf(false) }
    val selection = remember(savedStartDate, savedEndDate) {
        PeriodRangeSelectionPolicy.restore(savedStartDate, savedEndDate)
    }
    val initialPickerMonth = remember(period.startDate) {
        runCatching { YearMonth.from(LocalDate.parse(period.startDate)) }.getOrDefault(YearMonth.now())
    }
    val overlap = remember(selection, allPeriods, period.id) {
        periodRangeOverlapsExisting(selection, allPeriods, excludedPeriodId = period.id)
    }
    val changed = selection.startDate?.let {
        it.toString() != period.startDate || selection.endDate?.toString() != period.endDate
    } == true

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { if (!submitting) confirmDelete = false },
            title = { Text(stringResource(R.string.delete_period_start)) },
            text = { Text(stringResource(R.string.delete_period_confirmation, formatDate(period.startDate))) },
            confirmButton = {
                TextButton(
                    onClick = onDelete,
                    enabled = !submitting,
                    modifier = Modifier.testTag("period-delete-confirm"),
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null)
                    Text(stringResource(R.string.delete), Modifier.padding(start = 6.dp))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDelete = false },
                    enabled = !submitting,
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.edit_period_dates)) },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.period_picker_edit_notice), color = MaterialTheme.colorScheme.onSurfaceVariant)
                PeriodRangePicker(
                    selection = selection,
                    initialMonth = initialPickerMonth,
                    enabled = !submitting,
                    onSelectionChanged = { updated ->
                        savedStartDate = updated.savedStartDate
                        savedEndDate = updated.savedEndDate
                    },
                )
                PeriodRangeSummary(selection = selection, overlap = overlap)
                TextButton(
                    onClick = {
                        savedStartDate = null
                        savedEndDate = null
                    },
                    enabled = !submitting,
                    modifier = Modifier.align(Alignment.End).testTag("period-range-reset"),
                ) { Text(stringResource(R.string.period_picker_reset)) }
                if (selection.endDate != null) {
                    TextButton(
                        onClick = { savedEndDate = null },
                        enabled = !submitting,
                        modifier = Modifier.align(Alignment.End).testTag("period-end-clear"),
                    ) { Text(stringResource(R.string.remove_period_end)) }
                }
                TextButton(
                    onClick = { confirmDelete = true },
                    enabled = !submitting,
                    modifier = Modifier.align(Alignment.End).testTag("period-delete"),
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null)
                    Text(stringResource(R.string.delete_period_start), Modifier.padding(start = 6.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selection.startDate?.let { onUpdate(it, selection.endDate) } },
                enabled = changed && !overlap && !submitting,
                modifier = Modifier.testTag("period-update-confirm"),
            ) {
                Text(
                    stringResource(
                        if (selection.endDate == null) {
                            R.string.period_save_open
                        } else {
                            R.string.period_save_range
                        },
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
internal fun PeriodCreateDialog(
    initialStartDate: LocalDate,
    allPeriods: List<PeriodEntity> = emptyList(),
    onCreate: (LocalDate, LocalDate?) -> Unit,
    onDismiss: () -> Unit,
    submitting: Boolean = false,
) {
    var savedStartDate by rememberSaveable(initialStartDate.toString()) {
        mutableStateOf<String?>(initialStartDate.toString())
    }
    var savedEndDate by rememberSaveable(initialStartDate.toString()) {
        mutableStateOf<String?>(null)
    }
    val selection = remember(savedStartDate, savedEndDate) {
        PeriodRangeSelectionPolicy.restore(savedStartDate, savedEndDate)
    }
    val overlap = remember(selection, allPeriods) {
        periodRangeOverlapsExisting(selection, allPeriods)
    }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.period_picker_title)) },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.period_picker_create_notice),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PeriodRangePicker(
                    selection = selection,
                    initialMonth = YearMonth.from(initialStartDate),
                    enabled = !submitting,
                    onSelectionChanged = { updated ->
                        savedStartDate = updated.savedStartDate
                        savedEndDate = updated.savedEndDate
                    },
                )
                PeriodRangeSummary(selection = selection, overlap = overlap)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selection.startDate?.let { onCreate(it, selection.endDate) } },
                enabled = selection.startDate != null && !overlap && !submitting,
                modifier = Modifier.testTag("period-create-confirm"),
            ) {
                Text(
                    stringResource(
                        if (selection.endDate == null) {
                            R.string.period_save_open
                        } else {
                            R.string.period_save_range
                        },
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

internal enum class PeriodRangeDayRole {
    NONE,
    START,
    MIDDLE,
    END,
    SINGLE,
}

internal fun periodRangeDayRole(
    selection: PeriodRangeSelection,
    date: LocalDate,
): PeriodRangeDayRole {
    val start = selection.startDate ?: return PeriodRangeDayRole.NONE
    val end = selection.endDate
    if (date == start && date == end) return PeriodRangeDayRole.SINGLE
    if (date == start) return PeriodRangeDayRole.START
    if (date == end) return PeriodRangeDayRole.END
    if (end != null && date.isAfter(start) && date.isBefore(end)) return PeriodRangeDayRole.MIDDLE
    return PeriodRangeDayRole.NONE
}

internal fun periodRangeOverlapsExisting(
    selection: PeriodRangeSelection,
    periods: List<PeriodEntity>,
    today: LocalDate = LocalDate.now(),
    excludedPeriodId: String? = null,
): Boolean {
    val selectedStart = selection.startDate ?: return false
    val selectedEnd = selection.endDate ?: maxOf(selectedStart, today)
    if (selectedEnd.isBefore(selectedStart)) return true

    return periods.any { period ->
        if (period.id == excludedPeriodId || period.deletedAt != null) return@any false
        val existingStart = runCatching { LocalDate.parse(period.startDate) }.getOrNull()
            ?: return@any false
        val existingEnd = period.endDate
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: maxOf(existingStart, today)
        !selectedEnd.isBefore(existingStart) && !existingEnd.isBefore(selectedStart)
    }
}

@Composable
private fun PeriodRangePicker(
    selection: PeriodRangeSelection,
    initialMonth: YearMonth,
    enabled: Boolean,
    onSelectionChanged: (PeriodRangeSelection) -> Unit,
) {
    val today = LocalDate.now()
    val currentMonth = YearMonth.from(today)
    val initialPage = remember(initialMonth, currentMonth) {
        CalendarMonthPagingPolicy.pageForMonth(initialMonth, currentMonth)
    }
    val pageCount = remember(currentMonth) { CalendarMonthPagingPolicy.pageCount(currentMonth) }
    val pagerState = rememberPagerState(initialPage = initialPage) { pageCount }
    val locale = LocalLocale.current.platformLocale

    Column(
        Modifier.fillMaxWidth().testTag("period-range-picker"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height((392 * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp)
                .testTag("period-range-month-pager"),
            userScrollEnabled = enabled,
            key = { page -> CalendarMonthPagingPolicy.monthForPage(page, currentMonth).toString() },
        ) { page ->
            val month = CalendarMonthPagingPolicy.monthForPage(page, currentMonth)
            val first = month.atDay(1)
            val leading = first.dayOfWeek.value - 1
            val populatedCells = List<LocalDate?>(leading) { null } +
                (1..month.lengthOfMonth()).map(month::atDay)
            val cells = populatedCells + List(42 - populatedCells.size) { null }

            Column(
                Modifier.fillMaxWidth().testTag("period-picker-month-$month"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(Modifier.fillMaxWidth()) {
                    java.time.DayOfWeek.entries.forEach { day ->
                        Text(
                            day.getDisplayName(java.time.format.TextStyle.NARROW, locale),
                            Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { date ->
                            if (date == null) {
                                Spacer(Modifier.weight(1f).height((48 * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp))
                            } else {
                                PeriodRangePickerDay(
                                    date = date,
                                    role = periodRangeDayRole(selection, date),
                                    enabled = enabled && !date.isAfter(today),
                                    onClick = {
                                        onSelectionChanged(
                                            PeriodRangeSelectionPolicy.select(selection, date),
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodRangePickerDay(
    date: LocalDate,
    role: PeriodRangeDayRole,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
    val formattedDate = remember(date, locale) {
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))
    }
    val status = stringResource(
        when {
            !enabled -> R.string.period_picker_day_unavailable
            role == PeriodRangeDayRole.START -> R.string.period_picker_day_start
            role == PeriodRangeDayRole.END -> R.string.period_picker_day_end
            role == PeriodRangeDayRole.SINGLE -> R.string.period_picker_day_single
            role == PeriodRangeDayRole.MIDDLE -> R.string.period_picker_day_middle
            else -> R.string.period_picker_day_available
        },
    )
    val selected = role != PeriodRangeDayRole.NONE
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .padding(2.dp)
            .height((48 * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp)
            .testTag("period-picker-day-$date")
            .semantics {
                contentDescription = "$formattedDate. $status"
                this.selected = selected
            },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            disabledContainerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            Modifier.fillMaxSize().padding(vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium)
            when (role) {
                PeriodRangeDayRole.START -> Text(
                    stringResource(R.string.period_picker_marker_start),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
                PeriodRangeDayRole.END -> Text(
                    stringResource(R.string.period_picker_marker_end),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
                PeriodRangeDayRole.SINGLE -> Text(
                    stringResource(R.string.period_picker_marker_single),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
                PeriodRangeDayRole.MIDDLE -> Text("•", style = MaterialTheme.typography.labelSmall)
                PeriodRangeDayRole.NONE -> Unit
            }
        }
    }
}

@Composable
private fun PeriodRangeSummary(
    selection: PeriodRangeSelection,
    overlap: Boolean,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .testTag("period-range-summary")
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(
            containerColor = if (overlap) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val start = selection.startDate
            val end = selection.endDate
            when {
                start == null -> Text(stringResource(R.string.period_picker_choose_start))
                end == null -> {
                    Text(
                        stringResource(R.string.period_picker_start_selected, formatDate(start.toString())),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(stringResource(R.string.period_picker_choose_end))
                }
                else -> {
                    val days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1
                    Text(
                        stringResource(
                            R.string.period_picker_selected_range,
                            formatDate(start.toString()),
                            formatDate(end.toString()),
                        ),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(
                            if (days == 1L) R.string.period_picker_one_day else R.string.period_picker_days,
                            days,
                        ),
                    )
                    Text(stringResource(R.string.period_picker_restart_hint))
                }
            }
            if (overlap) {
                Text(
                    stringResource(R.string.period_picker_overlap),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun PeriodTransferSettings(
    role: UserRole,
    state: PeriodTransferUiState,
    onBegin: () -> PeriodTransferIdentity?,
    onExport: (Uri, PeriodFileFormat, PeriodTransferIdentity) -> Unit,
    onPreviewImport: (Uri, PeriodTransferIdentity) -> Unit,
    onConfirmImport: () -> Unit,
    onClear: () -> Unit,
) {
    // Not saveable: after process death a picker result must be discarded, never rebound.
    var pendingExport by remember { mutableStateOf<Pair<PeriodTransferIdentity, PeriodFileFormat>?>(null) }
    var pendingImport by remember { mutableStateOf<PeriodTransferIdentity?>(null) }
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val pending = pendingExport
        pendingExport = null
        if (uri != null && pending?.second == PeriodFileFormat.CSV) onExport(uri, pending.second, pending.first)
    }
    val jsonPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val pending = pendingExport
        pendingExport = null
        if (uri != null && pending?.second == PeriodFileFormat.JSON) onExport(uri, pending.second, pending.first)
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val identity = pendingImport
        pendingImport = null
        if (uri != null && identity != null) onPreviewImport(uri, identity)
    }
    PeriodTransferControls(
        role = role, state = state,
        onExport = { format ->
            onBegin()?.let { identity ->
                pendingExport = identity to format
                val name = "lunara-periods-${LocalDate.now()}.${format.extension}"
                if (format == PeriodFileFormat.CSV) csvPicker.launch(name) else jsonPicker.launch(name)
            }
        },
        onImport = {
            onBegin()?.let { identity ->
                pendingImport = identity
                // Providers often label CSV/JSON as plain text or octet-stream. Validate content.
                importPicker.launch(arrayOf("*/*"))
            }
        },
        onConfirmImport = onConfirmImport, onClear = onClear,
    )
}

@Composable
internal fun PeriodTransferControls(
    role: UserRole,
    state: PeriodTransferUiState,
    onExport: (PeriodFileFormat) -> Unit,
    onImport: () -> Unit,
    onConfirmImport: () -> Unit,
    onClear: () -> Unit,
) {
    if (role != UserRole.TRACKER) return
    var requestedExport by remember { mutableStateOf<PeriodFileFormat?>(null) }
    var showImportInfo by remember { mutableStateOf(false) }
    val busy = state.status == PeriodTransferStatus.BUSY
    InsightCard(
        Icons.Rounded.DateRange,
        stringResource(R.string.period_transfer_title),
        stringResource(R.string.period_transfer_summary),
        action = {
            Column(Modifier.testTag("period-transfer-controls"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { requestedExport = PeriodFileFormat.CSV }, enabled = !busy, modifier = Modifier.testTag("period-export-csv").heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.period_export_csv))
                }
                TextButton(onClick = { requestedExport = PeriodFileFormat.JSON }, enabled = !busy, modifier = Modifier.testTag("period-export-json").heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.period_export_json))
                }
                TextButton(onClick = { showImportInfo = true }, enabled = !busy, modifier = Modifier.testTag("period-import-file").heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.period_import_file))
                }
                if (busy) CircularProgressIndicator(Modifier.size(24.dp))
                if (state.status !in setOf(PeriodTransferStatus.IDLE, PeriodTransferStatus.PREVIEW)) {
                    val message = when (state.status) {
                        PeriodTransferStatus.EXPORTED -> stringResource(R.string.period_export_success, state.completedCount)
                        PeriodTransferStatus.IMPORTED -> stringResource(R.string.period_import_success, state.completedCount)
                        PeriodTransferStatus.BUSY -> stringResource(R.string.period_transfer_busy)
                        else -> stringResource(when (state.problem) {
                            PeriodFileProblem.INVALID_FORMAT -> R.string.period_import_invalid_format
                            PeriodFileProblem.INVALID_DATE -> R.string.period_import_invalid_date
                            PeriodFileProblem.AMBIGUOUS_DATE -> R.string.period_import_ambiguous_date
                            PeriodFileProblem.TOO_LARGE -> R.string.period_import_too_large
                            PeriodFileProblem.TOO_MANY -> R.string.period_import_too_many
                            PeriodFileProblem.CHANGED -> R.string.period_import_changed
                            PeriodFileProblem.ACCOUNT_CHANGED -> R.string.period_transfer_account_changed
                            null -> R.string.period_transfer_failed
                        })
                    }
                    Text(message, modifier = Modifier.testTag("period-transfer-status").semantics { liveRegion = LiveRegionMode.Polite },
                        color = if (state.status == PeriodTransferStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                }
            }
        },
    )
    requestedExport?.let { format ->
        AlertDialog(
            onDismissRequest = { requestedExport = null },
            title = { Text(stringResource(R.string.period_export_warning_title)) },
            text = { Text(stringResource(R.string.period_export_warning), Modifier.testTag("period-export-warning")) },
            confirmButton = { TextButton(onClick = { requestedExport = null; onExport(format) }, modifier = Modifier.testTag("period-export-confirm")) { Text(stringResource(R.string.period_export_choose_destination)) } },
            dismissButton = { TextButton(onClick = { requestedExport = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showImportInfo) AlertDialog(
        onDismissRequest = { showImportInfo = false },
        title = { Text(stringResource(R.string.period_import_file)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.period_import_explanation))
            Text(stringResource(R.string.period_import_format_guide))
        } },
        confirmButton = { TextButton(onClick = { showImportInfo = false; onImport() }, modifier = Modifier.testTag("period-import-choose")) { Text(stringResource(R.string.period_import_choose)) } },
        dismissButton = { TextButton(onClick = { showImportInfo = false }) { Text(stringResource(R.string.cancel)) } },
    )
    state.summary?.takeIf { state.status == PeriodTransferStatus.PREVIEW || busy }?.let { summary ->
        AlertDialog(
            onDismissRequest = { if (!busy) onClear() },
            title = { Text(stringResource(R.string.period_import_preview_title)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()).testTag("period-import-preview"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.period_import_preview_counts, summary.newCount, summary.duplicateCount, summary.conflictCount))
                Text(stringResource(R.string.period_import_explanation))
                if (summary.conflictCount > 0) Text(stringResource(R.string.period_import_conflicts), color = MaterialTheme.colorScheme.error)
                else if (summary.newCount == 0) Text(stringResource(R.string.period_import_nothing_new))
                if (busy) CircularProgressIndicator(Modifier.size(24.dp))
            } },
            confirmButton = { TextButton(onClick = onConfirmImport, enabled = !busy && summary.canImport, modifier = Modifier.testTag("period-import-confirm")) { Text(stringResource(R.string.period_import_confirm)) } },
            dismissButton = { TextButton(onClick = onClear, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
internal fun SettingsScreen(
    session: AppSession,
    preferences: AppPreferencesState,
    onPrivacyMode: (Boolean) -> Unit,
    onUseSystemTheme: (Boolean) -> Unit,
    onDarkMode: (Boolean) -> Unit,
    onNotificationPreferences: (NotificationPreferences) -> Unit,
    onSync: () -> Unit,
    onRenewSession: () -> Unit,
    sharing: SharingSettingsDto?,
    cycleProfile: CycleProfileDto?,
    carePreferences: CarePreferencesDto?,
    invitation: de.lunara.app.data.remote.dto.InvitationDto?,
    relationship: de.lunara.app.data.repository.RelationshipState,
    settingsBusy: Boolean,
    settingsError: Boolean,
    passwordChangeStatus: PasswordChangeStatus,
    oidc: OidcLinkUiState,
    backupStatus: LocalBackupStatus,
    onCreateInvitation: () -> Unit,
    onAcceptInvitation: (String) -> Unit,
    onRefreshInvitation: () -> Unit,
    onRevokeInvitation: () -> Unit,
    onUpdateSharing: (UpdateSharingRequest) -> Unit,
    onUpdateCarePreferences: (UpdateCarePreferencesRequest) -> Unit,
    onFertilityPrediction: (Boolean) -> Unit,
    onChangePassword: (String, String, String) -> Unit,
    onClearPasswordChangeStatus: () -> Unit,
    onBeginAuthentikLink: ((Intent) -> Unit) -> Unit,
    onCompleteAuthentikLink: (Intent?, String) -> Unit,
    onClearAuthentikError: () -> Unit,
    onExportLocalBackup: (Uri, CharArray) -> Unit,
    onImportLocalBackup: (Uri, CharArray) -> Unit,
    onClearLocalBackupStatus: () -> Unit,
    onLogout: () -> Unit,
    partnerCalendarStatus: PartnerCalendarStatus? = null,
    onPartnerRefresh: () -> Unit = {},
    periodTransfer: PeriodTransferUiState = PeriodTransferUiState(),
    onBeginPeriodTransfer: () -> PeriodTransferIdentity? = { null },
    onExportPeriods: (Uri, PeriodFileFormat, PeriodTransferIdentity) -> Unit = { _, _, _ -> },
    onPreviewPeriodImport: (Uri, PeriodTransferIdentity) -> Unit = { _, _ -> },
    onConfirmPeriodImport: () -> Unit = {},
    onClearPeriodTransfer: () -> Unit = {},
) {
    val isLocalMode = preferences.appMode == AppMode.LOCAL
    var settingsSection by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    val settingsListState = rememberLazyListState()
    BackHandler(settingsSection != null) { settingsSection = null }
    LaunchedEffect(settingsSection) { settingsListState.scrollToItem(0) }
    var confirmLogout by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showAuthentikLinkDialog by remember { mutableStateOf(false) }
    // A current account password must never enter SavedInstanceState. If the
    // activity is recreated while the browser flow is open, linking fails
    // closed and the user can start it again.
    var pendingAuthentikPassword by remember { mutableStateOf("") }
    var showExportBackupDialog by remember { mutableStateOf(false) }
    var showImportBackupDialog by remember { mutableStateOf(false) }
    var showInvitationAcceptDialog by remember { mutableStateOf(false) }
    var invitationToAccept by remember { mutableStateOf("") }
    var invitationInputInvalid by remember { mutableStateOf(false) }
    var pendingExportPassphrase by remember { mutableStateOf("") }
    var pendingImportUri by remember { mutableStateOf<String?>(null) }
    var notificationPermissionDenied by remember { mutableStateOf(false) }
    val invitationScanPrompt = stringResource(R.string.scan_invitation_qr_prompt)
    val shareInvitationLabel = stringResource(R.string.share_invitation)
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationPermissionDenied = !granted
        if (granted) {
            onNotificationPreferences(preferences.notifications.copy(master = true))
        }
    }
    val authentikLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val password = pendingAuthentikPassword
        pendingAuthentikPassword = ""
        onCompleteAuthentikLink(result.data, password)
    }
    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val passphrase = pendingExportPassphrase.toCharArray()
        pendingExportPassphrase = ""
        if (uri == null) {
            passphrase.fill('\u0000')
        } else {
            onExportLocalBackup(uri, passphrase)
        }
    }
    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri.toString()
            showImportBackupDialog = true
        }
    }
    val invitationScanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        when {
            result.contents != null -> {
                parseInvitationQrPayload(result.contents, preferences.baseUrl)?.let {
                    invitationToAccept = it
                    invitationInputInvalid = false
                } ?: run { invitationInputInvalid = true }
            }
            result.originalIntent?.getBooleanExtra(Intents.Scan.MISSING_CAMERA_PERMISSION, false) == true -> {
                invitationInputInvalid = true
            }
        }
    }
    LaunchedEffect(session.userId) {
        if (session.role == UserRole.TRACKER && !isLocalMode) onRefreshInvitation()
    }
    LaunchedEffect(passwordChangeStatus) {
        if (passwordChangeStatus == PasswordChangeStatus.SUCCESS) {
            showPasswordDialog = false
            onClearPasswordChangeStatus()
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp).testTag("settings-screen"), state = settingsListState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            if (settingsSection != null) TextButton(onClick = { settingsSection = null }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                Text(stringResource(R.string.journal_settings_back), Modifier.padding(start = 8.dp))
            }
            LunaraHeader(
                title = stringResource(settingsSection?.title ?: R.string.settings),
                body = if (settingsSection == null) stringResource(R.string.journal_settings_intro) else null,
                modifier = Modifier.padding(vertical = 18.dp),
            )
        }
        if (settingsSection == null) {
            items(availableSettingsSections(session.role, isLocalMode), key = { it.name }) { section ->
                SettingsCategoryRow(section, when (section) {
                    SettingsSection.APPEARANCE -> Icons.Rounded.Settings
                    SettingsSection.CYCLE -> Icons.Rounded.DateRange
                    SettingsSection.PARTNER -> Icons.Rounded.FavoriteBorder
                    SettingsSection.SHARING -> Icons.Rounded.Lock
                    SettingsSection.CARE -> Icons.Rounded.Star
                    SettingsSection.NOTIFICATIONS -> Icons.Rounded.Notifications
                    SettingsSection.ACCOUNT -> Icons.Rounded.Lock
                    SettingsSection.ABOUT -> Icons.Rounded.Info
                }) { settingsSection = section }
            }
        }
        if (settingsSection == SettingsSection.ACCOUNT) {
        if (session.role == UserRole.TRACKER) item {
            PeriodTransferSettings(
                session.role, periodTransfer, onBeginPeriodTransfer, onExportPeriods,
                onPreviewPeriodImport, onConfirmPeriodImport, onClearPeriodTransfer,
            )
        }
        if (isLocalMode) {
            item { SettingHeader(stringResource(R.string.local_storage_title)) }
            item {
                SettingText(
                    Icons.Rounded.Lock,
                    stringResource(R.string.local_storage_title),
                    stringResource(R.string.local_storage_summary),
                )
            }
            item {
                InsightCard(
                    Icons.Rounded.Lock,
                    stringResource(R.string.portable_backup_title),
                    stringResource(R.string.portable_backup_summary),
                    action = {
                        Column(horizontalAlignment = Alignment.End) {
                            TextButton(
                                enabled = backupStatus != LocalBackupStatus.BUSY,
                                onClick = {
                                    onClearLocalBackupStatus()
                                    showExportBackupDialog = true
                                },
                            ) { Text(stringResource(R.string.backup_create)) }
                            TextButton(
                                enabled = backupStatus != LocalBackupStatus.BUSY,
                                onClick = {
                                    onClearLocalBackupStatus()
                                    importBackupLauncher.launch(arrayOf("application/octet-stream", "application/zip", "*/*"))
                                },
                            ) { Text(stringResource(R.string.backup_restore)) }
                        }
                    },
                )
            }
            if (backupStatus != LocalBackupStatus.IDLE) {
                item {
                    Text(
                        stringResource(
                            when (backupStatus) {
                                LocalBackupStatus.BUSY -> R.string.backup_busy
                                LocalBackupStatus.EXPORTED -> R.string.backup_export_success
                                LocalBackupStatus.IMPORTED -> R.string.backup_import_success
                                LocalBackupStatus.BAD_PASSPHRASE -> R.string.backup_bad_passphrase
                                LocalBackupStatus.INVALID_BACKUP -> R.string.backup_invalid
                                LocalBackupStatus.FAILED -> R.string.backup_failed
                                LocalBackupStatus.IDLE -> R.string.backup_busy
                            },
                        ),
                        color = when (backupStatus) {
                            LocalBackupStatus.BAD_PASSPHRASE,
                            LocalBackupStatus.INVALID_BACKUP,
                            LocalBackupStatus.FAILED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.tertiary
                        },
                    )
                }
            }
        } else {
            item { SettingHeader(stringResource(R.string.server_information)) }
            item { SettingText(Icons.Rounded.Info, session.serverName, stringResource(R.string.server_version_explicit, ConnectionPresentation.version(session.serverVersion))) }
            item { SettingHeader(stringResource(R.string.account_security)) }
            item {
                SettingText(
                    Icons.Rounded.Lock,
                    stringResource(R.string.renew_login),
                    stringResource(R.string.session_recovery_preserves_data),
                    onRenewSession,
                )
            }
        }
        if (!isLocalMode && oidc.status?.enabled == true) {
            item {
                if (oidc.status.linked) {
                    SettingText(
                        Icons.Rounded.Lock,
                        stringResource(R.string.authentik_connected, oidc.status.providerName ?: "Authentik"),
                        stringResource(
                            if (oidc.status.passwordLoginEnabled) {
                                R.string.authentik_connected_password_available
                            } else {
                                R.string.authentik_connected_only
                            },
                        ),
                    )
                } else {
                    SettingText(
                        Icons.Rounded.Lock,
                        stringResource(R.string.connect_authentik, oidc.status.providerName ?: "Authentik"),
                        stringResource(R.string.connect_authentik_summary),
                    ) {
                        onClearAuthentikError()
                        showAuthentikLinkDialog = true
                    }
                }
            }
        }
        if (!isLocalMode && oidc.status?.passwordLoginEnabled != false) {
            item {
                SettingText(
                    Icons.Rounded.Lock,
                    stringResource(R.string.change_password),
                    stringResource(R.string.change_password_summary),
                ) {
                    onClearPasswordChangeStatus()
                    showPasswordDialog = true
                }
            }
        }
        if (!isLocalMode && oidc.error) {
            item { Text(stringResource(R.string.authentik_link_failed), color = MaterialTheme.colorScheme.error) }
        }
        if (!isLocalMode) {
            item { SettingText(Icons.Rounded.Refresh, stringResource(R.string.sync_now), null, onSync) }
        }
        }
        if (settingsSection == SettingsSection.APPEARANCE) {
        item { SettingHeader(stringResource(R.string.appearance)) }
        item { SettingSwitch(Icons.Rounded.Settings, stringResource(R.string.system_theme), null, preferences.useSystemTheme, onUseSystemTheme) }
        item { SettingSwitch(Icons.Rounded.Settings, stringResource(R.string.dark_mode), null, preferences.darkMode, onDarkMode, !preferences.useSystemTheme) }
        item { SettingHeader(stringResource(R.string.privacy_mode)) }
        item {
            SettingSwitch(
                Icons.Rounded.Lock,
                stringResource(R.string.privacy_mode),
                stringResource(R.string.privacy_mode_summary),
                preferences.privacyMode,
                onPrivacyMode,
            )
        }
        }
        if (settingsSection == SettingsSection.NOTIFICATIONS) {
        item { SettingHeader(stringResource(R.string.notifications)) }
        item {
            val hasRuntimePermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            val systemAllowsNotifications = NotificationManagerCompat.from(context).areNotificationsEnabled()
            SettingSwitch(
                Icons.Rounded.Notifications,
                stringResource(R.string.notifications_master),
                stringResource(R.string.notifications_master_summary),
                preferences.notifications.master,
                { requested ->
                    notificationPermissionDenied = false
                    when {
                        !requested -> onNotificationPreferences(preferences.notifications.copy(master = false))
                        !hasRuntimePermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        !systemAllowsNotifications -> notificationPermissionDenied = true
                        else -> onNotificationPreferences(preferences.notifications.copy(master = true))
                    }
                },
                modifier = Modifier.testTag("notifications-master"),
            )
        }
        if (
            notificationPermissionDenied ||
            (preferences.notifications.master && !NotificationManagerCompat.from(context).areNotificationsEnabled())
        ) {
            item {
                Text(
                    stringResource(R.string.notifications_permission_denied),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            item {
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    },
                ) { Text(stringResource(R.string.notifications_open_system_settings)) }
            }
        }
        item {
            NotificationControls(
                role = session.role,
                preferences = preferences.notifications,
                fertilityPredictionEnabled = cycleProfile?.fertilityPredictionEnabled == true,
                onUpdate = onNotificationPreferences,
            )
        }
        }
        if (settingsSection == SettingsSection.CYCLE && session.role == UserRole.TRACKER) {
            item { SettingHeader(stringResource(R.string.fertility_predictions)) }
            item {
                FertilityPredictionControl(
                    profile = cycleProfile,
                    busy = settingsBusy,
                    onEnabled = onFertilityPrediction,
                )
            }
        }
        if (settingsSection == SettingsSection.ABOUT) {
            item { SettingText(Icons.Rounded.Info, stringResource(R.string.app_information), stringResource(R.string.app_version_explicit, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)) }
            item { DonationButton() }
        }
        if (!isLocalMode && settingsSection == SettingsSection.PARTNER) {
            item { RelationshipCard(relationship, onRefreshInvitation) }
            if (session.role == UserRole.PARTNER) item {
                PartnerCalendarAccessSettings(
                    partnerCalendarStatus ?: CalendarStatusPolicy.partner(null, unavailable = true, refreshing = false),
                    onPartnerRefresh,
                )
            }
        }
        if (session.role == UserRole.TRACKER && !isLocalMode && settingsSection == SettingsSection.PARTNER) {
            item { SettingHeader(stringResource(R.string.partner_connect)) }
            item {
                InsightCard(
                    Icons.Rounded.FavoriteBorder,
                    stringResource(R.string.partner_connect),
                    stringResource(R.string.partner_connect_summary),
                    action = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (relationship.connected) {
                                Text(
                                    stringResource(R.string.partner_connected),
                                    fontWeight = FontWeight.Bold,
                                )
                            } else if (relationship.known && !relationship.unavailable) {
                                OutlinedButton(
                                    onClick = {
                                        invitationToAccept = ""
                                        invitationInputInvalid = false
                                        showInvitationAcceptDialog = true
                                    },
                                    enabled = !settingsBusy,
                                ) { Text(stringResource(R.string.accept_partner_invitation)) }
                            }
                            invitation?.let {
                                it.code?.let { code ->
                                    SelectionContainer {
                                        Text(stringResource(R.string.invitation_code_value, code), fontWeight = FontWeight.Bold)
                                    }
                                } ?: Text(stringResource(R.string.invitation_active_after_restart), fontWeight = FontWeight.Bold)
                                Text(
                                    it.expiresAt?.let { expiry ->
                                        stringResource(R.string.invitation_expiry, expiry.take(16).replace('T', ' '))
                                    } ?: stringResource(R.string.invitation_no_expiry),
                                )
                                it.code?.let { code ->
                                    val link = it.acceptanceUrl
                                        ?.takeIf { url ->
                                            parseInvitationQrPayload(url, preferences.baseUrl) == code
                                        }
                                        ?: canonicalInvitationLink(preferences.baseUrl, code)
                                    QrCode(link ?: code, stringResource(R.string.invitation_qr_description, code))
                                    if (link != null) {
                                        TextButton(
                                            onClick = {
                                                context.startActivity(
                                                    Intent.createChooser(
                                                        Intent(Intent.ACTION_SEND)
                                                            .setType("text/plain")
                                                            .putExtra(Intent.EXTRA_TEXT, link),
                                                        shareInvitationLabel,
                                                    ),
                                                )
                                            },
                                            enabled = !settingsBusy,
                                        ) { Text(stringResource(R.string.share_invitation)) }
                                    }
                                }
                                TextButton(
                                    onClick = onRevokeInvitation,
                                    enabled = !settingsBusy,
                                    modifier = Modifier.align(Alignment.End),
                                ) {
                                    Text(stringResource(R.string.revoke_invitation))
                                }
                            }
                            Button(
                                onClick = onCreateInvitation,
                                enabled = !settingsBusy && invitation == null && relationship.known && !relationship.connected && !relationship.unavailable,
                            ) {
                                Text(stringResource(R.string.create_invitation))
                            }
                            TextButton(onClick = onRefreshInvitation, enabled = !settingsBusy) {
                                Text(stringResource(R.string.refresh_invitation))
                            }
                        }
                    },
                )
            }
        }
        if (session.role == UserRole.TRACKER && !isLocalMode && settingsSection == SettingsSection.SHARING) {
            item { SettingHeader(stringResource(R.string.sharing_settings)) }
            item { Text(stringResource(R.string.sharing_preview_default), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            sharing?.let { current ->
                item {
                    SharingControls(
                        current = current,
                        fertilityPredictionEnabled = cycleProfile?.fertilityPredictionEnabled == true,
                        onUpdate = onUpdateSharing,
                    )
                }
            }
        }
        if (session.role == UserRole.TRACKER && !isLocalMode && settingsSection == SettingsSection.CARE) {
            item { SettingHeader(stringResource(R.string.care_preferences)) }
            item { PersonalTipsPanel(session.userId, preferences.baseUrl) }
            item {
                Text(
                    stringResource(R.string.care_preferences_summary),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            carePreferences?.let { current ->
                item {
                    CarePreferencesControls(
                        current = current,
                        busy = settingsBusy,
                        onUpdate = onUpdateCarePreferences,
                    )
                }
            }
        }
        if (settingsError && settingsSection != null) item { Text(stringResource(R.string.settings_save_error), color = MaterialTheme.colorScheme.error) }
        if (settingsSection == SettingsSection.ACCOUNT) {
        item { HorizontalDivider(Modifier.padding(vertical = 10.dp)) }
        item {
            SettingText(
                Icons.AutoMirrored.Rounded.ExitToApp,
                stringResource(if (isLocalMode) R.string.local_delete_and_switch else R.string.logout),
                if (isLocalMode) stringResource(R.string.local_delete_and_switch_summary) else null,
            ) { confirmLogout = true }
        }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(if (isLocalMode) R.string.local_delete_title else R.string.logout)) },
            text = {
                Text(
                    stringResource(
                        if (isLocalMode) R.string.local_delete_confirmation else R.string.logout_confirmation,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onLogout) {
                    Text(stringResource(if (isLocalMode) R.string.local_delete_confirm else R.string.logout))
                }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.back_label)) } },
        )
    }
    if (showInvitationAcceptDialog) {
        AlertDialog(
            onDismissRequest = { if (!settingsBusy) showInvitationAcceptDialog = false },
            title = { Text(stringResource(R.string.accept_partner_invitation)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.accept_partner_invitation_warning),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = invitationToAccept,
                        onValueChange = {
                            invitationToAccept = it.take(512)
                            invitationInputInvalid = false
                        },
                        label = { Text(stringResource(R.string.invitation_code)) },
                        singleLine = true,
                        isError = invitationInputInvalid,
                        enabled = !settingsBusy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = {
                            invitationScanner.launch(
                                lunaraQrScanOptions(invitationScanPrompt),
                            )
                        },
                        enabled = !settingsBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.scan_invitation_qr)) }
                    if (invitationInputInvalid) {
                        Text(stringResource(R.string.error_invitation_code), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !settingsBusy,
                    onClick = {
                        val code = parseInvitationQrPayload(invitationToAccept, preferences.baseUrl)
                        if (code == null) {
                            invitationInputInvalid = true
                        } else {
                            showInvitationAcceptDialog = false
                            onAcceptInvitation(code)
                        }
                    },
                ) { Text(stringResource(R.string.connect_with_invitation)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showInvitationAcceptDialog = false },
                    enabled = !settingsBusy,
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (showPasswordDialog) {
        PasswordChangeDialog(
            status = passwordChangeStatus,
            onSubmit = onChangePassword,
            onInputChanged = onClearPasswordChangeStatus,
            onDismiss = {
                if (passwordChangeStatus != PasswordChangeStatus.BUSY) {
                    showPasswordDialog = false
                    onClearPasswordChangeStatus()
                }
            },
        )
    }
    if (showAuthentikLinkDialog) {
        AuthentikLinkDialog(
            providerName = oidc.status?.providerName ?: "Authentik",
            busy = oidc.busy,
            failed = oidc.error,
            onDismiss = {
                if (!oidc.busy) {
                    showAuthentikLinkDialog = false
                    pendingAuthentikPassword = ""
                    onClearAuthentikError()
                }
            },
            onSubmit = { password ->
                pendingAuthentikPassword = password
                showAuthentikLinkDialog = false
                onBeginAuthentikLink { authentikLauncher.launch(it) }
            },
        )
    }
    if (showExportBackupDialog) {
        BackupPassphraseDialog(
            title = stringResource(R.string.backup_create),
            explanation = stringResource(R.string.backup_export_passphrase_notice),
            requireConfirmation = true,
            onDismiss = { showExportBackupDialog = false },
            onSubmit = { passphrase ->
                showExportBackupDialog = false
                pendingExportPassphrase = passphrase
                exportBackupLauncher.launch("lunara-backup-${LocalDate.now()}.lunara")
            },
        )
    }
    if (showImportBackupDialog) {
        BackupPassphraseDialog(
            title = stringResource(R.string.backup_restore),
            explanation = stringResource(R.string.backup_import_warning),
            requireConfirmation = false,
            onDismiss = {
                showImportBackupDialog = false
                pendingImportUri = null
            },
            onSubmit = { passphrase ->
                val uri = pendingImportUri?.let(Uri::parse)
                showImportBackupDialog = false
                pendingImportUri = null
                if (uri == null) {
                    passphrase.toCharArray().fill('\u0000')
                } else {
                    onImportLocalBackup(uri, passphrase.toCharArray())
                }
            },
        )
    }
}

@Composable
private fun BackupPassphraseDialog(
    title: String,
    explanation: String,
    requireConfirmation: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var showValidation by remember { mutableStateOf(false) }
    val validLength = de.lunara.app.data.backup.PortableBackupPassphrasePolicy.isValid(passphrase)
    val matches = !requireConfirmation || passphrase == confirmation
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(explanation, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = {
                        passphrase = it.take(de.lunara.app.data.backup.PortableBackupPassphrasePolicy.MAX_UTF16_CODE_UNITS)
                        showValidation = false
                    },
                    label = { Text(stringResource(R.string.backup_passphrase)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = showValidation && !validLength,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (requireConfirmation) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = {
                            confirmation = it.take(de.lunara.app.data.backup.PortableBackupPassphrasePolicy.MAX_UTF16_CODE_UNITS)
                            showValidation = false
                        },
                        label = { Text(stringResource(R.string.backup_passphrase_confirm)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = showValidation && !matches,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (showValidation && !validLength) {
                    Text(stringResource(R.string.backup_passphrase_length), color = MaterialTheme.colorScheme.error)
                } else if (showValidation && !matches) {
                    Text(stringResource(R.string.backup_passphrase_mismatch), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (validLength && matches) {
                        val submitted = passphrase
                        passphrase = ""
                        confirmation = ""
                        onSubmit(submitted)
                    } else {
                        showValidation = true
                    }
                },
            ) { Text(stringResource(R.string.continue_label)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun AuthentikLinkDialog(
    providerName: String,
    busy: Boolean,
    failed: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var currentPassword by remember { mutableStateOf("") }
    var passwordMissing by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.connect_authentik, providerName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.connect_authentik_confirmation),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = {
                        currentPassword = limitAccountPasswordInput(it)
                        passwordMissing = false
                    },
                    label = { Text(stringResource(R.string.current_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    enabled = !busy,
                    isError = passwordMissing,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (passwordMissing) Text(stringResource(R.string.password_current_required), color = MaterialTheme.colorScheme.error)
                if (failed) Text(stringResource(R.string.authentik_link_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    if (currentPassword.isBlank()) passwordMissing = true else onSubmit(currentPassword)
                },
            ) { Text(stringResource(R.string.continue_label)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun PasswordChangeDialog(
    status: PasswordChangeStatus,
    onSubmit: (String, String, String) -> Unit,
    onInputChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val busy = status == PasswordChangeStatus.BUSY
    val error = when (status) {
        PasswordChangeStatus.CURRENT_REQUIRED -> R.string.password_current_required
        PasswordChangeStatus.NEW_LENGTH -> R.string.password_new_length
        PasswordChangeStatus.SAME_PASSWORD -> R.string.password_must_differ
        PasswordChangeStatus.CONFIRMATION_MISMATCH -> R.string.password_confirmation_mismatch
        PasswordChangeStatus.CURRENT_REJECTED -> R.string.password_current_rejected
        PasswordChangeStatus.FAILED -> R.string.password_change_failed
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.change_password)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.change_password_notice), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = {
                        currentPassword = limitAccountPasswordInput(it)
                        onInputChanged()
                    },
                    label = { Text(stringResource(R.string.current_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    enabled = !busy,
                    isError = status == PasswordChangeStatus.CURRENT_REQUIRED || status == PasswordChangeStatus.CURRENT_REJECTED,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = limitAccountPasswordInput(it)
                        onInputChanged()
                    },
                    label = { Text(stringResource(R.string.new_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    enabled = !busy,
                    isError = status == PasswordChangeStatus.NEW_LENGTH || status == PasswordChangeStatus.SAME_PASSWORD,
                    supportingText = { Text(stringResource(R.string.password_length_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = {
                        confirmation = limitAccountPasswordInput(it)
                        onInputChanged()
                    },
                    label = { Text(stringResource(R.string.confirm_new_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    enabled = !busy,
                    isError = status == PasswordChangeStatus.CONFIRMATION_MISMATCH,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(currentPassword, newPassword, confirmation) },
                enabled = !busy,
                modifier = Modifier.testTag("password-change-submit"),
            ) {
                Text(stringResource(if (busy) R.string.password_changing else R.string.change_password))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun FertilityPredictionControl(
    profile: CycleProfileDto?,
    busy: Boolean,
    onEnabled: (Boolean) -> Unit,
) {
    var confirmEnable by remember { mutableStateOf(false) }
    val enabled = profile?.fertilityPredictionEnabled == true
    SettingSwitch(
        icon = Icons.Rounded.Info,
        title = stringResource(R.string.fertility_prediction_enabled),
        subtitle = stringResource(R.string.fertility_prediction_summary),
        checked = enabled,
        onChecked = { requested ->
            if (requested) confirmEnable = true else onEnabled(false)
        },
        enabled = profile != null && !busy,
    )
    FertilitySafetyNotice()
    if (confirmEnable) {
        AlertDialog(
            onDismissRequest = { confirmEnable = false },
            title = { Text(stringResource(R.string.enable_fertility_prediction_title)) },
            text = { Text(stringResource(R.string.fertility_enable_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmEnable = false
                        onEnabled(true)
                    },
                    modifier = Modifier.testTag("fertility-enable-confirm"),
                ) { Text(stringResource(R.string.enable_estimate)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmEnable = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NotificationControls(
    role: UserRole,
    preferences: NotificationPreferences,
    fertilityPredictionEnabled: Boolean,
    onUpdate: (NotificationPreferences) -> Unit,
) {
    val context = LocalContext.current
    val reminderTime = formatClock(preferences.reminderHour, preferences.reminderMinute)
    val quietStart = formatClock(preferences.quietStartHour, preferences.quietStartMinute)
    val quietEnd = formatClock(preferences.quietEndHour, preferences.quietEndMinute)

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SettingSwitch(
            Icons.Rounded.DateRange,
            stringResource(R.string.notification_period_tomorrow),
            stringResource(R.string.notification_period_tomorrow_summary),
            preferences.periodTomorrow,
            { onUpdate(preferences.copy(periodTomorrow = it)) },
            modifier = Modifier.testTag("notification-period-tomorrow"),
        )
        SettingSwitch(
            Icons.Rounded.Info,
            stringResource(R.string.notification_fertile_tomorrow),
            stringResource(
                if (role == UserRole.TRACKER && !fertilityPredictionEnabled) {
                    R.string.notification_fertility_disabled_hint
                } else {
                    R.string.notification_fertile_tomorrow_summary
                },
            ),
            preferences.fertileTomorrow,
            { onUpdate(preferences.copy(fertileTomorrow = it)) },
            enabled = role == UserRole.PARTNER || fertilityPredictionEnabled || preferences.fertileTomorrow,
            modifier = Modifier.testTag("notification-fertile-tomorrow"),
        )
        if (role == UserRole.TRACKER) {
            SettingSwitch(
                Icons.AutoMirrored.Rounded.List,
                stringResource(R.string.notification_daily_log),
                stringResource(R.string.notification_daily_log_summary),
                preferences.dailyLogReminder,
                { onUpdate(preferences.copy(dailyLogReminder = it)) },
                modifier = Modifier.testTag("notification-daily-log"),
            )
        } else {
            SettingSwitch(
                Icons.Rounded.FavoriteBorder,
                stringResource(R.string.notification_partner_tip),
                stringResource(R.string.notification_partner_tip_summary),
                preferences.partnerTip,
                { onUpdate(preferences.copy(partnerTip = it)) },
                modifier = Modifier.testTag("notification-partner-tip"),
            )
        }
        SettingText(
            Icons.Rounded.Notifications,
            stringResource(R.string.notification_time),
            stringResource(R.string.notification_time_summary, reminderTime),
        ) {
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    onUpdate(preferences.copy(reminderHour = hour, reminderMinute = minute))
                },
                preferences.reminderHour,
                preferences.reminderMinute,
                true,
            ).show()
        }
        Text(
            stringResource(R.string.notification_quiet_summary, quietStart, quietEnd),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        SettingText(
            Icons.Rounded.Lock,
            stringResource(R.string.notification_quiet_start),
            quietStart,
        ) {
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    onUpdate(preferences.copy(quietStartHour = hour, quietStartMinute = minute))
                },
                preferences.quietStartHour,
                preferences.quietStartMinute,
                true,
            ).show()
        }
        SettingText(
            Icons.Rounded.Lock,
            stringResource(R.string.notification_quiet_end),
            quietEnd,
        ) {
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    onUpdate(preferences.copy(quietEndHour = hour, quietEndMinute = minute))
                },
                preferences.quietEndHour,
                preferences.quietEndMinute,
                true,
            ).show()
        }
        Text(
            stringResource(R.string.notification_max_per_day),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 10.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            (1..3).forEach { limit ->
                FilterChip(
                    selected = preferences.maxPerDay == limit,
                    onClick = { onUpdate(preferences.copy(maxPerDay = limit)) },
                    label = { Text(stringResource(R.string.notification_max_per_day_value, limit)) },
                )
            }
        }
        SettingSwitch(
            Icons.Rounded.Lock,
            stringResource(R.string.notification_hide_sensitive),
            stringResource(R.string.notification_hide_sensitive_summary),
            preferences.hideSensitiveContent,
            { onUpdate(preferences.copy(hideSensitiveContent = it)) },
        )
        Text(
            stringResource(R.string.notification_timing_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

private fun formatClock(hour: Int, minute: Int): String =
    String.format(Locale.ROOT, "%02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59))

@Composable
private fun SharingControls(
    current: SharingSettingsDto,
    fertilityPredictionEnabled: Boolean,
    onUpdate: (UpdateSharingRequest) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SettingSwitch(Icons.Rounded.DateRange, stringResource(R.string.sharing_prediction), null, current.sharePrediction, { onUpdate(UpdateSharingRequest(sharePrediction = it)) })
        SettingSwitch(Icons.Rounded.DateRange, stringResource(R.string.sharing_period_dates), null, current.sharePeriodDates, { onUpdate(UpdateSharingRequest(sharePeriodDates = it)) })
        SettingSwitch(Icons.Rounded.Info, stringResource(R.string.sharing_phase), null, current.sharePhase, { onUpdate(UpdateSharingRequest(sharePhase = it)) })
        SettingSwitch(Icons.AutoMirrored.Rounded.List, stringResource(R.string.sharing_flow), null, current.shareFlow, { onUpdate(UpdateSharingRequest(shareFlow = it)) })
        SettingSwitch(Icons.Rounded.FavoriteBorder, stringResource(R.string.sharing_pain), null, current.sharePain, { onUpdate(UpdateSharingRequest(sharePain = it)) })
        SettingSwitch(Icons.Rounded.FavoriteBorder, stringResource(R.string.sharing_mood), null, current.shareMood, { onUpdate(UpdateSharingRequest(shareMood = it)) })
        SettingSwitch(Icons.Rounded.Star, stringResource(R.string.sharing_energy), null, current.shareEnergy, { onUpdate(UpdateSharingRequest(shareEnergy = it)) })
        SettingSwitch(Icons.Rounded.Info, stringResource(R.string.sharing_sleep), null, current.shareSleep, { onUpdate(UpdateSharingRequest(shareSleep = it)) })
        SettingSwitch(Icons.AutoMirrored.Rounded.List, stringResource(R.string.sharing_symptoms), null, current.shareSymptoms, { onUpdate(UpdateSharingRequest(shareSymptoms = it)) })
        SettingSwitch(Icons.AutoMirrored.Rounded.List, stringResource(R.string.sharing_notes), null, current.shareNotes, { onUpdate(UpdateSharingRequest(shareNotes = it)) })
        SettingSwitch(
            Icons.Rounded.Info,
            stringResource(R.string.sharing_fertility),
            stringResource(
                if (fertilityPredictionEnabled) R.string.fertility_safety_notice
                else R.string.enable_fertility_before_sharing,
            ),
            current.shareFertileWindow,
            { requested ->
                if (
                    FertilitySharingPolicy.mayEnableSharing(
                        fertilityPredictionEnabled,
                        current.shareFertileWindow,
                        requested,
                    )
                ) {
                    onUpdate(UpdateSharingRequest(shareFertileWindow = requested))
                }
            },
            enabled = fertilityPredictionEnabled || current.shareFertileWindow,
        )
        SettingSwitch(Icons.Rounded.Star, stringResource(R.string.partner_tips_enabled), null, current.tipsEnabled, { onUpdate(UpdateSharingRequest(tipsEnabled = it)) })
        SettingSwitch(
            Icons.Rounded.Info,
            stringResource(R.string.symptom_based_tips),
            stringResource(if (current.shareSymptoms) R.string.symptom_tips_explanation else R.string.share_symptoms_before_tips),
            current.allowSymptomBasedTips,
            { requested -> if (!requested || current.shareSymptoms) onUpdate(UpdateSharingRequest(allowSymptomBasedTips = requested)) },
            enabled = current.shareSymptoms || current.allowSymptomBasedTips,
        )
        SettingSwitch(Icons.Rounded.FavoriteBorder, stringResource(R.string.check_ins_enabled), null, current.checkInsEnabled, { onUpdate(UpdateSharingRequest(checkInsEnabled = it)) })
        SettingSwitch(Icons.Rounded.Lock, stringResource(R.string.sharing_pause), null, current.sharingPaused, { onUpdate(UpdateSharingRequest(sharingPaused = it)) })
    }
}

@Composable
private fun CarePreferencesControls(
    current: CarePreferencesDto,
    busy: Boolean,
    onUpdate: (UpdateCarePreferencesRequest) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SettingSwitch(
            Icons.Rounded.FavoriteBorder,
            stringResource(R.string.care_warmth),
            stringResource(R.string.care_warmth_summary),
            current.likesWarmth,
            { onUpdate(CarePreferencesPatchPolicy.request(CarePreferenceOption.WARMTH, it)) },
            enabled = !busy,
        )
        SettingSwitch(
            Icons.Rounded.FavoriteBorder,
            stringResource(R.string.care_cuddling),
            stringResource(R.string.care_cuddling_summary),
            current.likesCuddling,
            { onUpdate(CarePreferencesPatchPolicy.request(CarePreferenceOption.CUDDLING, it)) },
            enabled = !busy,
        )
        SettingSwitch(
            Icons.Rounded.Lock,
            stringResource(R.string.care_space),
            stringResource(R.string.care_space_summary),
            current.likesSpace,
            { onUpdate(CarePreferencesPatchPolicy.request(CarePreferenceOption.SPACE, it)) },
            enabled = !busy,
        )
        SettingSwitch(
            Icons.Rounded.Star,
            stringResource(R.string.care_snacks),
            stringResource(R.string.care_snacks_summary),
            current.likesSnacks,
            { onUpdate(CarePreferencesPatchPolicy.request(CarePreferenceOption.SNACKS, it)) },
            enabled = !busy,
        )
        SettingSwitch(
            Icons.Rounded.Info,
            stringResource(R.string.care_conversation),
            stringResource(R.string.care_conversation_summary),
            current.likesConversation,
            { onUpdate(CarePreferencesPatchPolicy.request(CarePreferenceOption.CONVERSATION, it)) },
            enabled = !busy,
        )
        SettingSwitch(
            Icons.Rounded.Home,
            stringResource(R.string.care_household_help),
            stringResource(R.string.care_household_help_summary),
            current.likesHouseholdHelp,
            { onUpdate(CarePreferencesPatchPolicy.request(CarePreferenceOption.HOUSEHOLD_HELP, it)) },
            enabled = !busy,
        )
        SettingSwitch(
            Icons.Rounded.Refresh,
            stringResource(R.string.care_reminders),
            stringResource(R.string.care_reminders_summary),
            current.likesReminders,
            { onUpdate(CarePreferencesPatchPolicy.request(CarePreferenceOption.REMINDERS, it)) },
            enabled = !busy,
        )
        SettingSwitch(
            Icons.Rounded.Lock,
            stringResource(R.string.care_no_unsolicited_advice),
            stringResource(R.string.care_no_unsolicited_advice_summary),
            current.dislikesUnsolicitedAdvice,
            { onUpdate(CarePreferencesPatchPolicy.request(CarePreferenceOption.NO_UNSOLICITED_ADVICE, it)) },
            enabled = !busy,
        )
    }
}

@Composable
private fun SettingHeader(text: String) = Text(
    text,
    style = MaterialTheme.typography.titleMedium,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
)

@Composable
private fun SettingText(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String?, onClick: (() -> Unit)? = null) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (onClick != null) Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingSwitch(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChecked)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Column(Modifier.padding(horizontal = 14.dp).weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun syncDescription(pending: Int, conflicts: Int, lastSync: String?): String = when {
    conflicts > 0 -> stringResource(R.string.sync_conflicts, conflicts)
    pending > 0 -> stringResource(R.string.sync_pending, pending)
    lastSync == null -> stringResource(R.string.last_sync_never)
    else -> {
        val locale = LocalLocale.current.platformLocale
        val timestamp = runCatching {
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale)
                .withZone(ZoneId.systemDefault()).format(Instant.parse(lastSync))
        }.getOrDefault("—")
        stringResource(R.string.last_sync_value, timestamp)
    }
}

@Composable
private fun localizedConfidence(confidence: String): String = stringResource(
    when (confidence.uppercase()) {
        "LOW" -> R.string.confidence_low
        "MEDIUM" -> R.string.confidence_medium
        "HIGH" -> R.string.confidence_high
        else -> R.string.confidence_unavailable
    },
)

@Composable
private fun localizedPhase(phase: String): String = stringResource(
    when (phase.uppercase()) {
        "MENSTRUATION" -> R.string.phase_menstruation
        "FOLLICULAR_ESTIMATE" -> R.string.phase_follicular_estimate
        "OVULATION_ESTIMATE" -> R.string.phase_ovulation_estimate
        "LUTEAL_ESTIMATE" -> R.string.phase_luteal_estimate
        "PREMENSTRUAL_ESTIMATE" -> R.string.phase_premenstrual_estimate
        else -> R.string.phase_unknown
    },
)

private fun formatDate(raw: String): String = runCatching {
    LocalDate.parse(raw).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}.getOrDefault(raw)

private fun formatRange(from: String?, to: String?): String =
    if (from == null || to == null) "–" else "${formatDate(from)} – ${formatDate(to)}"

@Preview(showBackground = true, locale = "de", heightDp = 900)
@Composable
private fun TrackerPreview() = LunaraTheme {
    TrackerDashboard(
        "Alex",
        listOf(PeriodEntity("1", "u", "2026-08-10", createdAt = "", updatedAt = "")),
        emptyList(),
        null,
        1,
        0,
        null,
        onPeriodStarted = {},
        onPeriodEnded = {},
        onPeriodUpdated = { _, _, _ -> },
        onPeriodDeleted = {},
        onDailyLog = {},
        onSync = {},
        onDiscardConflicts = {},
    )
}

@Preview(showBackground = true, locale = "en", heightDp = 900)
@Composable
private fun PartnerPreview() = LunaraTheme {
    PartnerDashboard(
        "Sam",
        PartnerDashboardDto(
            prediction = PredictionDto(
                available = true,
                predictedStartFrom = "2026-09-18",
                predictedStartTo = "2026-09-21",
                confidence = "MEDIUM",
                basedOnCycleCount = 6,
            ),
            tip = PartnerTipDto("1", "Ask first", "Ask what would feel supportive today.", "Offer one small action", "COMMUNICATION"),
        ),
        unavailable = false,
        onRefresh = {},
        checkInSent = false,
        checkInError = false,
        onCheckIn = {},
    )
}
