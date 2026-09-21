package de.lunara.app.data.sync

import de.lunara.app.data.local.MutationEntity
import de.lunara.app.data.repository.ApiException
import de.lunara.app.security.AuthentikSessionExpiredException
import java.io.IOException
import kotlinx.coroutines.CancellationException

internal enum class RecoveryFailureAction { CANCEL, PAUSE_AUTHENTICATION, RETRY, FAIL }

internal object SessionRecoverySyncPolicy {
    val recoverableStates = listOf("PENDING", "IN_FLIGHT", "FAILED")

    /** A delayed rejected request cannot condemn credentials installed after it was sent. */
    fun canMarkAuthenticationFailure(
        requestToken: String?, currentToken: String?,
        requestGeneration: Long, currentGeneration: Long,
    ): Boolean = requestGeneration == currentGeneration && requestToken == currentToken

    fun requiresReauthentication(failure: Throwable): Boolean {
        if (failure is CancellationException) return false
        return generateSequence(failure) { it.cause }.take(8).any {
            it is AuthentikSessionExpiredException ||
                (it is ApiException && (it.status == 401 || it.status in setOf(301, 302, 303, 307, 308)))
        }
    }

    fun failureAction(failure: Throwable, attempt: Int): RecoveryFailureAction = when {
        failure is CancellationException -> RecoveryFailureAction.CANCEL
        requiresReauthentication(failure) -> RecoveryFailureAction.PAUSE_AUTHENTICATION
        failure is IOException -> RecoveryFailureAction.RETRY
        failure is ApiException && (failure.status in setOf(408, 425, 429) || failure.status >= 500 ||
            SyncConflictPolicy.isTransientReservation(failure.status, failure.message)) -> RecoveryFailureAction.RETRY
        MutationQueuePolicy.isTerminalAfterNextFailure(attempt) -> RecoveryFailureAction.FAIL
        else -> RecoveryFailureAction.RETRY
    }

    /** Reset only retry bookkeeping. Stable mutation UUIDs make uncertain previous uploads safe. */
    fun afterRecovery(mutation: MutationEntity, now: String): MutationEntity =
        if (mutation.state !in recoverableStates) mutation else mutation.copy(
            state = "PENDING", attemptCount = 0, nextAttemptAt = now, lastErrorCode = null,
        )
}
