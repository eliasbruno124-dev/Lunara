package de.lunara.app.ui.onboarding

import de.lunara.app.data.remote.dto.MetaDto
import de.lunara.app.data.remote.dto.OidcEnrollmentDto
import de.lunara.app.data.remote.dto.OidcMetaDto
import de.lunara.app.data.repository.ApiException
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
            validateOnboardingForm(validSetup.copy(password = "short")),
        )
    }

    @Test
    fun `new account accepts eight characters without composition rules`() {
        assertNull(validateOnboardingForm(validSetup.copy(password = "12345678")))
    }

    @Test
    fun `new account counts supplementary unicode characters instead of utf16 units`() {
        assertEquals(
            OnboardingError.PASSWORD,
            validateOnboardingForm(validSetup.copy(password = "🔒".repeat(7))),
        )
        assertNull(validateOnboardingForm(validSetup.copy(password = "🔒".repeat(8))))
    }

    @Test
    fun `login accepts an existing non-empty password independently of creation policy`() {
        assertNull(
            validateOnboardingForm(
                validSetup.copy(authMode = AuthMode.LOGIN, password = "x", bootstrapToken = ""),
            ),
        )
        assertEquals(
            OnboardingError.PASSWORD_REQUIRED,
            validateOnboardingForm(
                validSetup.copy(authMode = AuthMode.LOGIN, password = "", bootstrapToken = ""),
            ),
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

    @Test
    fun `invitation qr parser trims and normalizes a valid payload`() {
        assertEquals("ABCDEFGH2345", parseInvitationQrPayload("  abcdefgh2345\n"))
        assertEquals("ABCDEFGH", parseInvitationQrPayload("abcdefgh"))
    }

    @Test
    fun `invitation qr parser rejects ambiguous or wrapped payloads`() {
        listOf(
            null,
            "",
            "ABCDEFG",
            "ABCDEFGH23456",
            "ABCD1FGH",
            "ABCD0FGH",
            "ABCDIFGH",
            "ABCDOFGH",
            "ABCD FGH",
            "https://lunara.example/invite/ABCDEFGH",
            "{\"code\":\"ABCDEFGH\"}",
        ).forEach { payload -> assertNull(payload, parseInvitationQrPayload(payload)) }
    }

    @Test
    fun `invitation parser accepts only the canonical same-server fragment link`() {
        val server = "https://lunara.example"

        assertEquals(
            "ABCDEFGH2345",
            parseInvitationQrPayload("https://lunara.example/invite#invite=abcdefgh2345", server),
        )
        assertEquals("ABCDEFGH2345", parseInvitationQrPayload("ABCDEFGH2345", server))
        listOf(
            "https://other.example/invite#invite=ABCDEFGH2345",
            "https://user@lunara.example/invite#invite=ABCDEFGH2345",
            "https://lunara.example/invite?code=ABCDEFGH2345",
            "https://lunara.example/invite#invite=ABCDEFGH2345&other=x",
        ).forEach { assertNull(it, parseInvitationQrPayload(it, server)) }
    }

    @Test
    fun `canonical invitation link keeps the code in the fragment`() {
        assertEquals(
            "https://lunara.example:8443/invite#invite=ABCDEFGH2345",
            canonicalInvitationLink("https://lunara.example:8443", "abcdefgh2345"),
        )
        assertNull(canonicalInvitationLink("http://lunara.example", "ABCDEFGH2345"))
        assertNull(canonicalInvitationLink("https://user@lunara.example", "ABCDEFGH2345"))
    }

    @Test
    fun `recovered invitation proof forces join continuation after volatile state is lost`() {
        val recovered = invitationOidcContinuation(
            OnboardingUiState(),
            "abcdefgh2345",
        )

        assertEquals("ABCDEFGH2345", recovered?.code)
        assertEquals(true, recovered?.recoveredFromSecureStore)
    }

    @Test
    fun `ordinary oidc login clears abandoned invitation intent before it starts`() {
        val ordinaryLogin = invitationOidcContinuation(OnboardingUiState(authMode = AuthMode.LOGIN), null)

        assertNull(ordinaryLogin)
        assertEquals(true, shouldClearPendingInvitationBeforeOidc(ordinaryLogin?.code))
    }

    @Test
    fun `gateway mode selects required legacy or no authentik join step`() {
        assertEquals(AuthentikJoinPolicy.REQUIRED, authentikJoinPolicy(oidcMeta("authentik")))
        assertEquals(AuthentikJoinPolicy.LEGACY_OPTIONAL, authentikJoinPolicy(oidcMeta(null)))
        assertEquals(AuthentikJoinPolicy.NONE, authentikJoinPolicy(oidcMeta("direct")))
        assertEquals(AuthentikJoinPolicy.NONE, authentikJoinPolicy(oidcMeta("future-mode")))
        assertEquals(
            AuthentikJoinPolicy.NONE,
            authentikJoinPolicy(oidcMeta("authentik").copy(oidc = null)),
        )
    }

    @Test
    fun `authentik primary login never requires a second local identity`() {
        val state = OnboardingUiState(
            authMode = AuthMode.LOGIN,
            username = "",
            password = "",
            displayName = "",
            meta = oidcMeta("authentik"),
        )

        assertFalse(requiresLocalIdentityFields(state))
        assertNull(validateOnboardingForm(state))
        assertEquals(true, shouldCreateMissingAuthentikTracker(state.meta, state.authMode))
    }

    @Test
    fun `authentik primary join validates only the invitation proof`() {
        val state = OnboardingUiState(
            authMode = AuthMode.JOIN,
            username = "",
            password = "",
            displayName = "",
            invitationCode = "abcdefgh2345",
            meta = oidcMeta("authentik"),
        )

        assertFalse(requiresLocalIdentityFields(state))
        assertNull(validateOnboardingForm(state))
        assertFalse(shouldCreateMissingAuthentikTracker(state.meta, state.authMode))
        assertEquals(
            OnboardingError.INVITATION_CODE,
            validateOnboardingForm(state.copy(invitationCode = "invalid")),
        )
    }

    @Test
    fun `legacy oidc metadata keeps the local invitation form compatible`() {
        val state = OnboardingUiState(
            authMode = AuthMode.JOIN,
            invitationCode = "ABCDEFGH2345",
            meta = oidcMeta(null),
        )

        assertEquals(AuthentikJoinPolicy.LEGACY_OPTIONAL, authentikJoinPolicy(state.meta))
        assertFalse(shouldCreateMissingAuthentikTracker(state.meta, AuthMode.LOGIN))
        assertEquals(OnboardingError.USERNAME, validateOnboardingForm(state))
    }

    @Test
    fun `old authentik gateway without primary capability keeps local identity compatibility`() {
        val legacyMeta = oidcMeta("authentik").copy(
            primaryAuthenticationMethod = null,
            oidc = oidcMeta("authentik").oidc?.copy(enrollment = null),
        )
        val state = OnboardingUiState(authMode = AuthMode.LOGIN, meta = legacyMeta)

        assertFalse(usesPrimaryAuthentikIdentity(legacyMeta))
        assertEquals(OnboardingError.USERNAME, validateOnboardingForm(state))
        assertFalse(shouldCreateMissingAuthentikTracker(legacyMeta, AuthMode.LOGIN))
    }

    @Test
    fun `gateway protected join gives authentik specific recovery`() {
        listOf(302, 401, 403).forEach { status ->
            assertEquals(
                OnboardingError.GATEWAY_AUTH_JOIN,
                gatewayJoinRecoveryError(
                    AuthMode.JOIN,
                    ApiException(status, "gateway rejected"),
                    gatewayOidcAvailable = true,
                ),
            )
        }
        assertNull(
            gatewayJoinRecoveryError(
                AuthMode.JOIN,
                ApiException(429, "rate limited"),
                gatewayOidcAvailable = true,
            ),
        )
        assertNull(
            gatewayJoinRecoveryError(
                AuthMode.JOIN,
                ApiException(401, "rejected"),
                gatewayOidcAvailable = false,
            ),
        )
        assertNull(
            gatewayJoinRecoveryError(
                AuthMode.LOGIN,
                ApiException(401, "rejected"),
                gatewayOidcAvailable = true,
            ),
        )
    }

    @Test
    fun `gateway rejection clears a previously prepared session`() {
        val state = authenticationFailureState(
            OnboardingUiState(
                isBusy = true,
                authMode = AuthMode.JOIN,
                password = "temporary-password",
                authentikGatewayReady = true,
            ),
            OnboardingError.GATEWAY_AUTH_JOIN,
        )

        assertFalse(state.isBusy)
        assertFalse(state.authentikGatewayReady)
        assertEquals("", state.password)
        assertEquals(OnboardingError.GATEWAY_AUTH_JOIN, state.error)
    }

    private fun oidcMeta(gatewayMode: String?) = MetaDto(
        name = "Lunara",
        version = "1.1.2",
        apiVersion = "v1",
        setupRequired = false,
        serverTime = "now",
        gatewayMode = gatewayMode,
        primaryAuthenticationMethod = if (gatewayMode == "authentik") "OIDC" else null,
        oidc = OidcMetaDto(
            providerName = "Authentik",
            issuer = "https://auth.example/application/o/lunara/",
            enrollment = OidcEnrollmentDto(
                registration = true,
                invitation = true,
                localPasswordRequired = false,
                createIfMissingRequired = true,
            ),
        ),
    )
}
