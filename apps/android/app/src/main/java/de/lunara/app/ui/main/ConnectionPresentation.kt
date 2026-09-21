package de.lunara.app.ui.main

import de.lunara.app.data.preferences.AppMode
import java.net.URI
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import de.lunara.app.data.remote.dto.PredictionDto

internal enum class SyncBadge { LOCAL, AUTHENTICATION, CONFLICT, PENDING, SERVER }

internal object ConnectionPresentation {
    // A successful past request does NOT prove the server is reachable now.
    fun badge(mode: AppMode, authenticationRequired: Boolean, conflicts: Int, pending: Int): SyncBadge = when {
        mode == AppMode.LOCAL -> SyncBadge.LOCAL
        authenticationRequired -> SyncBadge.AUTHENTICATION
        conflicts > 0 -> SyncBadge.CONFLICT
        pending > 0 -> SyncBadge.PENDING
        else -> SyncBadge.SERVER
    }

    fun version(value: String): String = value.trim().ifEmpty { "—" }

    fun predictionWindowDays(prediction: PredictionDto?): Int? = runCatching {
        if (prediction?.available != true) return null
        val days = ChronoUnit.DAYS.between(LocalDate.parse(prediction.predictedStartFrom), LocalDate.parse(prediction.predictedStartTo)) + 1
        days.toInt().takeIf { it in 1..181 }
    }.getOrNull()
}

internal object DonationLinkPolicy {
    fun safeUrl(configured: String): String? = runCatching {
        val uri = URI(configured)
        configured.takeIf {
            uri.scheme == "https" && uri.host == "paypal.me" && uri.userInfo == null &&
                uri.port == -1 && uri.query == null && uri.fragment == null &&
                uri.path.matches(Regex("/[A-Za-z0-9]{1,40}/?"))
        }
    }.getOrNull()
}
