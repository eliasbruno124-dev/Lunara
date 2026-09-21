package de.lunara.app.data.repository

import de.lunara.app.core.model.AppSession
import de.lunara.app.core.model.UserRole
import de.lunara.app.data.preferences.AppMode
import de.lunara.app.data.preferences.AppPreferencesState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRecoveryPolicyTest {
    private val original = RecoveryIdentity("https://lunara.example/app/", "saved-user", UserRole.TRACKER)
    private fun allowed(returned: RecoveryIdentity = original, current: RecoveryIdentity? = original,
                        generation: Long = 7, cancelled: Boolean = false) =
        SessionRecoveryPolicy.canCommit(original, current, returned, 7, generation, cancelled)

    @Test fun `same account and normalized server may recover`() {
        val normalized = SessionRecoveryPolicy.normalizedBaseUrl("HTTPS://LUNARA.EXAMPLE:443/app")
        assertEquals(original.baseUrl, normalized)
        assertTrue(allowed(original.copy(baseUrl = normalized!!)))
    }

    @Test fun `different server port or prefix is not the same vault`() {
        listOf("https://other.example/app/", "http://lunara.example/app/",
            "https://lunara.example:444/app/", "https://lunara.example/other/").forEach {
            assertFalse(allowed(original.copy(baseUrl = SessionRecoveryPolicy.normalizedBaseUrl(it)!!)))
        }
        assertFalse(SessionRecoveryPolicy.normalizedBaseUrl("https://lunara.example/a%2Fb/") ==
            SessionRecoveryPolicy.normalizedBaseUrl("https://lunara.example/a/b/"))
    }

    @Test fun `user IDs are exact and role cannot change during recovery`() {
        assertFalse(allowed(original.copy(userId = "another-user")))
        assertFalse(allowed(original.copy(userId = "SAVED-USER")))
        assertFalse(allowed(original.copy(role = UserRole.PARTNER)))
    }

    @Test fun `matching response cannot overwrite a changed local scope`() {
        assertFalse(allowed(current = null))
        assertFalse(allowed(current = original.copy(userId = "other")))
        assertFalse(allowed(current = original.copy(role = UserRole.PARTNER)))
        assertFalse(allowed(current = original.copy(baseUrl = "https://other.example/")))
        assertFalse(allowed(generation = 8))
        assertFalse(allowed(cancelled = true))
    }

    @Test fun `local-only vault and malformed servers have no recovery identity`() {
        val state = AppPreferencesState(baseUrl = original.baseUrl,
            session = AppSession(original.userId, "Name", original.role, "Lunara", "1"))
        assertEquals(original, SessionRecoveryPolicy.identity(state))
        assertNull(SessionRecoveryPolicy.identity(state.copy(appMode = AppMode.LOCAL)))
        assertNull(SessionRecoveryPolicy.identity(state.copy(session = null)))
        listOf("https://user:password@lunara.example/", "https://lunara.example/?key=value",
            "https://lunara.example/#fragment", "file:///app", "bad").forEach {
            assertNull(SessionRecoveryPolicy.normalizedBaseUrl(it))
        }
    }

    @Test fun `persisted recovery marker cannot leak to another account server role or local mode`() {
        fun marked(base: String? = original.baseUrl, user: String? = original.userId,
                   role: UserRole? = original.role, mode: AppMode = AppMode.SERVER) =
            SessionRecoveryPolicy.markerMatches(mode, base, user, role,
                original.baseUrl, original.userId, original.role.name)
        assertTrue(marked())
        assertFalse(marked(base = "https://other.example/"))
        assertFalse(marked(base = null))
        assertFalse(marked(user = "other"))
        assertFalse(marked(user = null))
        assertFalse(marked(role = UserRole.PARTNER))
        assertFalse(marked(role = null))
        assertFalse(marked(mode = AppMode.LOCAL))
    }

    @Test fun `generation invalidation leaves queued data and old credentials untouched`() = runTest {
        val gate = SessionCredentialGate()
        val generation = gate.snapshot()
        val retained = mutableListOf("original-refresh", "mutation-uuid", "encrypted-note", "cursor")
        val originalData = retained.toList()
        gate.invalidate()
        val committed = gate.commitIfCurrent(generation) { retained.clear(); true }
        assertFalse(committed)
        assertEquals(originalData, retained)
    }

    @Test fun `cancelled coroutine cannot enter a guarded credential commit`() = runTest {
        val gate = SessionCredentialGate()
        var committed = false
        val job = launch {
            currentCoroutineContext().ensureActive()
            gate.commitIfCurrent(gate.snapshot()) { committed = true; true }
        }
        job.cancel(CancellationException("browser closed"))
        job.join()
        assertFalse(committed)
    }
}
