package de.lunara.app.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayHeaderPolicyTest {
    @Test
    fun `attaches basic header before Authentik can redirect`() {
        assertTrue(GatewayHeaderPolicy.shouldAttach(true, true, null))
    }

    @Test
    fun `never attaches credentials outside the configured server origin`() {
        val origin = GatewayOrigin.fromBaseUrl("https://lunara.example.de/instance/")
        assertTrue(origin.matches("https://lunara.example.de/api/v1/meta".toHttpUrl()))
        assertFalse(origin.matches("https://authentik.example.de/api/v1/meta".toHttpUrl()))
        assertFalse(origin.matches("http://lunara.example.de/api/v1/meta".toHttpUrl()))
        assertFalse(origin.matches("https://lunara.example.de:8443/api/v1/meta".toHttpUrl()))
    }

    @Test
    fun `never overwrites an existing authorization header`() {
        assertFalse(GatewayHeaderPolicy.shouldAttach(true, true, "Bearer token"))
        assertFalse(GatewayHeaderPolicy.shouldAttach(false, true, null))
        assertFalse(GatewayHeaderPolicy.shouldAttach(true, false, null))
    }
}
