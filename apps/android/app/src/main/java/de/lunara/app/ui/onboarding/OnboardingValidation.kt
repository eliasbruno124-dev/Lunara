package de.lunara.app.ui.onboarding

private val usernamePattern = Regex("^[\\p{L}\\p{N}][\\p{L}\\p{N}._-]*$")
private val invitationPattern = Regex("^[A-HJ-NP-Z2-9]{8,12}$", RegexOption.IGNORE_CASE)

internal fun validateOnboardingForm(state: OnboardingUiState): OnboardingError? {
    val username = state.username.trim()
    if (username.length !in 3..64 || !usernamePattern.matches(username)) {
        return OnboardingError.USERNAME
    }
    if (state.password.length !in 12..128) return OnboardingError.PASSWORD

    return when (state.authMode) {
        AuthMode.LOGIN -> null
        AuthMode.SETUP -> when {
            state.displayName.trim().length !in 1..120 -> OnboardingError.DISPLAY_NAME
            state.householdName.trim().length !in 1..120 -> OnboardingError.HOUSEHOLD_NAME
            normalizeBootstrapToken(state.bootstrapToken).length !in 20..256 -> OnboardingError.BOOTSTRAP_TOKEN
            else -> null
        }
        AuthMode.JOIN -> when {
            state.displayName.trim().length !in 1..120 -> OnboardingError.DISPLAY_NAME
            !invitationPattern.matches(state.invitationCode.trim()) -> OnboardingError.INVITATION_CODE
            else -> null
        }
    }
}

internal fun normalizeBootstrapToken(value: String): String = value.trim()

internal fun authenticationFailureState(
    state: OnboardingUiState,
    error: OnboardingError,
): OnboardingUiState = if (error == OnboardingError.SETUP_COMPLETE) {
    state.copy(
        isBusy = false,
        password = "",
        bootstrapToken = "",
        authMode = AuthMode.LOGIN,
        meta = state.meta?.copy(setupRequired = false),
        error = error,
    )
} else {
    state.copy(isBusy = false, password = "", error = error)
}
