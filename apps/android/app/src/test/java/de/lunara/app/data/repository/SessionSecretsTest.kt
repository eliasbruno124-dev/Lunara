package de.lunara.app.data.repository

import de.lunara.app.core.security.SecretStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSecretsTest {
    @Test
    fun `late Authentik refresh cannot restore state after logout`() {
        val secrets = SessionSecrets(InMemorySecretStore())
        secrets.replaceAuthentik("https://lunara.example/", "initial")
        val beforeRefresh = secrets.authentikSnapshot()

        secrets.clearAuthentik()

        assertFalse(
            secrets.replaceAuthentikStateIfCurrent(beforeRefresh.generation, "late-refresh"),
        )
        assertNull(secrets.authentikSnapshot().baseUrl)
        assertNull(secrets.authentikSnapshot().stateJson)
    }

    @Test
    fun `current Authentik refresh replaces only serialized state`() {
        val secrets = SessionSecrets(InMemorySecretStore())
        secrets.replaceAuthentik("https://lunara.example/", "initial")
        val snapshot = secrets.authentikSnapshot()

        assertTrue(secrets.replaceAuthentikStateIfCurrent(snapshot.generation, "fresh"))
        assertEquals("https://lunara.example/", secrets.authentikSnapshot().baseUrl)
        assertEquals("fresh", secrets.authentikSnapshot().stateJson)
    }

    private class InMemorySecretStore : SecretStore {
        private val values = mutableMapOf<String, ByteArray>()

        override fun putString(key: String, value: String?) = putBytes(key, value?.encodeToByteArray())
        override fun getString(key: String): String? = getBytes(key)?.decodeToString()
        override fun putBytes(key: String, value: ByteArray?) {
            if (value == null) values.remove(key) else values[key] = value.copyOf()
            value?.fill(0)
        }
        override fun getBytes(key: String): ByteArray? = values[key]?.copyOf()
        override fun clear() = values.clear()
    }
}
