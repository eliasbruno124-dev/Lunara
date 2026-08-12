package de.lunara.app.core.security

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class SecureWipeException(val failures: List<Throwable>) : IllegalStateException(
    "${failures.size} local secure-wipe step(s) failed",
    failures.firstOrNull(),
) {
    init {
        failures.drop(1).forEach(::addSuppressed)
    }
}

/**
 * Executes every local erase step even when another step fails. Remote revocation is explicitly
 * best-effort and can never block the local wipe.
 */
object SecureWipeCoordinator {
    suspend fun execute(
        bestEffortRemoteLogout: suspend () -> Unit = {},
        localSteps: List<suspend () -> Unit>,
    ) {
        runCatching { bestEffortRemoteLogout() }
        withContext(NonCancellable) {
            val failures = mutableListOf<Throwable>()
            localSteps.forEach { step ->
                try {
                    step()
                } catch (failure: Throwable) {
                    failures += failure
                }
            }
            if (failures.isNotEmpty()) throw SecureWipeException(failures)
        }
    }
}
