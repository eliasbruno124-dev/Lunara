package de.lunara.app.data.sync

import de.lunara.app.data.local.MutationEntity
import de.lunara.app.data.repository.ApiException
import de.lunara.app.security.AuthentikSessionExpiredException
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRecoverySyncPolicyTest {
    @Test fun `delayed B rejection cannot mark valid C credentials as expired`() {
        assertFalse(SessionRecoverySyncPolicy.canMarkAuthenticationFailure("B", "C", 7, 7))
        assertTrue(SessionRecoverySyncPolicy.canMarkAuthenticationFailure("C", "C", 7, 7))
        assertFalse(SessionRecoverySyncPolicy.canMarkAuthenticationFailure("C", "C", 7, 8))
    }

    @Test fun `missing request token is an exact credential snapshot not a wildcard`() {
        assertFalse(SessionRecoverySyncPolicy.canMarkAuthenticationFailure(null, "new-login", 7, 7))
        assertTrue(SessionRecoverySyncPolicy.canMarkAuthenticationFailure(null, null, 7, 7))
        assertFalse(SessionRecoverySyncPolicy.canMarkAuthenticationFailure(null, null, 7, 8))
    }

    @Test fun `only terminal auth and login redirects require recovery`() {
        listOf(ApiException(401), ApiException(302), ApiException(307),
            AuthentikSessionExpiredException(), IOException(AuthentikSessionExpiredException())).forEach {
            assertTrue(SessionRecoverySyncPolicy.requiresReauthentication(it))
            assertEquals(RecoveryFailureAction.PAUSE_AUTHENTICATION,
                SessionRecoverySyncPolicy.failureAction(it, 100))
        }
        listOf(SocketTimeoutException(), IOException(), ApiException(403), ApiException(404),
            ApiException(429), ApiException(503), CancellationException()).forEach {
            assertFalse(SessionRecoverySyncPolicy.requiresReauthentication(it))
        }
    }

    @Test fun `network and unavailable server never exhaust a health mutation`() {
        listOf(SocketTimeoutException(), IOException(), ApiException(408), ApiException(429),
            ApiException(500), ApiException(503), ApiException(409, "already being processed")).forEach {
            assertEquals(RecoveryFailureAction.RETRY, SessionRecoverySyncPolicy.failureAction(it, 1000))
        }
        assertEquals(RecoveryFailureAction.FAIL, SessionRecoverySyncPolicy.failureAction(ApiException(422), 7))
        assertEquals(RecoveryFailureAction.RETRY, SessionRecoverySyncPolicy.failureAction(ApiException(422), 0))
    }

    @Test fun `cancellation preserves the retry budget even if it wraps an auth error`() {
        val cancelled = CancellationException("cancelled").apply { initCause(ApiException(401)) }
        assertFalse(SessionRecoverySyncPolicy.requiresReauthentication(cancelled))
        assertEquals(RecoveryFailureAction.CANCEL, SessionRecoverySyncPolicy.failureAction(cancelled, 100))
    }

    @Test fun `one recovery revives legacy ApiException failures without replacing mutation identity`() {
        val row = mutation("FAILED", "ApiException")
        val recovered = SessionRecoverySyncPolicy.afterRecovery(row, "now")
        assertEquals(row.copy(state = "PENDING", attemptCount = 0, nextAttemptAt = "now", lastErrorCode = null), recovered)
        assertEquals(row.id, recovered.id)
        assertEquals(row.clientMutationId, recovered.clientMutationId)
        assertEquals(row.payloadJson, recovered.payloadJson)
        assertEquals(row.baseVersion, recovered.baseVersion)
        assertEquals(row.createdAt, recovered.createdAt)
    }

    @Test fun `pending and interrupted rows become ready while conflicts remain untouched`() {
        assertEquals(setOf("PENDING", "IN_FLIGHT", "FAILED"), SessionRecoverySyncPolicy.recoverableStates.toSet())
        SessionRecoverySyncPolicy.recoverableStates.forEach {
            val recovered = SessionRecoverySyncPolicy.afterRecovery(mutation(it), "now")
            assertEquals("PENDING", recovered.state)
            assertEquals(0, recovered.attemptCount)
            assertEquals("now", recovered.nextAttemptAt)
        }
        val conflict = mutation("CONFLICT", "VERSION_CONFLICT")
        assertEquals(conflict, SessionRecoverySyncPolicy.afterRecovery(conflict, "now"))
    }

    private fun mutation(state: String, error: String? = "IOException") = MutationEntity(
        id = "queue-row", clientMutationId = "stable-uuid", entityType = "PERIOD", entityId = "period",
        operation = "UPDATE", payloadJson = "{\"privateNote\":\"retained\"}", baseVersion = 4,
        createdAt = "original-order", attemptCount = 8, nextAttemptAt = "later", state = state, lastErrorCode = error,
    )
}
