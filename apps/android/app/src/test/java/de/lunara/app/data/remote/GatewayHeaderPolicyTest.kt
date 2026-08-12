package de.lunara.app.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayHeaderPolicyTest {
    @Test
    fun `attaches basic header before Authentik can redirect`() {
        assertTrue(GatewayHeaderPolicy.shouldAttach(true, false, null))
    }

    @Test
    fun `valid Authentik cookie suppresses repeated basic credentials`() {
        assertFalse(GatewayHeaderPolicy.shouldAttach(true, true, null))
    }

    @Test
    fun `never overwrites an existing authorization header`() {
        assertFalse(GatewayHeaderPolicy.shouldAttach(true, false, "Bearer token"))
        assertFalse(GatewayHeaderPolicy.shouldAttach(false, false, null))
    }
}
