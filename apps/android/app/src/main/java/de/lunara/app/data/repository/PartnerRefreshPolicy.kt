package de.lunara.app.data.repository

import de.lunara.app.data.remote.dto.PartnerDashboardDto
import de.lunara.app.data.remote.dto.PartnerTipDto
import de.lunara.app.core.model.UserRole
import de.lunara.app.data.preferences.AppMode
import de.lunara.app.data.preferences.AppPreferencesState
import kotlinx.coroutines.CancellationException
import java.time.Instant

data class PartnerDashboardSnapshot(val dashboard: PartnerDashboardDto, val updatedAt: String)

internal data class PartnerRefreshState(
    val snapshot: PartnerDashboardSnapshot? = null,
    val unavailable: Boolean = true,
    val refreshing: Boolean = false,
)

/** Only server-filtered partner payloads are accepted; never supplement them from local logs. */
internal object PartnerRefreshPolicy {
    fun canCommit(expectedUserId: String, expectedBaseUrl: String, current: AppPreferencesState): Boolean =
        current.appMode == AppMode.SERVER && current.baseUrl == expectedBaseUrl &&
            current.session?.userId == expectedUserId && current.session.role == UserRole.PARTNER

    fun permitted(dashboard: PartnerDashboardDto): PartnerDashboardDto =
        if (dashboard.sharingPaused) PartnerDashboardDto(sharingPaused = true) else dashboard

    suspend fun withOptionalTip(
        dashboard: PartnerDashboardDto,
        fetch: suspend () -> PartnerTipDto?,
    ): PartnerDashboardDto {
        val permitted = permitted(dashboard)
        if (permitted.sharingPaused) return permitted
        return try {
            permitted.copy(tip = fetch())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A missing optional tip must never erase an otherwise successful dashboard.
            permitted
        }
    }

    fun cached(state: PartnerRefreshState, snapshot: PartnerDashboardSnapshot?): PartnerRefreshState {
        if (snapshot == null) return state.copy(snapshot = null, unavailable = true)
        val currentTime = state.snapshot?.updatedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val incomingTime = runCatching { Instant.parse(snapshot.updatedAt) }.getOrNull()
        if (currentTime != null && (incomingTime == null || incomingTime < currentTime)) return state
        return state.copy(snapshot = snapshot.copy(dashboard = permitted(snapshot.dashboard)))
    }

    fun succeeded(state: PartnerRefreshState, dashboard: PartnerDashboardDto, at: String): PartnerRefreshState =
        state.copy(snapshot = PartnerDashboardSnapshot(permitted(dashboard), at), unavailable = false, refreshing = false)

    fun failed(state: PartnerRefreshState): PartnerRefreshState = state.copy(unavailable = true, refreshing = false)
}
