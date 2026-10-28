package de.lunara.app.ui.main

/** Presentation precedence only: dates, sharing and estimates are resolved by existing policies. */
internal enum class CalendarDayKind(val marker: String?) {
    RECORDED("P"),
    PREDICTED("≈"),
    FERTILE("F?"),
    NONE(null);

    companion object {
        fun resolve(
            actual: Boolean,
            predicted: Boolean,
            fertileEstimate: Boolean,
        ): CalendarDayKind = when {
            actual -> RECORDED
            predicted -> PREDICTED
            fertileEstimate -> FERTILE
            else -> NONE
        }
    }
}
