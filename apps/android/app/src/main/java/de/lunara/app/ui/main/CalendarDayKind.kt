package de.lunara.app.ui.main

/** Presentation precedence only: dates, sharing and estimates are resolved by existing policies. */
internal enum class CalendarDayKind(val marker: String?) {
    RECORDED("P"),
    PREDICTED_START("≈"),
    PREDICTED_DURATION("≈"),
    FERTILE("F?"),
    NONE(null);

    companion object {
        fun resolve(
            actual: Boolean,
            predictionRole: CalendarPredictionDayRole,
            fertileEstimate: Boolean,
        ): CalendarDayKind = when {
            actual -> RECORDED
            predictionRole == CalendarPredictionDayRole.START_UNCERTAINTY -> PREDICTED_START
            predictionRole == CalendarPredictionDayRole.POSSIBLE_DURATION -> PREDICTED_DURATION
            fertileEstimate -> FERTILE
            else -> NONE
        }
    }
}
