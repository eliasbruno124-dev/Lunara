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

    @Test
    fun `provider rotation and destination survive a process restart together`() {
        val storage = InMemorySecretStore()
        val first = SessionSecrets(storage)
        first.replaceAuthentik("https://lunara.example/", "synthetic-original")
        assertTrue(first.replaceAuthentikStateIfCurrent(first.authentikSnapshot().generation, "synthetic-rotated"))

        val restarted = SessionSecrets(storage).authentikSnapshot()
        assertEquals("https://lunara.example/", restarted.baseUrl)
        assertEquals("synthetic-rotated", restarted.stateJson)
        assertNull(storage.getString("gateway.authentik_state.v1"))
        assertNull(storage.getString("gateway.authentik_base_url.v1"))
    }

    @Test
    fun `legacy provider state migrates without requiring another login`() {
        val storage = InMemorySecretStore()
        storage.putString("gateway.authentik_base_url.v1", "https://lunara.example/")
        storage.putString("gateway.authentik_state.v1", "synthetic-legacy")

        val secrets = SessionSecrets(storage)
        val legacy = secrets.authentikSnapshot()
        assertEquals("synthetic-legacy", legacy.stateJson)
        assertNull(storage.getString("gateway.authentik_session.v2"))
        assertTrue(secrets.replaceAuthentikStateIfCurrent(legacy.generation, requireNotNull(legacy.stateJson)))
        assertEquals("synthetic-legacy", SessionSecrets(storage).authentikSnapshot().stateJson)
        assertNull(storage.getString("gateway.authentik_state.v1"))
        assertNull(storage.getString("gateway.authentik_base_url.v1"))
    }

    @Test
    fun `interrupted logout cannot resurrect old migration sources`() {
        val storage = InMemorySecretStore()
        storage.putString("gateway.authentik_base_url.v1", "https://lunara.example/")
        storage.putString("gateway.authentik_state.v1", "synthetic-legacy")
        storage.failNextKey = "gateway.authentik_state.v1"
        assertTrue(runCatching { SessionSecrets(storage).clearAuthentik() }.isFailure)

        val restarted = SessionSecrets(storage).authentikSnapshot()
        assertNull(restarted.baseUrl)
        assertNull(restarted.stateJson)
    }

    @Test
    fun `interrupted account replacement never combines old state with new destination`() {
        val storage = InMemorySecretStore()
        val secrets = SessionSecrets(storage)
        secrets.replaceAuthentik("https://first.example/", "synthetic-first")
        storage.failNextKey = "gateway.authentik_session.v2"
        assertTrue(runCatching {
            secrets.replaceAuthentik("https://second.example/", "synthetic-second")
        }.isFailure)

        val restarted = SessionSecrets(storage).authentikSnapshot()
        assertEquals("https://first.example/", restarted.baseUrl)
        assertEquals("synthetic-first", restarted.stateJson)
    }

    @Test
    fun `corrupt new record fails closed rather than restoring legacy credentials`() {
        val storage = InMemorySecretStore()
        storage.putString("gateway.authentik_base_url.v1", "https://lunara.example/")
        storage.putString("gateway.authentik_state.v1", "synthetic-legacy")
        storage.putString("gateway.authentik_session.v2", "malformed-record")
        assertNull(SessionSecrets(storage).authentikSnapshot().stateJson)
    }

    @Test
    fun `unreadable logout tombstone never restores surviving legacy credentials`() {
        val storage = InMemorySecretStore()
        storage.putString("gateway.authentik_base_url.v1", "https://lunara.example/")
        storage.putString("gateway.authentik_state.v1", "synthetic-legacy")
        storage.failNextKey = "gateway.authentik_state.v1"
        assertTrue(runCatching { SessionSecrets(storage).clearAuthentik() }.isFailure)
        storage.unreadableKeys += "gateway.authentik_session.v2"

        assertTrue(storage.contains("gateway.authentik_session.v2"))
        assertNull(storage.getString("gateway.authentik_session.v2"))
        assertEquals("synthetic-legacy", storage.getString("gateway.authentik_state.v1"))
        val restarted = SessionSecrets(storage)
        val snapshot = restarted.authentikSnapshot()
        assertNull(snapshot.baseUrl)
        assertNull(snapshot.stateJson)
        assertFalse(restarted.replaceAuthentikStateIfCurrent(snapshot.generation, "synthetic-late-refresh"))
    }

    @Test
    fun `absent new record still permits valid legacy migration`() {
        val storage = InMemorySecretStore()
        storage.putString("gateway.authentik_base_url.v1", "https://lunara.example/")
        storage.putString("gateway.authentik_state.v1", "synthetic-legacy")
        assertFalse(storage.contains("gateway.authentik_session.v2"))
        assertNull(storage.getString("gateway.authentik_session.v2"))
        val secrets = SessionSecrets(storage)
        val snapshot = secrets.authentikSnapshot()
        assertEquals("synthetic-legacy", snapshot.stateJson)
        assertTrue(secrets.replaceAuthentikStateIfCurrent(snapshot.generation, "synthetic-refreshed"))
        assertTrue(storage.contains("gateway.authentik_session.v2"))
        assertEquals("synthetic-refreshed", SessionSecrets(storage).authentikSnapshot().stateJson)
    }

    @Test
    fun `identical retry commits rotation after failed write updated only memory`() {
        val storage = InMemorySecretStore()
        val secrets = SessionSecrets(storage)
        secrets.replaceAuthentik("https://lunara.example/", "synthetic-original")
        val generation = secrets.authentikSnapshot().generation
        val originalDurableRecord = storage.durableString("gateway.authentik_session.v2")
        storage.failAfterMemoryNextKey = "gateway.authentik_session.v2"

        assertTrue(runCatching {
            secrets.replaceAuthentikStateIfCurrent(generation, "synthetic-rotated")
        }.isFailure)
        assertEquals("synthetic-rotated", secrets.authentikSnapshot().stateJson)
        assertEquals(originalDurableRecord, storage.durableString("gateway.authentik_session.v2"))

        assertTrue(secrets.replaceAuthentikStateIfCurrent(generation, "synthetic-rotated"))
        assertNotEquals(originalDurableRecord, storage.durableString("gateway.authentik_session.v2"))
        assertEquals(storage.getString("gateway.authentik_session.v2"),
            storage.durableString("gateway.authentik_session.v2"))
        val writesAfterRecovery = storage.writeAttempts
        assertTrue(secrets.replaceAuthentikStateIfCurrent(generation, "synthetic-rotated"))
        assertEquals(writesAfterRecovery, storage.writeAttempts)
    }

    @Test
    fun `repeated failed identical commits never report renewal success`() {
        val storage = InMemorySecretStore()
        val secrets = SessionSecrets(storage)
        secrets.replaceAuthentik("https://lunara.example/", "synthetic-original")
        val generation = secrets.authentikSnapshot().generation
        val originalDurableRecord = storage.durableString("gateway.authentik_session.v2")
        repeat(2) {
            storage.failAfterMemoryNextKey = "gateway.authentik_session.v2"
            assertTrue(runCatching {
                secrets.replaceAuthentikStateIfCurrent(generation, "synthetic-rotated")
            }.isFailure)
            assertEquals(originalDurableRecord, storage.durableString("gateway.authentik_session.v2"))
        }
        assertTrue(secrets.replaceAuthentikStateIfCurrent(generation, "synthetic-rotated"))
        assertNotEquals(originalDurableRecord, storage.durableString("gateway.authentik_session.v2"))
    }

    @Test
    fun `rotation request UUID cannot escape a failed memory-only persistence retry`() {
        val storage = InMemorySecretStore()
        val secrets = SessionSecrets(storage)
        repeat(2) {
            storage.failAfterMemoryNextKey = "session.refresh_request.v1"
            assertTrue(runCatching { secrets.refreshRequestIdFor("synthetic-refresh") }.isFailure)
            assertTrue(storage.contains("session.refresh_request.v1"))
            assertNull(storage.durableString("session.refresh_request.v1"))
        }
        val request = secrets.refreshRequestIdFor("synthetic-refresh")
        assertEquals(storage.getString("session.refresh_request.v1"),
            storage.durableString("session.refresh_request.v1"))
        assertEquals(request, SessionSecrets(storage).refreshRequestIdFor("synthetic-refresh"))
        val writesAfterRecovery = storage.writeAttempts
        assertEquals(request, secrets.refreshRequestIdFor("synthetic-refresh"))
        assertEquals(writesAfterRecovery, storage.writeAttempts)
    }

    @Test
    fun `old account refresh cannot overwrite a newer account on the same server`() {
        val secrets = SessionSecrets(InMemorySecretStore())
        secrets.replaceAuthentik("https://lunara.example/", "synthetic-account-a")
        val accountA = secrets.authentikSnapshot()
        secrets.replaceAuthentik("https://lunara.example/", "synthetic-account-b")

        assertFalse(secrets.replaceAuthentikStateIfCurrent(accountA.generation, "synthetic-a-rotated"))
        assertEquals("synthetic-account-b", secrets.authentikSnapshot().stateJson)
        assertNotEquals(accountA.generation, secrets.authentikSnapshot().generation)
    }

    private class InMemorySecretStore : SecretStore {
        private val values = mutableMapOf<String, ByteArray>()
        private val durableValues = mutableMapOf<String, ByteArray>()
        var failNextKey: String? = null
        var failAfterMemoryNextKey: String? = null
        val unreadableKeys = mutableSetOf<String>()
        var writeAttempts = 0
            private set

        fun durableString(key: String): String? = durableValues[key]?.decodeToString()

        override fun contains(key: String): Boolean = values.containsKey(key)
        override fun putString(key: String, value: String?) = putBytes(key, value?.encodeToByteArray())
        override fun getString(key: String): String? = getBytes(key)?.decodeToString()
        override fun putBytes(key: String, value: ByteArray?) {
            writeAttempts++
            if (failNextKey == key) {
                failNextKey = null
                throw IllegalStateException("Synthetic interrupted persistence")
            }
            if (value == null) values.remove(key) else values[key] = value.copyOf()
            unreadableKeys.remove(key)
            if (failAfterMemoryNextKey == key) {
                failAfterMemoryNextKey = null
                value?.fill(0)
                throw IllegalStateException("Synthetic disk failure after process-visible update")
            }
            if (value == null) durableValues.remove(key) else durableValues[key] = value.copyOf()
            value?.fill(0)
        }
        override fun getBytes(key: String): ByteArray? =
            if (key in unreadableKeys) null else values[key]?.copyOf()
        override fun clear() {
            values.clear()
            durableValues.clear()
            unreadableKeys.clear()
        }
    }
}
