package de.lunara.app.data.repository

import de.lunara.app.core.model.AppSession
import de.lunara.app.core.model.UserRole
import de.lunara.app.data.preferences.AppMode
import de.lunara.app.data.preferences.AppPreferencesState
import de.lunara.app.data.remote.dto.PartnerDashboardDto
import de.lunara.app.data.remote.dto.PartnerTipDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PartnerRefreshPolicyTest {
    private val permitted = PartnerDashboardDto(periodDates = listOf(mapOf("startDate" to "2026-08-01")))
    private val before = "2026-09-21T08:00:00Z"
    private val after = "2026-09-21T09:00:00Z"

    @Test fun `tip failure keeps successful permitted dashboard and never substitutes local records`() = runBlocking {
        val result = PartnerRefreshPolicy.withOptionalTip(permitted) { throw IOException("tip offline") }
        assertEquals(permitted, result)
        assertNull(result.todayLog)
        assertNull(result.phase)
        assertNull(result.tip)
    }

    @Test fun `successful tip enriches only server permitted data`() = runBlocking {
        val tip = PartnerTipDto("tip", "Support", "Ask first", "Listen", "COMMUNICATION")
        val result = PartnerRefreshPolicy.withOptionalTip(permitted) { tip }
        assertEquals(tip, result.tip)
        assertEquals(permitted.periodDates, result.periodDates)
        assertNull(result.todayLog)
    }

    @Test fun `cancelled tip request propagates cancellation`() = runBlocking {
        val cancellation = CancellationException("session ended")
        try {
            PartnerRefreshPolicy.withOptionalTip(permitted) { throw cancellation }
            fail("Cancellation must not be converted to success")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }

    @Test fun `paused sharing removes period symptom phase and tip fields without fetching tips`() = runBlocking {
        var requested = false
        val result = PartnerRefreshPolicy.withOptionalTip(
            permitted.copy(sharingPaused = true, todayLog = mapOf("symptoms" to listOf("pain")), phase = "LUTEAL"),
        ) { requested = true; PartnerTipDto("tip", "Private phase", "Detail", "Act", "PHASE") }
        assertEquals(PartnerDashboardDto(sharingPaused = true), result)
        assertFalse(requested)
    }

    @Test fun `failed refresh retains last success while showing stale warning and enabling retry`() {
        val successful = PartnerRefreshPolicy.succeeded(PartnerRefreshState(), permitted, before)
        val failed = PartnerRefreshPolicy.failed(successful.copy(refreshing = true))
        assertEquals(successful.snapshot, failed.snapshot)
        assertTrue(failed.unavailable)
        assertFalse(failed.refreshing)
        val cachedEmission = PartnerRefreshPolicy.cached(failed, failed.snapshot)
        assertTrue(cachedEmission.unavailable)
        val retried = PartnerRefreshPolicy.succeeded(cachedEmission.copy(refreshing = true), PartnerDashboardDto(), after)
        assertFalse(retried.unavailable)
        assertFalse(retried.refreshing)
        assertEquals(after, retried.snapshot?.updatedAt)
        assertTrue(retried.snapshot!!.dashboard.periodDates.isEmpty())
    }

    @Test fun `initial cache is unconfirmed and old emissions cannot undo newer sharing response`() {
        val cached = PartnerRefreshPolicy.cached(PartnerRefreshState(), PartnerDashboardSnapshot(permitted, before))
        assertTrue(cached.unavailable)
        assertEquals(before, cached.snapshot?.updatedAt)
        val fresh = PartnerRefreshPolicy.succeeded(cached, PartnerDashboardDto(sharingPaused = true), after)
        val delayed = PartnerRefreshPolicy.cached(fresh, cached.snapshot)
        assertEquals(fresh, delayed)
        assertTrue(delayed.snapshot!!.dashboard.periodDates.isEmpty())
    }

    @Test fun `uncached refresh failure does not fabricate health data or last success`() {
        val failed = PartnerRefreshPolicy.failed(PartnerRefreshState(refreshing = true))
        assertNull(failed.snapshot)
        assertTrue(failed.unavailable)
        assertFalse(failed.refreshing)
    }

    @Test fun `cache commit is scoped to original server partner identity and server mode`() {
        val original = AppPreferencesState(
            baseUrl = "https://example.test/",
            session = AppSession("partner", "Partner", UserRole.PARTNER, "Lunara", "1"),
        )
        fun accepts(state: AppPreferencesState) = PartnerRefreshPolicy.canCommit("partner", "https://example.test/", state)
        assertTrue(accepts(original))
        assertFalse(accepts(original.copy(session = null)))
        assertFalse(accepts(original.copy(session = original.session!!.copy(userId = "other"))))
        assertFalse(accepts(original.copy(session = original.session!!.copy(role = UserRole.TRACKER))))
        assertFalse(accepts(original.copy(baseUrl = "https://another.test/")))
        assertFalse(accepts(original.copy(appMode = AppMode.LOCAL)))
    }
}
