package de.lunara.app.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayProbePolicyTest {
    @Test
    fun `authentik and legacy health probes are accepted`() {
        assertTrue(gatewayProbeAccepted(200, "ok", "authentik"))
        assertTrue(gatewayProbeAccepted(200, "ok", null))
    }

    @Test
    fun `redirects errors malformed bodies and direct mode are rejected`() {
        assertFalse(gatewayProbeAccepted(302, "ok", "authentik"))
        assertFalse(gatewayProbeAccepted(401, "ok", "authentik"))
        assertFalse(gatewayProbeAccepted(403, "ok", "authentik"))
        assertFalse(gatewayProbeAccepted(200, null, "authentik"))
        assertFalse(gatewayProbeAccepted(200, "<html>", "authentik"))
        assertFalse(gatewayProbeAccepted(200, "ready", "authentik"))
        assertFalse(gatewayProbeAccepted(200, "ok", "direct"))
        assertFalse(gatewayProbeAccepted(200, "ok", "unknown"))
    }
}
