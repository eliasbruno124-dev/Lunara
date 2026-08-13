package de.lunara.app.ui.main

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import de.lunara.app.security.limitAccountPasswordInput
import de.lunara.app.ui.components.InsightCard
import de.lunara.app.ui.components.LunaraHeader
import de.lunara.app.ui.components.StatusPill
import de.lunara.app.ui.components.QrCode
import de.lunara.app.ui.theme.LunaraTheme
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(
    session: AppSession,
    preferences: AppPreferencesState,
    onLogout: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val destinations = remember(session.role) { RoleNavigation.destinations(session.role) }
    val backStack by navController.currentBackStackEntryAsState()
    val selected = backStack?.destination?.route ?: MainDestination.TODAY.route
    val snackbar = remember { SnackbarHostState() }
    val periodSavedMessage = stringResource(R.string.period_saved_offline)
    val periodUpdatedMessage = stringResource(R.string.period_updated_offline)
    val periodDeletedMessage = stringResource(R.string.period_deleted_offline)
    val periodChangeFailedMessage = stringResource(R.string.period_change_failed)
    val dailyLogSavedMessage = stringResource(R.string.daily_log_saved_offline)
    val dailyLogFailedMessage = stringResource(R.string.daily_log_change_failed)
    val passwordChangedMessage = stringResource(R.string.password_change_success)

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            snackbar.showSnackbar(
                when (event) {
                    MainEvent.PeriodSaved -> periodSavedMessage
                    MainEvent.PeriodUpdated -> periodUpdatedMessage
                    MainEvent.PeriodDeleted -> periodDeletedMessage
                    MainEvent.PeriodChangeFailed -> periodChangeFailedMessage
                    MainEvent.DailyLogSaved -> dailyLogSavedMessage
                    MainEvent.DailyLogFailed -> dailyLogFailedMessage
                    MainEvent.PasswordChanged -> passwordChangedMessage
                },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lunara", fontWeight = FontWeight.SemiBold) },
                actions = { StatusPill(stringResource(R.string.offline_badge), Modifier.padding(end = 12.dp)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
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
        NavHost(
            navController = navController,
            startDestination = MainDestination.TODAY.route,
            modifier = Modifier.padding(insets),
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
                    )
                } else {
                    PartnerDashboard(
                        displayName = session.displayName,
                        dashboard = state.partnerDashboard,
                        unavailable = state.partnerDashboardUnavailable,
                        onRefresh = viewModel::refreshPartnerDashboard,
                        checkInSent = state.checkInSent,
                        checkInError = state.checkInError,
                        onCheckIn = viewModel::sendCheckIn,
                    )
                }
            }
            composable(MainDestination.CALENDAR.route) {
                CalendarScreen(
                    state.periods,
                    session.role,
                    if (session.role == UserRole.TRACKER) state.trackerPrediction else state.partnerDashboard?.prediction,
                    state.partnerDashboard?.periodDates.orEmpty(),
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
                )
            }
            composable(MainDestination.SUPPORT.route) {
                SupportScreen(
                    state.partnerDashboard,
                    state.checkInSent,
                    state.checkInError,
                    viewModel::sendCheckIn,
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
                    onSync = viewModel::syncNow,
                    sharing = state.sharing,
                    cycleProfile = state.cycleProfile,
                    carePreferences = state.carePreferences,
                    invitation = state.invitation,
                    partnerConnected = state.partnerConnected,
                    settingsBusy = state.settingsBusy,
                    settingsError = state.settingsError,
                    passwordChangeStatus = state.passwordChangeStatus,
                    onCreateInvitation = viewModel::createInvitation,
                    onRefreshInvitation = viewModel::refreshInvitation,
                    onRevokeInvitation = viewModel::revokeInvitation,
                    onUpdateSharing = viewModel::updateSharing,
                    onUpdateCarePreferences = viewModel::updateCarePreferences,
                    onFertilityPrediction = viewModel::setFertilityPredictionEnabled,
                    onChangePassword = viewModel::changePassword,
                    onClearPasswordChangeStatus = viewModel::clearPasswordChangeStatus,
                    onLogout = onLogout,
                )
            }
        }
    }
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
    onPeriodUpdated: (String, LocalDate, LocalDate?) -> Unit,
    onPeriodDeleted: (String) -> Unit,
    onDailyLog: (TodayLogDraft) -> Unit,
    onSync: () -> Unit,
    onDiscardConflicts: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var showTodayLog by remember { mutableStateOf(false) }
    var showDiscardConflicts by remember { mutableStateOf(false) }
    val today = LocalDate.now().toString()
    val todayLog = dailyLogs.firstOrNull { it.localDate == today }
    val periodStartResolving = periods.any { it.startDate == today && it.syncState == "RESOLVING" }
    val dailyLogResolving = dailyLogs.any { it.localDate == today && it.syncState == "RESOLVING" }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            LunaraHeader(
                eyebrow = stringResource(R.string.today),
                title = stringResource(R.string.tracker_greeting, displayName),
                modifier = Modifier.padding(top = 20.dp),
            )
        }
        item {
            InsightCard(
                Icons.Rounded.DateRange,
                stringResource(R.string.estimated_period),
                prediction?.takeIf { it.available }?.let { formatRange(it.predictedStartFrom, it.predictedStartTo) }
                    ?: stringResource(R.string.not_enough_data),
                action = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill(stringResource(R.string.estimated_label))
                        prediction?.takeIf { it.available }?.let {
                            Text(
                                stringResource(R.string.prediction_basis, it.basedOnCycleCount, localizedConfidence(it.confidence)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        }
        PredictionPresentationPolicy.fertileWindow(prediction)?.let { fertileWindow ->
            item {
                FertilityEstimateCard(fertileWindow)
            }
        }
        item {
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onPeriodStarted()
                },
                enabled = !periodStartResolving,
                modifier = Modifier.fillMaxWidth().height(60.dp).semantics { role = Role.Button }.testTag("period-start"),
            ) {
                Icon(Icons.Rounded.AddCircle, contentDescription = null)
                Text(stringResource(R.string.period_start_action), Modifier.padding(start = 10.dp))
            }
        }
        item {
            OutlinedButton(
                onClick = { showTodayLog = true },
                enabled = !dailyLogResolving,
                modifier = Modifier.fillMaxWidth().height(56.dp).testTag("today-log"),
            ) {
                Icon(Icons.AutoMirrored.Rounded.List, contentDescription = null)
                Text(stringResource(R.string.today_log), Modifier.padding(start = 10.dp))
            }
        }
        dailyLogs.firstOrNull()?.let { latest ->
            item { DailyLogRow(latest) }
        }
        item {
            InsightCard(
                Icons.Rounded.Refresh,
                stringResource(R.string.sync_status),
                syncDescription(pendingMutations, conflictMutations, lastSyncAt),
                action = {
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = onSync) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Text(stringResource(R.string.sync_now), Modifier.padding(start = 6.dp))
                        }
                        if (conflictMutations > 0) {
                            TextButton(onClick = { showDiscardConflicts = true }) {
                                Text(stringResource(R.string.resolve_sync_conflicts))
                            }
                        }
                    }
                },
            )
        }
        if (periods.isNotEmpty()) {
            item { Text(stringResource(R.string.history), style = MaterialTheme.typography.titleLarge) }
            items(periods.take(3), key = { it.id }) { period ->
                EditablePeriodRow(period, onPeriodUpdated, onPeriodDeleted)
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
    InsightCard(
        Icons.AutoMirrored.Rounded.List,
        stringResource(R.string.daily_log_date, formatDate(log.localDate)),
        details,
        action = {
            StatusPill(
                when (log.syncState) {
                    "SYNCED" -> stringResource(R.string.synced)
                    "CONFLICT" -> stringResource(R.string.sync_conflict)
                    "RESOLVING" -> stringResource(R.string.sync_resolving)
                    else -> stringResource(R.string.pending)
                },
            )
        },
    )
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
) {
    val checkInPrompt = stringResource(R.string.check_in)
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            LunaraHeader(
                eyebrow = stringResource(R.string.today),
                title = stringResource(R.string.partner_greeting, displayName),
                body = stringResource(R.string.privacy_guard),
                modifier = Modifier.padding(top = 20.dp),
            )
        }
        item {
            val prediction = dashboard?.prediction
            InsightCard(
                Icons.Rounded.DateRange,
                stringResource(R.string.estimated_period),
                prediction?.takeIf { it.available }?.let { formatRange(it.predictedStartFrom, it.predictedStartTo) }
                    ?: stringResource(R.string.not_enough_data),
                action = { StatusPill(stringResource(R.string.estimated_label)) },
            )
        }
        PredictionPresentationPolicy.disclosedPartnerPhase(dashboard)?.let { phase ->
            item {
                InsightCard(
                    Icons.Rounded.Info,
                    stringResource(R.string.current_estimated_phase),
                    localizedPhase(phase),
                    action = { StatusPill(stringResource(R.string.estimated_label)) },
                )
            }
        }
        PredictionPresentationPolicy.fertileWindow(dashboard?.prediction)?.let { fertileWindow ->
            item { FertilityEstimateCard(fertileWindow) }
        }
        item {
            val tip = dashboard?.tip
            if (tip == null) {
                InsightCard(
                    Icons.Rounded.Info,
                    stringResource(R.string.partner_tip_title),
                    stringResource(R.string.no_partner_tip_available),
                )
            } else {
                InsightCard(
                    Icons.Rounded.Star,
                    tip.title,
                    tip.body,
                    action = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PredictionPresentationPolicy.phaseAwareTipPhase(dashboard)?.let {
                                StatusPill(stringResource(R.string.tip_uses_shared_phase))
                            }
                            Text(
                                tip.callToAction,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    },
                )
            }
        }
        item {
            FilledTonalButton(onClick = { onCheckIn(checkInPrompt) }, enabled = !checkInSent, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Rounded.FavoriteBorder, contentDescription = null)
                Text(stringResource(R.string.check_in), Modifier.padding(start = 10.dp))
            }
        }
        if (checkInSent) item { Text(stringResource(R.string.check_in_sent), color = MaterialTheme.colorScheme.tertiary) }
        if (checkInError) item { Text(stringResource(R.string.check_in_error), color = MaterialTheme.colorScheme.error) }
        if (unavailable) {
            item {
                OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Text(stringResource(R.string.sync_now), Modifier.padding(start = 8.dp))
                }
            }
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
private fun CalendarScreen(
    periods: List<PeriodEntity>,
    role: UserRole,
    prediction: PredictionDto?,
    sharedPeriodDates: List<Map<String, Any?>>,
) {
    val month = remember { java.time.YearMonth.now() }
    val locale = LocalLocale.current.platformLocale
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value - 1
    val cells = List(leading) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    val periodDays = remember(periods, sharedPeriodDates) {
        val ownDays = periods.flatMap { period ->
            val start = runCatching { LocalDate.parse(period.startDate) }.getOrNull() ?: return@flatMap emptyList()
            val end = runCatching { LocalDate.parse(period.endDate ?: period.startDate) }.getOrDefault(start)
            generateSequence(start) { current -> current.plusDays(1).takeIf { !it.isAfter(end) } }.toList()
        }
        val sharedDays = sharedPeriodDates.flatMap { period ->
            val rawStart = period["startDate"] as? String ?: return@flatMap emptyList()
            val start = runCatching { LocalDate.parse(rawStart) }.getOrNull() ?: return@flatMap emptyList()
            val end = runCatching { LocalDate.parse(period["endDate"] as? String ?: rawStart) }.getOrDefault(start)
            generateSequence(start) { current -> current.plusDays(1).takeIf { !it.isAfter(end) } }.toList()
        }
        (ownDays + sharedDays).toSet()
    }
    val predictionDays = remember(prediction) {
        val start = prediction?.predictedStartFrom?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val end = prediction?.predictedStartTo?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (start == null || end == null || prediction.available.not()) emptySet() else
            generateSequence(start) { current -> current.plusDays(1).takeIf { !it.isAfter(end) } }.toSet()
    }
    val fertilityRange = remember(prediction) { PredictionPresentationPolicy.fertileWindow(prediction) }
    val fertilityDays = remember(fertilityRange) {
        val start = fertilityRange?.from?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val end = fertilityRange?.to?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (start == null || end == null) emptySet() else
            generateSequence(start) { current -> current.plusDays(1).takeIf { !it.isAfter(end) } }.toSet()
    }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { LunaraHeader(title = stringResource(R.string.calendar), body = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))) }
        item {
            Row(Modifier.fillMaxWidth()) {
                java.time.DayOfWeek.entries.forEach { day ->
                    Text(day.getDisplayName(java.time.format.TextStyle.NARROW, locale), Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
        items(cells.chunked(7)) { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    if (date == null) Spacer(Modifier.weight(1f).height(52.dp))
                    else DayCell(
                        date,
                        date in periodDays,
                        date in predictionDays,
                        date in fertilityDays,
                        Modifier.weight(1f),
                    )
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f).height(52.dp)) }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.calendar_actual_marker), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.calendar_prediction_marker), style = MaterialTheme.typography.bodyMedium)
                if (fertilityDays.isNotEmpty()) {
                    Text(stringResource(R.string.calendar_fertility_marker), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (fertilityDays.isNotEmpty()) {
            item { FertilitySafetyNotice() }
        }
        if (role == UserRole.PARTNER) item { Text(stringResource(R.string.privacy_guard), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    actual: Boolean,
    predicted: Boolean,
    fertileEstimate: Boolean,
    modifier: Modifier = Modifier,
) {
    val description = when {
        actual -> stringResource(R.string.calendar_day_period, date.dayOfMonth)
        predicted -> stringResource(R.string.calendar_day_prediction, date.dayOfMonth)
        fertileEstimate -> stringResource(R.string.calendar_day_fertility, date.dayOfMonth)
        else -> date.dayOfMonth.toString()
    }
    Card(
        modifier.padding(2.dp).height(52.dp).semantics { contentDescription = description },
        colors = CardDefaults.cardColors(
            containerColor = when {
                actual -> MaterialTheme.colorScheme.secondaryContainer
                predicted -> MaterialTheme.colorScheme.tertiaryContainer
                fertileEstimate -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(Modifier.fillMaxSize().padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium)
            if (actual) Text("P", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            else if (predicted) Text("~", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
            else if (fertileEstimate) Text("F?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun HistoryScreen(
    periods: List<PeriodEntity>,
    prediction: PredictionDto?,
    onPeriodUpdated: (String, LocalDate, LocalDate?) -> Unit,
    onPeriodDeleted: (String) -> Unit,
) {
    val starts = periods.mapNotNull { runCatching { LocalDate.parse(it.startDate) }.getOrNull() }.distinct().sorted()
    val lengths = starts.zipWithNext { first, second -> java.time.temporal.ChronoUnit.DAYS.between(first, second).toInt() }
    val average = lengths.takeIf { it.isNotEmpty() }?.average()?.toInt()
    val sorted = lengths.sorted()
    val median = sorted.takeIf { it.isNotEmpty() }?.let { values ->
        if (values.size % 2 == 1) values[values.size / 2] else (values[values.size / 2 - 1] + values[values.size / 2]) / 2
    }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { LunaraHeader(title = stringResource(R.string.cycle_insights), body = stringResource(R.string.recorded_cycles, lengths.size)) }
        if (lengths.isEmpty()) {
            item { InsightCard(Icons.AutoMirrored.Rounded.List, stringResource(R.string.history), stringResource(R.string.single_record_history)) }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(stringResource(R.string.average_cycle), average ?: 0, Modifier.weight(1f))
                    StatCard(stringResource(R.string.median_cycle), median ?: 0, Modifier.weight(1f))
                }
            }
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
            EditablePeriodRow(period, onPeriodUpdated, onPeriodDeleted)
        }
    }
}

@Composable
private fun StatCard(title: String, days: Int, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.days_value, days), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun SupportScreen(
    dashboard: PartnerDashboardDto?,
    checkInSent: Boolean,
    checkInError: Boolean,
    onCheckIn: (String) -> Unit,
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
                InsightCard(
                    Icons.Rounded.Star,
                    tip.title,
                    tip.body,
                    action = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PredictionPresentationPolicy.phaseAwareTipPhase(dashboard)?.let {
                                StatusPill(stringResource(R.string.tip_uses_shared_phase))
                            }
                            Text(tip.callToAction, color = MaterialTheme.colorScheme.tertiary)
                        }
                    },
                )
            }
        }
        item {
            FilledTonalButton(onClick = { onCheckIn(checkInPrompt) }, enabled = !checkInSent, modifier = Modifier.fillMaxWidth().height(56.dp)) {
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
    onUpdate: (String, LocalDate, LocalDate?) -> Unit,
    onDelete: (String) -> Unit,
) {
    var showEditor by remember(period.id) { mutableStateOf(false) }
    val editable = !SyncEditPolicy.isBlocked(period.syncState)
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
            onUpdate = { start, end ->
                onUpdate(period.id, start, end)
                showEditor = false
            },
            onDelete = {
                onDelete(period.id)
                showEditor = false
            },
            onDismiss = { showEditor = false },
        )
    }
}

@Composable
internal fun PeriodEditorDialog(
    period: PeriodEntity,
    onUpdate: (LocalDate, LocalDate?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var rawStartDate by remember(period.id, period.startDate) { mutableStateOf(period.startDate) }
    var rawEndDate by remember(period.id, period.endDate) { mutableStateOf(period.endDate.orEmpty()) }
    var confirmDelete by remember(period.id) { mutableStateOf(false) }
    val validation = remember(rawStartDate, rawEndDate) {
        PeriodEditValidation.validateRange(rawStartDate, rawEndDate)
    }
    val validationError = (validation as? PeriodDateValidationResult.Invalid)?.error
    val validDates = (validation as? PeriodDateValidationResult.Valid)?.dates
    val changed = validDates?.let {
        it.startDate.toString() != period.startDate || it.endDate?.toString() != period.endDate
    } == true

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_period_start)) },
            text = { Text(stringResource(R.string.delete_period_confirmation, formatDate(period.startDate))) },
            confirmButton = {
                TextButton(onClick = onDelete, modifier = Modifier.testTag("period-delete-confirm")) {
                    Icon(Icons.Rounded.Delete, contentDescription = null)
                    Text(stringResource(R.string.delete), Modifier.padding(start = 6.dp))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_period_dates)) },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.period_date_notice), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = rawStartDate,
                    onValueChange = { rawStartDate = PeriodEditValidation.sanitizeDateInput(it) },
                    label = { Text(stringResource(R.string.period_start_date)) },
                    placeholder = { Text("2026-08-13") },
                    supportingText = {
                        if (validationError == PeriodDateValidationError.INVALID_START) {
                            Text(stringResource(R.string.period_start_invalid))
                        }
                        else Text(stringResource(R.string.period_date_format))
                    },
                    isError = validationError == PeriodDateValidationError.INVALID_START,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().testTag("period-start-input"),
                )
                OutlinedTextField(
                    value = rawEndDate,
                    onValueChange = { rawEndDate = PeriodEditValidation.sanitizeDateInput(it) },
                    label = { Text(stringResource(R.string.period_end_date_optional)) },
                    placeholder = { Text("2026-08-17") },
                    supportingText = {
                        when (validationError) {
                            PeriodDateValidationError.INVALID_END -> Text(stringResource(R.string.period_end_invalid))
                            PeriodDateValidationError.END_BEFORE_START -> Text(stringResource(R.string.period_end_before_start))
                            else -> Text(stringResource(R.string.period_end_optional_hint))
                        }
                    },
                    isError = validationError == PeriodDateValidationError.INVALID_END ||
                        validationError == PeriodDateValidationError.END_BEFORE_START,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().testTag("period-end-input"),
                )
                if (rawEndDate.isNotBlank()) {
                    TextButton(
                        onClick = { rawEndDate = "" },
                        modifier = Modifier.align(Alignment.End).testTag("period-end-clear"),
                    ) { Text(stringResource(R.string.remove_period_end)) }
                }
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.align(Alignment.End).testTag("period-delete"),
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null)
                    Text(stringResource(R.string.delete_period_start), Modifier.padding(start = 6.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { validDates?.let { onUpdate(it.startDate, it.endDate) } },
                enabled = changed,
                modifier = Modifier.testTag("period-update-confirm"),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SettingsScreen(
    session: AppSession,
    preferences: AppPreferencesState,
    onPrivacyMode: (Boolean) -> Unit,
    onUseSystemTheme: (Boolean) -> Unit,
    onDarkMode: (Boolean) -> Unit,
    onSync: () -> Unit,
    sharing: SharingSettingsDto?,
    cycleProfile: CycleProfileDto?,
    carePreferences: CarePreferencesDto?,
    invitation: de.lunara.app.data.remote.dto.InvitationDto?,
    partnerConnected: Boolean,
    settingsBusy: Boolean,
    settingsError: Boolean,
    passwordChangeStatus: PasswordChangeStatus,
    onCreateInvitation: () -> Unit,
    onRefreshInvitation: () -> Unit,
    onRevokeInvitation: () -> Unit,
    onUpdateSharing: (UpdateSharingRequest) -> Unit,
    onUpdateCarePreferences: (UpdateCarePreferencesRequest) -> Unit,
    onFertilityPrediction: (Boolean) -> Unit,
    onChangePassword: (String, String, String) -> Unit,
    onClearPasswordChangeStatus: () -> Unit,
    onLogout: () -> Unit,
) {
    var confirmLogout by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    LaunchedEffect(session.userId) {
        if (session.role == UserRole.TRACKER) onRefreshInvitation()
    }
    LaunchedEffect(passwordChangeStatus) {
        if (passwordChangeStatus == PasswordChangeStatus.SUCCESS) {
            showPasswordDialog = false
            onClearPasswordChangeStatus()
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { LunaraHeader(title = stringResource(R.string.settings), modifier = Modifier.padding(vertical = 18.dp)) }
        item { SettingHeader(stringResource(R.string.server_information)) }
        item { SettingText(Icons.Rounded.Info, session.serverName, "${session.serverVersion} · ${BuildConfig.VERSION_NAME}") }
        item { SettingHeader(stringResource(R.string.account_security)) }
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
        item { SettingText(Icons.Rounded.Lock, stringResource(R.string.no_sensitive_lock_screen), null) }
        item { SettingText(Icons.Rounded.Refresh, stringResource(R.string.sync_now), null, onSync) }
        if (session.role == UserRole.TRACKER) {
            item { SettingHeader(stringResource(R.string.fertility_predictions)) }
            item {
                FertilityPredictionControl(
                    profile = cycleProfile,
                    busy = settingsBusy,
                    onEnabled = onFertilityPrediction,
                )
            }
            item { SettingHeader(stringResource(R.string.partner_connect)) }
            item {
                InsightCard(
                    Icons.Rounded.FavoriteBorder,
                    stringResource(R.string.partner_connect),
                    stringResource(R.string.partner_connect_summary),
                    action = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (partnerConnected) {
                                Text(
                                    stringResource(R.string.partner_connected),
                                    fontWeight = FontWeight.Bold,
                                )
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
                                    QrCode(code, stringResource(R.string.invitation_qr_description, code))
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
                                enabled = !settingsBusy && invitation == null && !partnerConnected,
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
            item { SettingHeader(stringResource(R.string.care_preferences)) }
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
            if (settingsError) item { Text(stringResource(R.string.settings_save_error), color = MaterialTheme.colorScheme.error) }
        }
        item { HorizontalDivider(Modifier.padding(vertical = 10.dp)) }
        item { SettingText(Icons.AutoMirrored.Rounded.ExitToApp, stringResource(R.string.logout), null) { confirmLogout = true } }
        item { Spacer(Modifier.height(20.dp)) }
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.logout)) },
            text = { Text(stringResource(R.string.privacy_mode_summary)) },
            confirmButton = { TextButton(onClick = onLogout) { Text(stringResource(R.string.logout)) } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.back_label)) } },
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
    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Column(Modifier.padding(start = 14.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
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
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null)
        Column(Modifier.padding(horizontal = 14.dp).weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked, onChecked, enabled = enabled)
    }
}

@Composable
private fun syncDescription(pending: Int, conflicts: Int, lastSync: String?): String = when {
    conflicts > 0 -> stringResource(R.string.sync_conflicts, conflicts)
    pending > 0 -> stringResource(R.string.sync_pending, pending)
    lastSync == null -> stringResource(R.string.last_sync_never)
    else -> stringResource(R.string.last_sync_value, lastSync.take(16).replace('T', ' '))
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
