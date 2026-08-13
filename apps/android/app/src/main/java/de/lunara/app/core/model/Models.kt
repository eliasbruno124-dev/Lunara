package de.lunara.app.core.model

import java.time.LocalDate

enum class UserRole { TRACKER, PARTNER }

enum class MainDestination { TODAY, CALENDAR, HISTORY, SUPPORT, SETTINGS }

object RoleNavigation {
    fun destinations(role: UserRole): List<MainDestination> = listOf(
        MainDestination.TODAY,
        MainDestination.CALENDAR,
        if (role == UserRole.TRACKER) MainDestination.HISTORY else MainDestination.SUPPORT,
        MainDestination.SETTINGS,
    )

    fun canCreatePeriod(role: UserRole): Boolean = canManagePeriods(role)

    fun canManagePeriods(role: UserRole): Boolean = role == UserRole.TRACKER
}

data class AppSession(
    val userId: String,
    val displayName: String,
    val role: UserRole,
    val serverName: String,
    val serverVersion: String,
)

data class PredictionSummary(
    val from: LocalDate,
    val to: LocalDate,
    val confidence: String,
    val explanation: String,
)
