package de.lunara.app.security

internal const val ACCOUNT_PASSWORD_MIN_LENGTH = 8
internal const val ACCOUNT_PASSWORD_MAX_LENGTH = 128

private fun accountPasswordLength(value: String): Int =
    value.codePointCount(0, value.length)

internal fun isValidNewAccountPassword(value: String): Boolean =
    accountPasswordLength(value) in ACCOUNT_PASSWORD_MIN_LENGTH..ACCOUNT_PASSWORD_MAX_LENGTH

// Verification remains compatible with credentials created under an older
// policy. Only newly chosen passwords use the current minimum.
internal fun isValidExistingPassword(value: String): Boolean =
    value.isNotEmpty() && accountPasswordLength(value) <= ACCOUNT_PASSWORD_MAX_LENGTH

/** Caps editable password input without cutting a UTF-16 surrogate pair in half. */
internal fun limitAccountPasswordInput(value: String): String {
    if (accountPasswordLength(value) <= ACCOUNT_PASSWORD_MAX_LENGTH) return value
    val endIndex = value.offsetByCodePoints(0, ACCOUNT_PASSWORD_MAX_LENGTH)
    return value.substring(0, endIndex)
}
