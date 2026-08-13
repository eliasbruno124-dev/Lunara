package de.lunara.app.ui.main

import java.time.Clock
import java.time.LocalDate

internal enum class PeriodDateValidationError {
    INVALID_START,
    INVALID_END,
    END_BEFORE_START,
}

internal data class ValidPeriodDates(
    val startDate: LocalDate,
    val endDate: LocalDate?,
)

internal sealed interface PeriodDateValidationResult {
    data class Valid(val dates: ValidPeriodDates) : PeriodDateValidationResult
    data class Invalid(val error: PeriodDateValidationError) : PeriodDateValidationResult
}

internal object PeriodEditValidation {
    private const val MAX_DATE_LENGTH = 10

    fun sanitizeDateInput(value: String): String = value
        .filter { it.isDigit() || it == '-' }
        .take(MAX_DATE_LENGTH)

    fun parseAllowedStartDate(value: String, clock: Clock = Clock.systemDefaultZone()): LocalDate? =
        runCatching { LocalDate.parse(value) }
            .getOrNull()
            ?.takeUnless { it.isAfter(LocalDate.now(clock)) }

    fun validateRange(
        startValue: String,
        endValue: String,
        clock: Clock = Clock.systemDefaultZone(),
    ): PeriodDateValidationResult {
        val today = LocalDate.now(clock)
        val start = parseDateNotAfter(startValue, today)
            ?: return PeriodDateValidationResult.Invalid(PeriodDateValidationError.INVALID_START)
        val end = if (endValue.isBlank()) {
            null
        } else {
            parseDateNotAfter(endValue, today)
                ?: return PeriodDateValidationResult.Invalid(PeriodDateValidationError.INVALID_END)
        }
        if (end != null && end.isBefore(start)) {
            return PeriodDateValidationResult.Invalid(PeriodDateValidationError.END_BEFORE_START)
        }
        return PeriodDateValidationResult.Valid(ValidPeriodDates(start, end))
    }

    private fun parseDateNotAfter(value: String, latest: LocalDate): LocalDate? =
        runCatching { LocalDate.parse(value) }
            .getOrNull()
            ?.takeUnless { it.isAfter(latest) }
}
