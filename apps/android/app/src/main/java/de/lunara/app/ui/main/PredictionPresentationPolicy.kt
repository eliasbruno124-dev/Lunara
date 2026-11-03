package de.lunara.app.ui.main

import de.lunara.app.data.remote.dto.PartnerDashboardDto
import de.lunara.app.data.remote.dto.PredictionDto
import de.lunara.app.data.prediction.FertilityWindowPolicy
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

    fun fertileWindow(prediction: PredictionDto?): PredictionDateRange? =
        FertilityWindowPolicy.validated(prediction)?.let { PredictionDateRange(it.from.toString(), it.to.toString()) }

    fun currentFertileWindow(prediction: PredictionDto?, today: LocalDate, pendingChanges: Boolean = false): PredictionDateRange? =
        FertilityWindowPolicy.current(prediction, today, pendingChanges)
            ?.let { PredictionDateRange(it.from.toString(), it.to.toString()) }

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
