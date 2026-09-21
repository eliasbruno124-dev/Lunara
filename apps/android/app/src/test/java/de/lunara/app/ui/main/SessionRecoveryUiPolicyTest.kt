package de.lunara.app.ui.main

import de.lunara.app.data.preferences.AppMode
import de.lunara.app.data.repository.ApiException
import de.lunara.app.data.repository.SessionRecoveryCancelledException
import org.junit.Assert.*
import org.junit.Test

class SessionRecoveryUiPolicyTest {
    @Test fun `only rejected server sessions show recovery banner`() {
        assertTrue(SessionRecoveryUiPolicy.showBanner(AppMode.SERVER, "owner", required = true))
        assertFalse(SessionRecoveryUiPolicy.showBanner(AppMode.SERVER, "owner", required = false))
        assertFalse(SessionRecoveryUiPolicy.showBanner(AppMode.LOCAL, "owner", required = true))
        assertFalse(SessionRecoveryUiPolicy.showBanner(AppMode.SERVER, null, required = true))
    }

    @Test fun `settings renewal remains available before recovery is flagged`() {
        assertTrue(SessionRecoveryUiPolicy.canOffer(AppMode.SERVER, "legacy-owner"))
        assertTrue(SessionRecoveryUiPolicy.canStart(AppMode.SERVER, "legacy-owner", SessionRecoveryStatus.IDLE))
        assertFalse(SessionRecoveryUiPolicy.canStart(AppMode.LOCAL, "local-owner", SessionRecoveryStatus.IDLE))
        assertFalse(SessionRecoveryUiPolicy.canStart(AppMode.SERVER, null, SessionRecoveryStatus.IDLE))
    }

    @Test fun `active submissions and open browsers reject duplicate launches`() {
        listOf(SessionRecoveryStatus.PREPARING, SessionRecoveryStatus.SUBMITTING, SessionRecoveryStatus.WAITING_BROWSER).forEach {
            assertFalse(SessionRecoveryUiPolicy.canStart(AppMode.SERVER, "owner", it))
        }
        assertTrue(SessionRecoveryStatus.PREPARING.busy)
        assertTrue(SessionRecoveryStatus.SUBMITTING.busy)
        assertFalse(SessionRecoveryStatus.WAITING_BROWSER.busy)
    }

    @Test fun `cancel unlocks retry while wrong-account errors stay distinguishable`() {
        val cancelled = SessionRecoveryUiPolicy.failure(SessionRecoveryCancelledException())
        val wrongAccount = SessionRecoveryUiPolicy.failure(ApiException(409, "recovery_identity_mismatch"))
        val badPassword = SessionRecoveryUiPolicy.failure(ApiException(401, "invalid_credentials"))
        assertEquals(SessionRecoveryStatus.CANCELLED, cancelled)
        assertEquals(SessionRecoveryStatus.WRONG_ACCOUNT, wrongAccount)
        assertEquals(SessionRecoveryStatus.FAILED, badPassword)
        listOf(cancelled, wrongAccount, badPassword).forEach {
            assertFalse(it.busy)
            assertTrue(SessionRecoveryUiPolicy.canStart(AppMode.SERVER, "owner", it))
        }
        assertEquals(SessionRecoveryStatus.FAILED, SessionRecoveryUiPolicy.failure(ApiException(409, "unrelated_conflict")))
    }

    @Test fun `missing browser callback can be dismissed and retried without accepting a late callback`() {
        assertTrue(SessionRecoveryUiPolicy.canCompleteBrowser(SessionRecoveryStatus.WAITING_BROWSER))
        val dismissed = SessionRecoveryUiPolicy.dismissed(SessionRecoveryStatus.WAITING_BROWSER)
        assertEquals(SessionRecoveryStatus.IDLE, dismissed)
        assertTrue(SessionRecoveryUiPolicy.canStart(AppMode.SERVER, "owner", dismissed))
        assertFalse(SessionRecoveryUiPolicy.canCompleteBrowser(dismissed))
        assertFalse(SessionRecoveryUiPolicy.canCompleteBrowser(SessionRecoveryStatus.SUCCESS))
        assertEquals(SessionRecoveryStatus.SUBMITTING, SessionRecoveryUiPolicy.dismissed(SessionRecoveryStatus.SUBMITTING))
    }
}
