package de.lunara.app.ui.onboarding

import de.lunara.app.data.remote.dto.MetaDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class OnboardingValidationTest {
    @Test
    fun `new onboarding never prefills a server address`() {
        assertEquals("", OnboardingUiState().serverUrl)
    }
    private val validSetup = OnboardingUiState(
        authMode = AuthMode.SETUP,
        username = "elias",
        password = "a-secure-password",
        displayName = "Elias",
        bootstrapToken = "b".repeat(32),
    )

    @Test
    fun `valid setup matches server constraints`() {
        assertNull(validateOnboardingForm(validSetup))
    }

    @Test
    fun `short password identifies password field`() {
        assertEquals(
            OnboardingError.PASSWORD,
            validateOnboardingForm(validSetup.copy(password = "too-short")),
        )
    }

    @Test
    fun `username containing spaces identifies username field`() {
        assertEquals(
            OnboardingError.USERNAME,
            validateOnboardingForm(validSetup.copy(username = "Elias User")),
        )
    }

    @Test
    fun `short bootstrap token identifies token field`() {
        assertEquals(
            OnboardingError.BOOTSTRAP_TOKEN,
            validateOnboardingForm(validSetup.copy(bootstrapToken = "short")),
        )
    }

    @Test
    fun `bootstrap token accepts copied surrounding whitespace`() {
        val token = "c".repeat(32)
        assertNull(validateOnboardingForm(validSetup.copy(bootstrapToken = "  $token\n")))
        assertEquals(token, normalizeBootstrapToken("  $token\n"))
    }

    @Test
    fun `registration requires the authoritative server capability`() {
        val registration = validSetup.copy(
            authMode = AuthMode.REGISTER,
            bootstrapToken = "",
            meta = MetaDto("Lunara", "1.0.3", "v1", false, false, "now"),
        )

        assertEquals(
            OnboardingError.REGISTRATION_UNAVAILABLE,
            validateOnboardingForm(registration),
        )
        assertNull(
            validateOnboardingForm(
                registration.copy(meta = requireNotNull(registration.meta).copy(registrationEnabled = true)),
            ),
        )
    }

    @Test
    fun `registration stays unavailable while initial setup is required`() {
        val registration = validSetup.copy(
            authMode = AuthMode.REGISTER,
            bootstrapToken = "",
            meta = MetaDto("Lunara", "1.0.3", "v1", true, true, "now"),
        )

        assertEquals(
            OnboardingError.REGISTRATION_UNAVAILABLE,
            validateOnboardingForm(registration),
        )
    }

    @Test
    fun `authoritative setup conflict clears bootstrap data and switches to login`() {
        val reconciled = authenticationFailureState(
            validSetup.copy(
                isBusy = true,
                meta = MetaDto("Lunara", "1.0.3", "v1", true, false, "now"),
            ),
            OnboardingError.SETUP_COMPLETE,
        )

        assertEquals(AuthMode.LOGIN, reconciled.authMode)
        assertEquals("", reconciled.bootstrapToken)
        assertEquals("", reconciled.password)
        assertFalse(reconciled.isBusy)
        assertFalse(requireNotNull(reconciled.meta).setupRequired)
        assertEquals(OnboardingError.SETUP_COMPLETE, reconciled.error)
    }
}
