package de.lunara.app.ui.main

import java.time.Clock
import java.time.LocalDate

internal object PeriodEditValidation {
    private const val MAX_DATE_LENGTH = 10

    fun sanitizeDateInput(value: String): String = value
        .filter { it.isDigit() || it == '-' }
        .take(MAX_DATE_LENGTH)

    fun parseAllowedStartDate(value: String, clock: Clock = Clock.systemDefaultZone()): LocalDate? =
        runCatching { LocalDate.parse(value) }
            .getOrNull()
            ?.takeUnless { it.isAfter(LocalDate.now(clock)) }
}
