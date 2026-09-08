package de.lunara.app.data.repository

import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Prevents a response from an old login/refresh generation from restoring credentials after
 * logout, a server switch, or a newer login. Network calls capture [snapshot] before starting;
 * destructive account operations call [invalidate] before clearing local state.
 */
@Singleton
class SessionCredentialGate @Inject constructor() {
    private val generation = AtomicLong(0)
    private val commitMutex = Mutex()

    fun snapshot(): Long = generation.get()

    fun invalidate() {
        generation.incrementAndGet()
    }

    suspend fun commitIfCurrent(expectedGeneration: Long, block: suspend () -> Boolean): Boolean =
        commitMutex.withLock {
            expectedGeneration == generation.get() && block()
        }

    suspend fun <T> exclusive(block: suspend () -> T): T = commitMutex.withLock { block() }
}

internal class CredentialGenerationChangedException :
    IllegalStateException("Credential generation changed while authenticating")

/** The server already provisioned the partner; retain the invitation proof for a safe retry. */
internal class PostProvisionFinalizationException(cause: Throwable) :
    IllegalStateException("Partner provisioning completed but session finalization failed", cause)

internal fun shouldClearPendingInvitationAfterOidcFailure(failure: Throwable): Boolean =
    failure !is PostProvisionFinalizationException
