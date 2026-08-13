package de.lunara.app.ui.main

import de.lunara.app.security.isValidExistingPassword
import de.lunara.app.security.isValidNewAccountPassword

internal enum class PasswordChangeValidation {
    CURRENT_REQUIRED,
    NEW_LENGTH,
    SAME_PASSWORD,
    CONFIRMATION_MISMATCH,
}

enum class PasswordChangeStatus {
    IDLE,
    BUSY,
    SUCCESS,
    CURRENT_REQUIRED,
    NEW_LENGTH,
    SAME_PASSWORD,
    CONFIRMATION_MISMATCH,
    CURRENT_REJECTED,
    FAILED,
}

internal fun validatePasswordChange(
    currentPassword: String,
    newPassword: String,
    confirmation: String,
): PasswordChangeValidation? = when {
    !isValidExistingPassword(currentPassword) -> PasswordChangeValidation.CURRENT_REQUIRED
    !isValidNewAccountPassword(newPassword) -> PasswordChangeValidation.NEW_LENGTH
    currentPassword == newPassword -> PasswordChangeValidation.SAME_PASSWORD
    newPassword != confirmation -> PasswordChangeValidation.CONFIRMATION_MISMATCH
    else -> null
}
