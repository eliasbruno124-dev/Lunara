package de.lunara.app.security

import de.lunara.app.data.repository.SessionCredentialGate
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DurableSessionRenewalTest {
    @Test
    fun `cancelled caller still persists rotation before next caller reads credentials`() = runTest {
        val renewal = DurableSessionRenewal(dispatcher = StandardTestDispatcher(testScheduler))
        val response = CompletableDeferred<String>()
        var persisted = "synthetic-old-refresh"
        val observed = mutableListOf<String>()
        val first = launch {
            renewal.run {
                observed += persisted
                persisted = response.await()
            }
        }
        runCurrent()
        first.cancel()
        val second = launch { renewal.run { observed += persisted } }
        runCurrent()
        assertEquals(listOf("synthetic-old-refresh"), observed)
        assertFalse(second.isCompleted)

        response.complete("synthetic-rotated-refresh")
        advanceUntilIdle()

        assertEquals("synthetic-rotated-refresh", persisted)
        assertEquals(listOf("synthetic-old-refresh", "synthetic-rotated-refresh"), observed)
        assertTrue(first.isCancelled)
        assertTrue(second.isCompleted)
    }

    @Test
    fun `cancellation during durable commit cannot expose the old token to another renewal`() = runTest {
        val renewal = DurableSessionRenewal(dispatcher = StandardTestDispatcher(testScheduler))
        val allowCommit = CompletableDeferred<Unit>()
        var persisted = "synthetic-old-refresh"
        val first = launch {
            renewal.run {
                val response = "synthetic-rotated-refresh"
                allowCommit.await()
                persisted = response
            }
        }
        runCurrent()
        first.cancel()
        var nextRead: String? = null
        launch { renewal.run { nextRead = persisted } }
        runCurrent()
        assertEquals(null, nextRead)
        allowCommit.complete(Unit)
        advanceUntilIdle()
        assertEquals("synthetic-rotated-refresh", nextRead)
    }

    @Test
    fun `cancelled waiting request never starts another token exchange`() = runTest {
        val renewal = DurableSessionRenewal(dispatcher = StandardTestDispatcher(testScheduler))
        val response = CompletableDeferred<Unit>()
        var exchanges = 0
        launch { renewal.run { exchanges++; response.await() } }
        runCurrent()
        val waiting = launch { renewal.run { exchanges++ } }
        runCurrent()
        waiting.cancel()
        response.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, exchanges)
    }

    @Test
    fun `network failure retains credentials and releases lock for retry`() = runTest {
        val renewal = DurableSessionRenewal(dispatcher = StandardTestDispatcher(testScheduler))
        var persisted = "synthetic-old-refresh"
        val response = CompletableDeferred<String>().apply {
            completeExceptionally(IOException("synthetic unavailable provider"))
        }
        val failure = runCatching {
            renewal.run { persisted = response.await() }
        }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertEquals("synthetic-old-refresh", persisted)
        renewal.run { persisted = "synthetic-retried-refresh" }
        assertEquals("synthetic-retried-refresh", persisted)
    }

    @Test
    fun `definitive rejection is propagated without inventing a new session`() = runTest {
        val renewal = DurableSessionRenewal(dispatcher = StandardTestDispatcher(testScheduler))
        val failure = runCatching {
            renewal.run { throw AuthentikSessionExpiredException() }
        }.exceptionOrNull()
        assertTrue(failure is AuthentikSessionExpiredException)
    }

    @Test
    fun `late renewal still refuses to restore credentials after logout`() = runTest {
        val renewal = DurableSessionRenewal(dispatcher = StandardTestDispatcher(testScheduler))
        val credentialGate = SessionCredentialGate()
        val generation = credentialGate.snapshot()
        val response = CompletableDeferred<String>()
        var persisted: String? = "synthetic-account-a"
        var committed: Boolean? = null
        val caller = launch {
            renewal.run {
                val rotated = response.await()
                committed = credentialGate.commitIfCurrent(generation) {
                    persisted = rotated
                    true
                }
            }
        }
        runCurrent()
        credentialGate.invalidate()
        persisted = null
        caller.cancel()
        response.complete("synthetic-old-account-rotation")
        advanceUntilIdle()
        assertEquals(false, committed)
        assertEquals(null, persisted)
    }
}
