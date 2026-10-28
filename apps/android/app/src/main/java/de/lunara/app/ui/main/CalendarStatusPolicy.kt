package de.lunara.app.ui.main

import de.lunara.app.data.remote.dto.PartnerDashboardDto

internal enum class CalendarLoadStatus { READY, LOADING, UNAVAILABLE, CACHED }
internal enum class CalendarPeriodNotice { NOT_SHARED, EMPTY, UNKNOWN }
internal enum class CalendarFertilityNotice { NOT_SHARED, DISABLED, INSUFFICIENT_DATA, UNRELIABLE_DATA, UNAVAILABLE }
internal enum class CalendarAccessStatus { SHARED, NOT_SHARED, UNKNOWN, LOADING, PAUSED }

internal data class PartnerCalendarStatus(
    val load: CalendarLoadStatus,
    val refreshing: Boolean,
    val sharingPaused: Boolean,
    val showPeriodDates: Boolean,
    val showPrediction: Boolean,
    val showFertility: Boolean,
    val periodNotice: CalendarPeriodNotice?,
    val fertilityNotice: CalendarFertilityNotice?,
    val periodAccess: CalendarAccessStatus,
    val fertilityAccess: CalendarAccessStatus,
)

/** Explains only server-disclosed facts; shared period dates never imply fertility or consent. */
internal object CalendarStatusPolicy {
    fun partner(
        dashboard: PartnerDashboardDto?,
        unavailable: Boolean,
        refreshing: Boolean,
    ): PartnerCalendarStatus {
        val load = when {
            dashboard == null && refreshing -> CalendarLoadStatus.LOADING
            dashboard == null -> CalendarLoadStatus.UNAVAILABLE
            unavailable -> CalendarLoadStatus.CACHED
            else -> CalendarLoadStatus.READY
        }
        val paused = dashboard?.sharingPaused == true
        val hasVisibleSnapshot = dashboard != null && !paused
        val periodAccess = dashboard?.calendarAccess?.periodDates
        val fertilityAccess = dashboard?.calendarAccess?.fertileWindow
        val showPeriods = hasVisibleSnapshot && periodAccess != false
        val showFertility = hasVisibleSnapshot && fertilityAccess != false
        val periodNotice = when {
            !hasVisibleSnapshot -> null
            periodAccess == false -> CalendarPeriodNotice.NOT_SHARED
            dashboard.periodDates.isNotEmpty() -> null
            periodAccess == true -> CalendarPeriodNotice.EMPTY
            else -> CalendarPeriodNotice.UNKNOWN
        }
        val fertilityNotice = when {
            !hasVisibleSnapshot -> null
            fertilityAccess == false -> CalendarFertilityNotice.NOT_SHARED
            PredictionPresentationPolicy.fertileWindow(dashboard.prediction) != null -> null
            // Never read the whole prediction's reason: it may describe a different estimate.
            else -> when (dashboard.prediction?.fertilityEstimate?.reason) {
                "DISABLED" -> CalendarFertilityNotice.DISABLED
                "INSUFFICIENT_DATA" -> CalendarFertilityNotice.INSUFFICIENT_DATA
                "UNRELIABLE_DATA" -> CalendarFertilityNotice.UNRELIABLE_DATA
                else -> CalendarFertilityNotice.UNAVAILABLE
            }
        }
        return PartnerCalendarStatus(
            load = load,
            refreshing = refreshing,
            sharingPaused = paused,
            showPeriodDates = showPeriods,
            showPrediction = hasVisibleSnapshot,
            showFertility = showFertility,
            periodNotice = periodNotice,
            fertilityNotice = fertilityNotice,
            periodAccess = accessStatus(periodAccess, paused, load),
            fertilityAccess = accessStatus(fertilityAccess, paused, load),
        )
    }

    private fun accessStatus(value: Boolean?, paused: Boolean, load: CalendarLoadStatus): CalendarAccessStatus = when {
        paused -> CalendarAccessStatus.PAUSED
        load == CalendarLoadStatus.LOADING -> CalendarAccessStatus.LOADING
        value == true -> CalendarAccessStatus.SHARED
        value == false -> CalendarAccessStatus.NOT_SHARED
        else -> CalendarAccessStatus.UNKNOWN
    }
}
