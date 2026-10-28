package de.lunara.app.security

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Refresh tokens are single-use credentials. Once a bounded network renewal starts, keep its
 * lock until the response has been durably committed, even if the requesting screen/worker
 * disappears. Otherwise cancellation can lose a rotated token and let a second caller replay
 * the consumed token. Waiting callers remain cancellable and never start abandoned work.
 *
 * The operation must still check its account generation before committing. NonCancellable
 * protects rotation durability, not permission to restore credentials after logout.
 */
internal class DurableSessionRenewal(
    private val mutex: Mutex = Mutex(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun <T> run(operation: suspend () -> T): T = mutex.withLock {
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable + dispatcher) { operation() }
    }
}
