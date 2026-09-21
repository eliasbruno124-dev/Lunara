package de.lunara.app.ui.main

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.lunara.app.R
import de.lunara.app.data.preferences.AppMode
import de.lunara.app.data.preferences.AppPreferencesState
import de.lunara.app.data.remote.dto.PredictionDto
import de.lunara.app.data.remote.dto.PartnerTipDto
import de.lunara.app.data.repository.RelationshipState
import de.lunara.app.ui.components.InsightCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun SyncStatusAction(preferences: AppPreferencesState, state: MainUiState, onSync: () -> Unit, onLogin: () -> Unit) {
    var details by remember { mutableStateOf(false) }
    val badge = ConnectionPresentation.badge(preferences.appMode, preferences.reauthenticationRequired, state.conflictMutations, state.pendingMutations)
    val label = when (badge) {
        SyncBadge.LOCAL -> stringResource(R.string.status_local_only)
        SyncBadge.AUTHENTICATION -> stringResource(R.string.status_login_needed)
        SyncBadge.CONFLICT -> stringResource(R.string.status_conflict)
        SyncBadge.PENDING -> stringResource(R.string.status_pending, state.pendingMutations)
        SyncBadge.SERVER -> stringResource(R.string.status_server_sync)
    }
    TextButton(onClick = { details = true }, modifier = Modifier.padding(end = 8.dp)) { Text(label) }
    if (details) AlertDialog(
        onDismissRequest = { details = false },
        title = { Text(stringResource(R.string.synchronization)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(if (badge == SyncBadge.LOCAL) R.string.status_local_detail else R.string.status_server_detail))
                if (badge != SyncBadge.LOCAL) {
                    val last = state.partnerDashboardLastSyncAt ?: state.lastSyncAt
                    Text(last?.let { stringResource(R.string.last_sync_value, readableTimestamp(it)) } ?: stringResource(R.string.last_sync_never))
                    if (state.pendingMutations > 0) Text(stringResource(R.string.sync_pending, state.pendingMutations))
                    if (state.conflictMutations > 0) Text(stringResource(R.string.status_conflict_detail))
                    if (preferences.reauthenticationRequired) Text(stringResource(R.string.session_recovery_preserves_data))
                }
            }
        },
        confirmButton = { TextButton(onClick = { details = false }) { Text(stringResource(R.string.close)) } },
        dismissButton = {
            if (badge != SyncBadge.LOCAL) TextButton(onClick = { details = false; if (preferences.reauthenticationRequired) onLogin() else onSync() }) {
                Text(stringResource(if (preferences.reauthenticationRequired) R.string.renew_login else R.string.sync_now))
            }
        },
    )
}

@Composable
internal fun RelationshipCard(state: RelationshipState, onRefresh: () -> Unit) {
    val summary = when {
        !state.known -> stringResource(if (state.unavailable) R.string.relationship_unknown else R.string.relationship_loading)
        state.connected -> state.peerName?.let { stringResource(R.string.relationship_with, it) } ?: stringResource(R.string.partner_connected)
        else -> stringResource(R.string.relationship_none)
    }
    InsightCard(Icons.Rounded.FavoriteBorder, stringResource(R.string.relationship_title), summary, action = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (state.connected) Text(stringResource(R.string.relationship_sharing_independent), style = MaterialTheme.typography.bodySmall)
            if (state.unavailable && state.known) Text(stringResource(R.string.relationship_cached, state.checkedAt?.let(::readableTimestamp) ?: "—"), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRefresh, enabled = !state.refreshing) {
                Text(stringResource(if (state.refreshing) R.string.relationship_loading else R.string.relationship_refresh))
            }
        }
    })
}

@Composable
internal fun PredictionExplanation(prediction: PredictionDto) {
    val days = ConnectionPresentation.predictionWindowDays(prediction) ?: return
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(stringResource(R.string.prediction_window_days, days), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(if (prediction.basedOnCycleCount < 3) R.string.prediction_few_cycles else R.string.prediction_window_basis, prediction.basedOnCycleCount), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.prediction_no_exact_day), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun DonationButton() {
    val configured = stringResource(R.string.donation_url)
    val url = remember(configured) { DonationLinkPolicy.safeUrl(configured) } ?: return
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.donation_button)) }
        Text(stringResource(R.string.donation_optional), style = MaterialTheme.typography.bodySmall)
        if (failed) Text(stringResource(R.string.donation_open_failed), color = MaterialTheme.colorScheme.error)
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.donation_button)) },
        text = { Text(stringResource(R.string.donation_confirm, url)) },
        confirmButton = { TextButton(onClick = {
            confirm = false
            failed = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)) }.isFailure
        }) { Text(stringResource(R.string.donation_open)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun readableTimestamp(value: String): String = runCatching {
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault()).format(Instant.parse(value))
}.getOrDefault("—")

@Composable
internal fun TipBasis(tip: PartnerTipDto) {
    val label = when (tip.selectionReason) {
        "PERSONAL_REQUEST" -> R.string.tip_reason_personal
        "CARE_PREFERENCE" -> R.string.tip_reason_preference
        "SHARED_SYMPTOM" -> R.string.tip_reason_symptom
        "SHARED_CONTEXT" -> R.string.tip_reason_context
        else -> R.string.tip_reason_general
    }
    Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    val context = LocalContext.current
    val source = tip.source
    if (source?.url == "https://www.nhs.uk/symptoms/period-pain/") {
        TextButton(onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url)).addCategory(Intent.CATEGORY_BROWSABLE)) }
        }) { Text(stringResource(R.string.tip_source_nhs)) }
    }
}
