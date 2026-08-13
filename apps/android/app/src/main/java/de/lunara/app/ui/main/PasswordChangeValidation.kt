package de.lunara.app.ui.main

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
    currentPassword.isEmpty() || currentPassword.length > 128 -> PasswordChangeValidation.CURRENT_REQUIRED
    newPassword.length !in 12..128 -> PasswordChangeValidation.NEW_LENGTH
    currentPassword == newPassword -> PasswordChangeValidation.SAME_PASSWORD
    newPassword != confirmation -> PasswordChangeValidation.CONFIRMATION_MISMATCH
    else -> null
}
