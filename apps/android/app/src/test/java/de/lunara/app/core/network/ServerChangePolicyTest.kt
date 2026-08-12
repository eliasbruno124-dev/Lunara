package de.lunara.app.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerChangePolicyTest {
    @Test
    fun `first connection and unchanged base need no destructive confirmation`() {
        assertFalse(ServerChangePolicy.requiresConfirmation(null, "https://lunara.example/"))
        assertFalse(
            ServerChangePolicy.requiresConfirmation(
                "https://lunara.example/",
                "https://lunara.example/",
            ),
        )
    }

    @Test
    fun `different normalized base requires confirmation`() {
        assertTrue(
            ServerChangePolicy.requiresConfirmation(
                "https://old.example/",
                "https://new.example/",
            ),
        )
    }
}
