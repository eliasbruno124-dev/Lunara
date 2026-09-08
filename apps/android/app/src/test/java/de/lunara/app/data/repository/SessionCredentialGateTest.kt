package de.lunara.app.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCredentialGateTest {
    @Test
    fun `invalidation rejects an older credential response`() = runTest {
        val gate = SessionCredentialGate()
        val oldGeneration = gate.snapshot()

        gate.invalidate()

        assertFalse(gate.commitIfCurrent(oldGeneration) { true })
        assertTrue(gate.commitIfCurrent(gate.snapshot()) { true })
    }

    @Test
    fun `stale invitation refresh cannot enter its cache reset commit`() = runTest {
        val gate = SessionCredentialGate()
        val invitationGeneration = gate.snapshot()
        var cacheResetEntered = false

        gate.invalidate()

        assertFalse(gate.commitIfCurrent(invitationGeneration) {
            cacheResetEntered = true
            true
        })
        assertFalse(cacheResetEntered)
    }

    @Test
    fun `post-provision role refresh failure retains invitation proof for retry`() {
        assertFalse(
            shouldClearPendingInvitationAfterOidcFailure(
                PostProvisionFinalizationException(IllegalStateException("offline")),
            ),
        )
        assertTrue(shouldClearPendingInvitationAfterOidcFailure(IllegalStateException("cancelled")))
    }
}
