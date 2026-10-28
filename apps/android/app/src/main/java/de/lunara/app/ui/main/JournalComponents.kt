package de.lunara.app.ui.main

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.lunara.app.R
import de.lunara.app.core.model.UserRole
import de.lunara.app.data.remote.dto.PartnerTipDto
import de.lunara.app.data.remote.dto.PredictionDto
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.ui.platform.LocalLocale

/** Decorative arcs express the brand, never a measured cycle or fertility score. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PredictionHero(prediction: PredictionDto?, modifier: Modifier = Modifier) {
    val ink = Color(0xFF173E43)
    val paper = Color(0xFFFFF8EC)
    val locale = LocalLocale.current.platformLocale
    var details by remember { mutableStateOf(false) }
    val available = JournalPredictionPolicy.hasStartWindow(prediction)
    val unavailableReason = when (prediction?.reason) {
        "PREDICTION_PAUSED" -> R.string.journal_prediction_paused
        "SPECIAL_MODE" -> R.string.journal_prediction_special
        "STALE_HISTORY" -> R.string.journal_prediction_stale
        else -> R.string.journal_prediction_empty_body
    }
    fun date(raw: String?): String = runCatching {
        LocalDate.parse(raw).format(DateTimeFormatter.ofPattern("d.\u00A0MMM", locale))
    }.getOrDefault("—")
    Surface(modifier.fillMaxWidth().testTag("prediction-hero"), shape = RoundedCornerShape(28.dp), color = ink, contentColor = paper) {
        BoxWithConstraints {
            val compact = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.2f
            Canvas(Modifier.align(Alignment.TopEnd).size(156.dp)) {
                val origin = Offset(size.width + 16.dp.toPx(), 4.dp.toPx())
                listOf(58f, 94f, 130f).forEach { radius ->
                    drawCircle(paper.copy(alpha = .12f), radius.dp.toPx(), origin, style = Stroke(1.dp.toPx()))
                }
            }
            Column(Modifier.padding(if (compact) 20.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.estimated_period), style = MaterialTheme.typography.labelLarge)
                Text(
                    if (available) "${date(prediction?.predictedStartFrom)} – ${date(prediction?.predictedStartTo)}"
                    else stringResource(if (unavailableReason == R.string.journal_prediction_empty_body) R.string.journal_prediction_empty else R.string.journal_prediction_unavailable),
                    style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    if (available) stringResource(R.string.journal_estimated_start) else stringResource(unavailableReason),
                    style = MaterialTheme.typography.bodyMedium,
                    color = paper.copy(alpha = .85f),
                )
                if (available) {
                    HorizontalDivider(color = paper.copy(alpha = .18f))
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val confidence = stringResource(when (prediction?.confidence?.uppercase()) {
                            "HIGH" -> R.string.confidence_high
                            "MEDIUM" -> R.string.confidence_medium
                            else -> R.string.confidence_low
                        })
                        Column(Modifier.padding(end = 12.dp).align(Alignment.CenterVertically)) {
                            Text(stringResource(R.string.journal_cycles_basis, prediction?.basedOnCycleCount ?: 0), style = MaterialTheme.typography.labelLarge)
                            Text(stringResource(R.string.journal_confidence, confidence), style = MaterialTheme.typography.bodySmall, color = paper.copy(alpha = .8f))
                        }
                        TextButton(onClick = { details = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("prediction-details")) {
                            Text(stringResource(R.string.journal_details), color = paper)
                        }
                    }
                }
            }
        }
    }
    if (details && prediction != null) AlertDialog(
        onDismissRequest = { details = false },
        title = { Text(stringResource(R.string.estimated_period)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { PredictionExplanation(prediction) } },
        confirmButton = { TextButton(onClick = { details = false }) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
internal fun JournalSection(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
internal fun PartnerActionCard(tip: PartnerTipDto, onNext: () -> Unit, refreshing: Boolean, phaseBased: Boolean = false) {
    var details by remember(tip.id) { mutableStateOf(false) }
    val detailsState = stringResource(if (details) R.string.journal_details_expanded else R.string.journal_details_collapsed)
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().testTag("partner-action-card")) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.journal_support_today), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
            Text(tip.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text(tip.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.journal_one_action), style = MaterialTheme.typography.labelLarge)
                    Text(tip.callToAction, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
            }
            Column(Modifier.fillMaxWidth()) {
                TextButton(onClick = onNext, enabled = !refreshing, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.tip_next))
                }
                TextButton(onClick = { details = !details }, modifier = Modifier.fillMaxWidth().semantics { stateDescription = detailsState }) {
                    Text(stringResource(R.string.journal_why_this_tip))
                    Text(if (details) " −" else " +")
                }
            }
            if (details) {
                if (phaseBased) Text(stringResource(R.string.tip_uses_shared_phase), style = MaterialTheme.typography.bodySmall)
                TipBasis(tip)
            }
        }
    }
}

internal enum class SettingsSection(@StringRes val title: Int, @StringRes val summary: Int) {
    APPEARANCE(R.string.appearance, R.string.journal_settings_appearance),
    CYCLE(R.string.journal_settings_cycle, R.string.journal_settings_cycle_body),
    PARTNER(R.string.partner_connect, R.string.journal_settings_partner),
    SHARING(R.string.sharing_settings, R.string.journal_settings_sharing),
    CARE(R.string.care_preferences, R.string.journal_settings_care),
    NOTIFICATIONS(R.string.notifications, R.string.journal_settings_notifications),
    ACCOUNT(R.string.account_security, R.string.journal_settings_account),
    ABOUT(R.string.app_information, R.string.journal_settings_about),
}

internal object JournalPredictionPolicy {
    fun hasStartWindow(prediction: PredictionDto?): Boolean {
        if (prediction?.available != true) return false
        val from = runCatching { LocalDate.parse(prediction.predictedStartFrom) }.getOrNull() ?: return false
        val to = runCatching { LocalDate.parse(prediction.predictedStartTo) }.getOrNull() ?: return false
        return !to.isBefore(from)
    }
}

internal fun availableSettingsSections(role: UserRole, local: Boolean): List<SettingsSection> =
    SettingsSection.entries.filter { section ->
        when (section) {
            SettingsSection.CYCLE -> role == UserRole.TRACKER
            SettingsSection.PARTNER -> !local
            SettingsSection.SHARING, SettingsSection.CARE -> role == UserRole.TRACKER && !local
            else -> true
        }
    }

@Composable
internal fun SettingsCategoryRow(section: SettingsSection, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 84.dp).padding(vertical = 16.dp).testTag("settings-section-${section.name.lowercase()}"),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, contentColor = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(14.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(12.dp).size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(section.title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(section.summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
}

@Composable
internal fun CycleLengthJournal(starts: List<LocalDate>, lengths: List<Int>) {
    val locale = LocalLocale.current.platformLocale
    val visible = lengths.indices.toList().takeLast(6)
    val maximum = visible.maxOfOrNull { lengths[it] }?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        JournalSection(stringResource(R.string.journal_cycle_lengths))
        visible.forEach { index ->
            val date = starts[index].format(DateTimeFormatter.ofPattern("d. MMM", locale))
            val days = lengths[index]
            val description = stringResource(R.string.journal_cycle_length_label, date, days)
            Row(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(date, modifier = Modifier.width(76.dp), style = MaterialTheme.typography.bodySmall)
                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(days.toFloat() / maximum).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.primary))
                }
                Text(stringResource(R.string.days_value, days), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
