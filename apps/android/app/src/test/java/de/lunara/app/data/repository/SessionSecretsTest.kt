package de.lunara.app.data.repository

import de.lunara.app.core.security.SecretStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSecretsTest {
    @Test
    fun `rotation request persists across restart and uncertain response loss`() {
        val storage = InMemorySecretStore()
        val firstProcess = SessionSecrets(storage)
        val request = firstProcess.refreshRequestIdFor("synthetic-refresh-before-rotation")
        val restarted = SessionSecrets(storage)
        assertEquals(request, restarted.refreshRequestIdFor("synthetic-refresh-before-rotation"))
        assertNotEquals(request, restarted.refreshRequestIdFor("synthetic-new-refresh"))
    }

    @Test
    fun `logout and completed recovery discard old retry request`() {
        val secrets = SessionSecrets(InMemorySecretStore())
        val first = secrets.refreshRequestIdFor("synthetic-token")
        secrets.clearRefreshRequestId()
        val second = secrets.refreshRequestIdFor("synthetic-token")
        assertNotEquals(first, second)
        secrets.clearAccountSecrets()
        assertNotEquals(second, secrets.refreshRequestIdFor("synthetic-token"))
    }

    @Test fun `logout clears private relationship snapshot`() {
        val secrets = SessionSecrets(InMemorySecretStore())
        secrets.relationshipJson = "synthetic-relationship"
        secrets.clearAccountSecrets()
        assertNull(secrets.relationshipJson)
    }

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
