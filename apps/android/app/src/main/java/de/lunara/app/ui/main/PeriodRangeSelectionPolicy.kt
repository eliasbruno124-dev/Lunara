package de.lunara.app.ui.main

import java.time.LocalDate

/** Current start/end selection for the calendar's two-tap period range picker. */
internal data class PeriodRangeSelection(
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
) {
    val isComplete: Boolean
        get() = startDate != null && endDate != null

    /** ISO values can be passed directly to rememberSaveable or SavedStateHandle. */
    val savedStartDate: String?
        get() = startDate?.toString()

    val savedEndDate: String?
        get() = endDate?.toString()
}

internal object PeriodRangeSelectionPolicy {
    /**
     * Selects a range without a date text field:
     * - the first tap chooses the start;
     * - a later or equal second tap chooses the end;
     * - an earlier second tap moves the start;
     * - any tap after a completed range starts a fresh range.
     */
    fun select(
        current: PeriodRangeSelection,
        tappedDate: LocalDate,
    ): PeriodRangeSelection {
        val start = current.startDate
        if (start == null || current.endDate != null) {
            return PeriodRangeSelection(startDate = tappedDate)
        }
        if (tappedDate.isBefore(start)) {
            return PeriodRangeSelection(startDate = tappedDate)
        }
        return PeriodRangeSelection(startDate = start, endDate = tappedDate)
    }

    /** Restores only a valid ISO-8601 range; malformed state safely becomes empty. */
    fun restore(
        savedStartDate: String?,
        savedEndDate: String?,
    ): PeriodRangeSelection {
        if (savedStartDate == null) return PeriodRangeSelection()
        val start = savedStartDate.toLocalDateOrNull() ?: return PeriodRangeSelection()
        if (savedEndDate == null) return PeriodRangeSelection(startDate = start)
        val end = savedEndDate.toLocalDateOrNull() ?: return PeriodRangeSelection()
        if (end.isBefore(start)) return PeriodRangeSelection()
        return PeriodRangeSelection(startDate = start, endDate = end)
    }

    private fun String.toLocalDateOrNull(): LocalDate? =
        runCatching { LocalDate.parse(this) }.getOrNull()
}
