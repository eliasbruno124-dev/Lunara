package de.lunara.app.ui.main

import de.lunara.app.data.remote.dto.PartnerDashboardDto
import de.lunara.app.data.remote.dto.PredictionDto
import java.time.LocalDate

internal data class PredictionDateRange(val from: String, val to: String)

/**
 * Keeps sensitive prediction presentation fail-closed. The Android app never derives a partner's
 * fertility or cycle phase from period dates; it renders only fields already projected by the
 * server after applying the tracker's sharing settings.
 */
internal object PredictionPresentationPolicy {
    private val disclosedPhases = setOf(
        "MENSTRUATION",
        "FOLLICULAR_ESTIMATE",
        "OVULATION_ESTIMATE",
        "LUTEAL_ESTIMATE",
        "PREMENSTRUAL_ESTIMATE",
    )

    fun fertileWindow(prediction: PredictionDto?): PredictionDateRange? {
        val safety = prediction?.fertilityEstimate ?: return null
        if (
            !safety.enabled || !safety.available || !safety.estimated ||
            !safety.notContraception || !safety.doesNotConfirmOvulation ||
            safety.reason != "AVAILABLE"
        ) return null
        val from = prediction.fertileWindowFrom ?: return null
        val to = prediction.fertileWindowTo ?: return null
        val fromDate = runCatching { LocalDate.parse(from) }.getOrNull() ?: return null
        val toDate = runCatching { LocalDate.parse(to) }.getOrNull() ?: return null
        if (toDate.isBefore(fromDate)) return null
        return PredictionDateRange(from, to)
    }

    fun disclosedPartnerPhase(dashboard: PartnerDashboardDto?): String? =
        dashboard?.phase?.uppercase()?.takeIf(disclosedPhases::contains)

    fun phaseAwareTipPhase(dashboard: PartnerDashboardDto?): String? {
        val disclosed = disclosedPartnerPhase(dashboard) ?: return null
        val applicable = dashboard?.tip?.applicablePhase?.uppercase() ?: return null
        return disclosed.takeIf { it == applicable }
    }
}

internal object FertilitySharingPolicy {
    fun mayEnableSharing(
        fertilityPredictionEnabled: Boolean,
        currentShareFertileWindow: Boolean,
        requested: Boolean,
    ): Boolean = !requested || currentShareFertileWindow || fertilityPredictionEnabled
}
