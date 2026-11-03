package de.lunara.app.data.prediction

import de.lunara.app.core.model.PeriodEndPolicy
import de.lunara.app.data.remote.dto.PredictionDto
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class FertilityWindow(val from: LocalDate, val to: LocalDate)

/** Defensive v2 eligibility ceiling, not clinical validation. Reject wide legacy caches; never clip. */
object FertilityWindowPolicy {
    const val MAX_INCLUSIVE_DAYS = 19L

    fun validated(prediction: PredictionDto?): FertilityWindow? {
        val safety = prediction?.fertilityEstimate ?: return null
        if (!safety.enabled || !safety.available || !safety.estimated || !safety.notContraception ||
            !safety.doesNotConfirmOvulation || safety.reason != "AVAILABLE"
        ) return null
        val from = PeriodEndPolicy.parse(prediction.fertileWindowFrom) ?: return null
        val to = PeriodEndPolicy.parse(prediction.fertileWindowTo) ?: return null
        if (ChronoUnit.DAYS.between(from, to) + 1 !in 1..MAX_INCLUSIVE_DAYS) return null
        // Overall availability may be withheld independently in a fertility-only partner payload.
        return FertilityWindow(from, to)
    }

    fun current(prediction: PredictionDto?, today: LocalDate, pendingChanges: Boolean = false): FertilityWindow? =
        if (pendingChanges) null else validated(prediction)?.takeUnless { it.to.isBefore(today) }
}
