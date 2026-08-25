package de.lunara.app.data.remote

import de.lunara.app.core.network.GatewayCredentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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

    @Test
    fun `personal Authentik bearer wins over legacy shared basic credentials`() {
        val value = GatewayAuthorizationPolicy.value(
            GatewayCredentials("legacy-app", "legacy-secret"),
            "personal-token",
        )
        assertEquals("Bearer personal-token", value)
    }

    @Test
    fun `blank bearer safely falls back to basic credentials`() {
        val value = GatewayAuthorizationPolicy.value(
            GatewayCredentials("legacy-app", "legacy-secret"),
            "  ",
        )
        assertTrue(value?.startsWith("Basic ") == true)
    }
}

class BlockingTokenBridgePolicyTest {
    @Test
    fun rejectsMainThreadBridging() {
        assertThrows(IllegalStateException::class.java) {
            BlockingTokenBridgePolicy.requireBackgroundThread(true)
        }
    }

    @Test
    fun permitsOkHttpWorkerThreadBridging() {
        BlockingTokenBridgePolicy.requireBackgroundThread(false)
    }
}

class AppTokenHeaderPolicyTest {
    @Test
    fun neverSendsInnerTokenToAbsoluteForeignUrl() {
        assertFalse(AppTokenHeaderPolicy.shouldAttach(true, false, null))
    }

    @Test
    fun neverOverwritesCallerHeader() {
        assertFalse(AppTokenHeaderPolicy.shouldAttach(true, true, "Bearer caller"))
    }

    @Test
    fun sendsInnerTokenOnlyToConfiguredOrigin() {
        assertTrue(AppTokenHeaderPolicy.shouldAttach(true, true, null))
        assertFalse(AppTokenHeaderPolicy.shouldAttach(false, true, null))
    }
}
