package de.lunara.app.ui.main

import de.lunara.app.data.local.PeriodEntity
import de.lunara.app.data.repository.SyncEditPolicy
import java.time.LocalDate

internal enum class PeriodPrimaryActionType {
    START,
    END,
    DONE,
}

internal data class PeriodPrimaryAction(
    val type: PeriodPrimaryActionType,
    val enabled: Boolean,
    val periodId: String? = null,
    val startDate: LocalDate? = null,
)

/** Derives the tracker dashboard action exclusively from locally persisted optimistic state. */
internal object PeriodPrimaryActionPolicy {
    fun resolve(periods: List<PeriodEntity>, today: LocalDate): PeriodPrimaryAction {
        val datedPeriods = periods.mapNotNull { period ->
            if (period.deletedAt != null) return@mapNotNull null
            val startDate = runCatching { LocalDate.parse(period.startDate) }.getOrNull()
                ?: return@mapNotNull null
            if (startDate.isAfter(today)) return@mapNotNull null
            DatedPeriod(period, startDate)
        }

        val open = datedPeriods
            .filter { it.period.endDate == null }
            .maxByOrNull { it.startDate }
        if (open != null) {
            return PeriodPrimaryAction(
                type = PeriodPrimaryActionType.END,
                enabled = !SyncEditPolicy.isBlocked(open.period.syncState),
                periodId = open.period.id,
                startDate = open.startDate,
            )
        }

        val completedToday = datedPeriods
            .filter { it.period.endDate == today.toString() }
            .maxByOrNull { it.startDate }
        if (completedToday != null) {
            return PeriodPrimaryAction(
                type = PeriodPrimaryActionType.DONE,
                enabled = false,
                periodId = completedToday.period.id,
                startDate = completedToday.startDate,
            )
        }

        return PeriodPrimaryAction(
            type = PeriodPrimaryActionType.START,
            enabled = true,
        )
    }

    private data class DatedPeriod(
        val period: PeriodEntity,
        val startDate: LocalDate,
    )
}
