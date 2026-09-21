package de.lunara.app.ui.main

import de.lunara.app.data.preferences.AppMode
import de.lunara.app.data.repository.ApiException
import de.lunara.app.data.repository.SessionRecoveryCancelledException

enum class SessionRecoveryStatus {
    IDLE, PREPARING, WAITING_BROWSER, SUBMITTING, SUCCESS, CANCELLED, WRONG_ACCOUNT, FAILED;

    val busy: Boolean get() = this == PREPARING || this == SUBMITTING
    val active: Boolean get() = busy || this == WAITING_BROWSER
}

internal object SessionRecoveryUiPolicy {
    fun canOffer(mode: AppMode, userId: String?): Boolean = mode == AppMode.SERVER && userId != null

    fun showBanner(mode: AppMode, userId: String?, required: Boolean): Boolean = canOffer(mode, userId) && required

    fun canStart(mode: AppMode, userId: String?, status: SessionRecoveryStatus): Boolean =
        canOffer(mode, userId) && !status.active

    fun canCompleteBrowser(status: SessionRecoveryStatus): Boolean = status == SessionRecoveryStatus.WAITING_BROWSER

    fun dismissed(status: SessionRecoveryStatus): SessionRecoveryStatus = if (status.busy) status else SessionRecoveryStatus.IDLE

    fun failure(failure: Throwable): SessionRecoveryStatus = when {
        failure is SessionRecoveryCancelledException -> SessionRecoveryStatus.CANCELLED
        failure is ApiException && failure.status == 409 && failure.message == "recovery_identity_mismatch" ->
            SessionRecoveryStatus.WRONG_ACCOUNT
        else -> SessionRecoveryStatus.FAILED
    }
}
