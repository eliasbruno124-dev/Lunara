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
}
