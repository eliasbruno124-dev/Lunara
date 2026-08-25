package de.lunara.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OidcRedirectPolicyTest {
    @Test
    fun `production build uses its configured verified HTTPS callback`() {
        val redirect = OidcRedirectPolicy.select(
            packageName = "de.lunara.app",
            verifiedAppLinkEnabled = true,
            appLinkHost = "LUNARA.EXAMPLE",
        )

        assertEquals("https://lunara.example/auth/android-callback", redirect)
    }

    @Test
    fun `explicit development build uses its exact package scheme`() {
        val redirect = OidcRedirectPolicy.select(
            packageName = "de.lunara.app.local.debug",
            verifiedAppLinkEnabled = false,
            appLinkHost = "lunara.example",
        )

        assertEquals("de.lunara.app.local.debug:/oauth2redirect", redirect)
    }

    @Test
    fun `only the exact build callbacks are accepted`() {
        assertFalse(
            OidcRedirectPolicy.isAllowed(
                "de.lunara.app:/oauth2redirect",
                "de.lunara.app",
                true,
                "lunara.example",
            ),
        )
        assertTrue(
            OidcRedirectPolicy.isAllowed(
                "https://lunara.example/auth/android-callback",
                "de.lunara.app",
                true,
                "lunara.example",
            ),
        )
        assertFalse(
            OidcRedirectPolicy.isAllowed(
                "https://attacker.example/auth/android-callback",
                "de.lunara.app",
                true,
                "lunara.example",
            ),
        )
        assertTrue(
            OidcRedirectPolicy.isAllowed(
                "de.lunara.app.local.debug:/oauth2redirect",
                "de.lunara.app.local.debug",
                false,
                "lunara.example",
            ),
        )
        assertFalse(
            OidcRedirectPolicy.isAllowed(
                "de.lunara.other:/oauth2redirect",
                "de.lunara.app",
                true,
                "lunara.example",
            ),
        )
    }
}
