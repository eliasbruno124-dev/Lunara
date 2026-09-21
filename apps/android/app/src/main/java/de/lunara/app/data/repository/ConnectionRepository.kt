package de.lunara.app.data.repository

import android.content.Intent
import android.os.Build
import androidx.room.withTransaction
import de.lunara.app.core.model.AppSession
import de.lunara.app.core.model.UserRole
import de.lunara.app.core.network.ParsedServerAddress
import de.lunara.app.core.network.ServerChangeConfirmationRequired
import de.lunara.app.core.network.ServerChangePolicy
import de.lunara.app.core.security.SecureCookieJar
import de.lunara.app.data.preferences.AppPreferences
import de.lunara.app.data.remote.ApiClientFactory
import de.lunara.app.data.remote.dto.AuthResponseDto
import de.lunara.app.data.remote.dto.AcceptInvitationRequest
import de.lunara.app.data.remote.dto.JoinRequest
import de.lunara.app.data.remote.dto.LoginRequest
import de.lunara.app.data.remote.dto.MetaDto
import de.lunara.app.data.remote.dto.OidcLinkRequest
import de.lunara.app.data.remote.dto.OidcLoginRequest
import de.lunara.app.data.remote.dto.OidcMetaDto
import de.lunara.app.data.remote.dto.OidcStatusDto
import de.lunara.app.data.remote.dto.RefreshRequest
import de.lunara.app.data.remote.dto.RegisterRequest
import de.lunara.app.data.remote.dto.SetupRequest
import de.lunara.app.data.sync.SyncScheduler
import de.lunara.app.data.sync.ProcessSyncGate
import de.lunara.app.data.sync.SessionRecoverySyncPolicy
import de.lunara.app.data.local.LunaraDatabase
import de.lunara.app.security.AuthentikAuthManager
import de.lunara.app.security.PendingAuthentikSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthorizationException
import java.util.Locale
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthentikLoginResult {
    data class SignedIn(val session: AppSession) : AuthentikLoginResult
    /** OIDC authenticated an existing TRACKER; only an explicit second action may change role. */
    data object InvitationConfirmationRequired : AuthentikLoginResult
    /** Compatibility path for older gateways that cannot provision through OIDC yet. */
    data object LegacyGatewayPrepared : AuthentikLoginResult
}

/** A tracker-to-partner move must discard every cursor, queued mutation and cached health record. */
internal fun requiresInvitationScopeReset(previous: UserRole?, refreshed: UserRole): Boolean =
    previous == UserRole.TRACKER && refreshed == UserRole.PARTNER

/** The local queue contains health records that must never be discarded for a role move. */
internal fun blocksInvitationAcceptance(pendingMutations: Int): Boolean = pendingMutations > 0

@Singleton
class ConnectionRepository @Inject constructor(
    private val preferences: AppPreferences,
    private val secrets: SessionSecrets,
    private val apiFactory: ApiClientFactory,
    private val cookieJar: SecureCookieJar,
    private val accountRepository: AccountRepository,
    private val syncScheduler: SyncScheduler,
    private val credentialGate: SessionCredentialGate,
    private val authentik: AuthentikAuthManager,
    private val database: LunaraDatabase,
) {
    private val refreshMutex = Mutex()
    private val metadataMutex = Mutex()
    private val metadata = mutableMapOf<String, MetaDto>()
    private data class RecoveryAttempt(val identity: RecoveryIdentity, val generation: Long)
    private val pendingRecovery = AtomicReference<RecoveryAttempt?>(null)

    private suspend fun publicMetadata(base: String): MetaDto = metadataMutex.withLock {
        val key = SessionRecoveryPolicy.normalizedBaseUrl(base) ?: throw ApiException(400, "server_not_configured")
        metadata[key] ?: apiFactory.create(base, secrets.gatewayCredentials, null, useStoredAuthentik = false)
            .meta(endpoint(base, "api/v1/meta")).requireBody().also { metadata[key] = it }
    }

    private suspend fun recoveryAttempt(): RecoveryAttempt {
        val generation = credentialGate.snapshot()
        val identity = SessionRecoveryPolicy.identity(preferences.state.first())
            ?: throw ApiException(401, "not_authenticated")
        return RecoveryAttempt(identity, generation)
    }

    suspend fun recoveryUsesAuthentik(): Boolean {
        val attempt = recoveryAttempt()
        val meta = publicMetadata(attempt.identity.baseUrl)
        return meta.oidc != null && (authentik.isConfiguredFor(attempt.identity.baseUrl) ||
            meta.primaryAuthenticationMethod == "OIDC" || "OIDC" in meta.authenticationMethods)
    }

    /** Launching the browser saves only an in-memory proof of the existing vault's identity. */
    suspend fun reauthenticationIntent(): Intent {
        val attempt = recoveryAttempt()
        val oidc = publicMetadata(attempt.identity.baseUrl).oidc ?: throw ApiException(404, "oidc_disabled")
        val intent = authentik.authorizationIntent(oidc)
        requireCurrentRecovery(attempt)
        pendingRecovery.set(attempt)
        return intent
    }

    suspend fun reauthenticateWithAuthentik(result: Intent?): AppSession {
        val attempt = pendingRecovery.getAndSet(null)
            ?: throw CredentialGenerationChangedException()
        if (result == null) throw SessionRecoveryCancelledException()
        val authorizationFailure = AuthorizationException.fromIntent(result)
        if (authorizationFailure != null &&
            authorizationFailure.type == AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW.type &&
            authorizationFailure.code == AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW.code) {
            throw SessionRecoveryCancelledException()
        }
        requireCurrentRecovery(attempt)
        val pending = authentik.completeAuthorization(result)
        return recoverSession(attempt, pending) { base, api ->
            api.oidcLogin(
                endpoint(base, "api/v1/auth/oidc/login"),
                OidcLoginRequest(accessToken = pending.accessToken, deviceName = deviceName(), createIfMissing = false),
            ).requireBody()
        }
    }

    suspend fun reauthenticateWithPassword(username: String, password: String): AppSession =
        recoverSession(recoveryAttempt()) { base, api ->
            api.login(endpoint(base, "api/v1/auth/login"), LoginRequest(username.trim(), password, deviceName()))
                .requireBody()
        }

    private suspend fun requireCurrentRecovery(attempt: RecoveryAttempt) {
        currentCoroutineContext().ensureActive()
        if (!SessionRecoveryPolicy.canCommit(attempt.identity,
                SessionRecoveryPolicy.identity(preferences.state.first()), attempt.identity,
                attempt.generation, credentialGate.snapshot())) {
            throw CredentialGenerationChangedException()
        }
    }

    private suspend fun recoverSession(
        attempt: RecoveryAttempt,
        pendingAuthentik: PendingAuthentikSession? = null,
        call: suspend (String, de.lunara.app.data.remote.LunaraApi) -> AuthResponseDto,
    ): AppSession = ProcessSyncGate.serialized {
        // Same ordering as a worker and the local wiper. No WorkManager cancellation while
        // holding this gate: an active worker may need it to finish cancellation.
        refreshMutex.withLock {
            requireCurrentRecovery(attempt)
            val base = attempt.identity.baseUrl
            val auth = call(base, apiFactory.create(base, secrets.gatewayCredentials, null,
                outerBearerOverride = pendingAuthentik?.accessToken, useStoredAuthentik = false))
            val role = runCatching { UserRole.valueOf(auth.user.role) }.getOrNull()
                ?: throw ApiException(409, "recovery_identity_mismatch")
            if (auth.user.id != attempt.identity.userId || role != attempt.identity.role) {
                throw ApiException(409, "recovery_identity_mismatch")
            }
            require(auth.refreshToken.isNotBlank() && auth.accessToken.isNotBlank()) { "Authentication returned empty credentials" }
            currentCoroutineContext().ensureActive()
            val committed = credentialGate.commitIfCurrent(attempt.generation) {
                val current = preferences.state.first()
                if (!SessionRecoveryPolicy.canCommit(attempt.identity, SessionRecoveryPolicy.identity(current),
                        RecoveryIdentity(base, auth.user.id, role), attempt.generation, credentialGate.snapshot())) {
                    return@commitIfCurrent false
                }
                currentCoroutineContext().ensureActive()
                // Once the verified commit starts, finish its small durable writes even if
                // the activity closes. Neither cancelled nor rejected logins enter this block.
                withContext(NonCancellable) {
                    secrets.refreshToken = auth.refreshToken
                    secrets.accessToken = auth.accessToken
                    secrets.clearRefreshRequestId()
                    if (pendingAuthentik != null) {
                        authentik.commit(base, pendingAuthentik)
                        secrets.gatewayCredentials = null
                    } else {
                        // A successful explicit password login must not retain a rejected
                        // provider session that would block every subsequent request.
                        authentik.clear()
                    }
                    val now = MutationTimestampPolicy.format(Instant.now())
                    database.withTransaction {
                        val retryable = database.mutationDao().recoveryCandidates(SessionRecoverySyncPolicy.recoverableStates)
                        database.mutationDao().updateRecoveryRetries(retryable.map {
                            SessionRecoverySyncPolicy.afterRecovery(it, now)
                        })
                    }
                    preferences.setReauthenticationRequired(base, auth.user.id, role, false)
                    // Invalidate browser attempts and failures captured before this replacement.
                    credentialGate.invalidate()
                    syncScheduler.schedulePeriodic()
                    syncScheduler.requestImmediate()
                    syncScheduler.requestManual()
                }
                true
            }
            if (!committed) throw CredentialGenerationChangedException()
            preferences.state.first().session ?: throw CredentialGenerationChangedException()
        }
    }

    suspend fun recordAuthenticationFailure(
        failure: Throwable,
        expectedBaseUrl: String,
        expectedUserId: String,
        expectedAccessToken: String? = null,
        expectedGeneration: Long = credentialGate.snapshot(),
    ): Boolean {
        if (!SessionRecoverySyncPolicy.requiresReauthentication(failure)) return false
        return credentialGate.commitIfCurrent(expectedGeneration) {
            val current = preferences.state.first()
            val identity = SessionRecoveryPolicy.identity(current) ?: return@commitIfCurrent false
            if (identity.baseUrl != SessionRecoveryPolicy.normalizedBaseUrl(expectedBaseUrl) ||
                identity.userId != expectedUserId ||
                !SessionRecoverySyncPolicy.canMarkAuthenticationFailure(expectedAccessToken, secrets.accessToken,
                    expectedGeneration, credentialGate.snapshot())) {
                return@commitIfCurrent false
            }
            preferences.setReauthenticationRequired(identity.baseUrl, identity.userId, identity.role, true)
        }
    }

    suspend fun startLocalMode(displayName: String): AppSession {
        require(preferences.state.first().session == null) { "An active account must be removed first" }
        // A connection test may already have stored gateway state. Entering local mode starts
        // from a cryptographically fresh, empty local vault and never carries server credentials.
        accountRepository.eraseLocalAccount()
        return preferences.activateLocalMode(displayName)
    }

    suspend fun testAndSaveConnection(
        address: ParsedServerAddress,
        confirmServerChange: Boolean = false,
    ): MetaDto {
        val requestGeneration = credentialGate.snapshot()
        val oldBase = preferences.state.first().baseUrl
        val switchingServer = ServerChangePolicy.requiresConfirmation(oldBase, address.baseUrl)
        if (switchingServer && !confirmServerChange) throw ServerChangeConfirmationRequired()

        // Anonymous proxy redirects can leave a cookie even when URL and credentials
        // have not changed. Every explicit connection test starts clean so "try again" can never
        // reuse a rejected gateway session. The personal header credentials are still attached
        // to every request and are stored separately only after this test succeeds.
        cookieJar.clear()
        val api = apiFactory.create(address.baseUrl, address.gatewayCredentials, null)
        val meta = api.meta(endpoint(address.baseUrl, "api/v1/meta")).requireBody()
        if (meta.apiVersion != SUPPORTED_API_VERSION || meta.name.isBlank()) {
            throw ApiException(426, "unsupported_api_version")
        }
        if (switchingServer) {
            // The new endpoint has been validated and the user explicitly confirmed. Revoke the
            // old session best-effort and destroy all old local state before saving anything new.
            accountRepository.eraseLocalAccount()
        }
        val commitGeneration = if (switchingServer) credentialGate.snapshot() else requestGeneration
        val committed = credentialGate.commitIfCurrent(commitGeneration) {
            secrets.gatewayCredentials = address.gatewayCredentials
            preferences.saveConnection(address.baseUrl, meta.name, meta.version)
            true
        }
        if (!committed) throw CredentialGenerationChangedException()
        return meta
    }

    suspend fun login(username: String, password: String): AppSession = authenticate { base, api ->
        api.login(
            endpoint(base, "api/v1/auth/login"),
            LoginRequest(username.trim(), password, deviceName()),
        ).requireBody()
    }

    suspend fun setup(
        bootstrapToken: String,
        username: String,
        displayName: String,
        password: String,
        language: String,
    ): AppSession = authenticate { base, api ->
        api.setup(
            endpoint(base, "api/v1/setup"),
            SetupRequest(
                bootstrapToken = bootstrapToken.trim(),
                username = username.trim(),
                displayName = displayName.trim(),
                password = password,
                preferredLanguage = language.uppercase(Locale.ROOT),
                deviceName = deviceName(),
            ),
        ).requireBody()
    }

    suspend fun register(
        username: String,
        displayName: String,
        password: String,
        language: String,
    ): AppSession = authenticate { base, api ->
        api.registerTracker(
            endpoint(base, "api/v1/auth/register"),
            RegisterRequest(
                username = username.trim(),
                displayName = displayName.trim(),
                password = password,
                preferredLanguage = language.uppercase(Locale.ROOT),
                deviceName = deviceName(),
            ),
        ).requireBody()
    }

    suspend fun join(
        invitationCode: String,
        username: String,
        displayName: String,
        password: String,
        language: String,
    ): AppSession = authenticate { base, api ->
        api.join(
            endpoint(base, "api/v1/household/join"),
            JoinRequest(
                invitationCode = invitationCode.trim().uppercase(Locale.ROOT),
                username = username.trim(),
                displayName = displayName.trim(),
                password = password,
                preferredLanguage = language.uppercase(Locale.ROOT),
                deviceName = deviceName(),
            ),
        ).requireBody()
    }

    suspend fun authentikAuthorizationIntent(meta: OidcMetaDto): Intent {
        preferences.state.first().baseUrl ?: throw ApiException(400, "server_not_configured")
        return authentik.authorizationIntent(meta)
    }

    fun rememberPendingInvitation(invitationCode: String) {
        secrets.pendingInvitationCode = invitationCode.trim().uppercase(Locale.ROOT)
    }

    fun pendingInvitationCode(): String? = secrets.pendingInvitationCode

    fun clearPendingInvitation() {
        secrets.pendingInvitationCode = null
    }

    suspend fun hasAuthentikGatewaySession(): Boolean {
        val base = preferences.state.first().baseUrl ?: return false
        if (!authentik.isConfiguredFor(base) || !authentik.refreshFor(base)) return false
        val accessToken = authentik.currentAccessTokenFor(base) ?: return false
        return gatewayBearerAccepted(base, accessToken)
    }

    suspend fun authentikAuthorizationIntent(): Intent {
        val state = preferences.state.first()
        val base = state.baseUrl ?: throw ApiException(400, "server_not_configured")
        val meta = apiFactory.create(base, secrets.gatewayCredentials, null)
            .meta(endpoint(base, "api/v1/meta"))
            .requireBody()
        val oidc = meta.oidc ?: throw ApiException(404, "oidc_disabled")
        return authentik.authorizationIntent(oidc)
    }

    suspend fun authentikLogin(
        result: Intent?,
        invitationCode: String? = null,
        preferredLanguage: String? = null,
        createIfMissing: Boolean? = null,
        allowLegacyGatewayPreparation: Boolean = true,
    ): AuthentikLoginResult {
        val pending = authentik.completeAuthorization(result)
        val generation = credentialGate.snapshot()
        val initialState = preferences.state.first()
        val base = initialState.baseUrl ?: throw ApiException(400, "server_not_configured")
        try {
            val deferInvitationSession = invitationCode != null && createIfMissing != true
            val session = authenticate(
                    outerBearerOverride = pending.accessToken,
                    onCommitted = {
                        authentik.commit(base, pending)
                        // A personal OIDC session supersedes the legacy shared gateway credential.
                        secrets.gatewayCredentials = null
                    },
                    persistSession = !deferInvitationSession,
                ) { requestBase, api ->
                    api.oidcLogin(
                        endpoint(requestBase, "api/v1/auth/oidc/login"),
                        OidcLoginRequest(
                            accessToken = pending.accessToken,
                            deviceName = deviceName(),
                            invitationCode = invitationCode
                                ?.trim()
                                ?.uppercase(Locale.ROOT)
                                ?.takeIf(String::isNotBlank),
                            preferredLanguage = preferredLanguage
                                ?.trim()
                                ?.uppercase(Locale.ROOT)
                                ?.takeIf(String::isNotBlank),
                            createIfMissing = createIfMissing,
                        ),
                    ).requireBody()
                }
            if (!deferInvitationSession) return AuthentikLoginResult.SignedIn(session)
            return if (session.role == UserRole.PARTNER) {
                val access = secrets.accessToken ?: throw ApiException(401, "not_authenticated")
                val refreshed = try {
                    ProcessSyncGate.serialized {
                        finalizeSessionFromMe(
                            clearServerData = false,
                            expectedGeneration = generation,
                            base = base,
                            access = access,
                            serverName = initialState.serverName,
                            serverVersion = initialState.serverVersion,
                            syncAlreadyStopped = false,
                        )
                    }
                } catch (failure: Throwable) {
                    throw PostProvisionFinalizationException(failure)
                }
                secrets.pendingInvitationCode = null
                AuthentikLoginResult.SignedIn(refreshed)
            } else {
                AuthentikLoginResult.InvitationConfirmationRequired
            }
        } catch (error: ApiException) {
            if (
                allowLegacyGatewayPreparation &&
                error.status == 401 &&
                gatewayBearerAccepted(base, pending.accessToken)
            ) {
                // The proxy already accepted this personal Authentik token, but the
                // older inner API cannot provision it yet. Retain only the outer
                // session for the legacy password-based invitation path. A current
                // Authentik-primary server never enables this branch: its OIDC token
                // and optional invitation are accepted or rejected as one operation.
                val committed = credentialGate.commitIfCurrent(generation) {
                    val current = preferences.state.first()
                    if (current.baseUrl != base || current.session != null) {
                        return@commitIfCurrent false
                    }
                    authentik.commit(base, pending)
                    secrets.gatewayCredentials = null
                    true
                }
                if (!committed) throw CredentialGenerationChangedException()
                return AuthentikLoginResult.LegacyGatewayPrepared
            }
            throw error
        }
    }

    private suspend fun gatewayBearerAccepted(base: String, accessToken: String): Boolean =
        runCatching {
            val api = apiFactory.create(base, null, null, accessToken)
            val health = api.health(endpoint(base, "health"))
            if (!health.isSuccessful) return@runCatching false
            val meta = api.meta(endpoint(base, "api/v1/meta"))
            if (!meta.isSuccessful) return@runCatching false
            gatewayProbeAccepted(
                statusCode = health.code(),
                healthStatus = health.body()?.status,
                gatewayMode = meta.body()?.gatewayMode,
            )
        }.getOrDefault(false)

    /** Redeems an invitation only after the UI has shown its explicit confirmation. */
    suspend fun acceptInvitation(invitationCode: String): AppSession {
        // Avoid disrupting ordinary sync when the queue is already known to contain data.
        if (blocksInvitationAcceptance(database.mutationDao().observeCount().first())) {
            throw ApiException(409, "pending_local_data")
        }
        // Workers must be cancelled before acquiring the process gate because an active worker
        // may already be waiting to finish while it holds that same gate.
        syncScheduler.cancelAllAndWait()
        apiFactory.cancelAll()
        var resumeSyncOnFailure = true
        try {
            return ProcessSyncGate.serialized {
                // Hold the same gate as all local health-data writes from this re-check through
                // the network acceptance and credential-guarded role commit.
                if (blocksInvitationAcceptance(database.mutationDao().observeCount().first())) {
                    throw ApiException(409, "pending_local_data")
                }
                val generation = credentialGate.snapshot()
                val initialState = preferences.state.first()
                val base = initialState.baseUrl ?: throw ApiException(400, "server_not_configured")
                val access = secrets.accessToken ?: throw ApiException(401, "not_authenticated")
                val response = apiFactory.create(base, secrets.gatewayCredentials, access)
                    .acceptInvitation(
                        endpoint(base, "api/v1/household/invitations/accept"),
                        AcceptInvitationRequest(invitationCode.trim().uppercase(Locale.ROOT)),
                    )
                if (!response.isSuccessful) throw ApiException(response.code())
                val refreshed = finalizeSessionFromMe(
                    clearServerData = true,
                    expectedGeneration = generation,
                    base = base,
                    access = access,
                    serverName = initialState.serverName,
                    serverVersion = initialState.serverVersion,
                    syncAlreadyStopped = true,
                )
                secrets.pendingInvitationCode = null
                resumeSyncOnFailure = false
                refreshed
            }
        } finally {
            if (resumeSyncOnFailure) {
                syncScheduler.schedulePeriodic()
                syncScheduler.requestImmediate()
            }
        }
    }

    private suspend fun finalizeSessionFromMe(
        clearServerData: Boolean,
        expectedGeneration: Long,
        base: String,
        access: String,
        serverName: String?,
        serverVersion: String?,
        syncAlreadyStopped: Boolean,
    ): AppSession {
        val user = apiFactory.create(base, secrets.gatewayCredentials, access)
            .me(endpoint(base, "api/v1/me"))
            .requireBody()
        val role = runCatching { UserRole.valueOf(user.role) }.getOrElse { throw ApiException(403, "unsupported_role") }
        val committed = credentialGate.commitIfCurrent(expectedGeneration) {
            val current = preferences.state.first()
            if (current.baseUrl != base || secrets.accessToken != access) return@commitIfCurrent false
            if (clearServerData || requiresInvitationScopeReset(current.session?.role, role)) {
                if (!syncAlreadyStopped) {
                    syncScheduler.cancelAllAndWait()
                    apiFactory.cancelAll()
                }
                withContext(Dispatchers.IO) {
                    database.clearAllTables()
                }
                preferences.clearLastSync()
            }
            preferences.saveSession(user.id, user.username, user.displayName, role)
            true
        }
        if (!committed) throw CredentialGenerationChangedException()
        syncScheduler.schedulePeriodic()
        syncScheduler.requestImmediate()
        return AppSession(user.id, user.displayName, role, serverName ?: "Lunara", serverVersion.orEmpty())
    }

    suspend fun linkAuthentik(result: Intent?, currentPassword: String): OidcStatusDto {
        val pending = authentik.completeAuthorization(result)
        val generation = credentialGate.snapshot()
        val state = preferences.state.first()
        val base = state.baseUrl ?: throw ApiException(400, "server_not_configured")
        val localAccessToken = secrets.accessToken ?: throw ApiException(401, "not_authenticated")
        val status = apiFactory.create(
            base,
            secrets.gatewayCredentials,
            localAccessToken,
            outerBearerOverride = pending.accessToken,
        ).linkOidc(
            endpoint(base, "api/v1/auth/oidc/link"),
            OidcLinkRequest(pending.accessToken, currentPassword),
        ).requireBody()
        val committed = credentialGate.commitIfCurrent(generation) {
            val current = preferences.state.first()
            if (current.baseUrl != base || current.session?.userId != state.session?.userId) {
                return@commitIfCurrent false
            }
            authentik.commit(base, pending)
            secrets.gatewayCredentials = null
            true
        }
        if (!committed) throw CredentialGenerationChangedException()
        return status
    }

    suspend fun oidcStatus(): OidcStatusDto {
        val state = preferences.state.first()
        val base = state.baseUrl ?: throw ApiException(400, "server_not_configured")
        val access = secrets.accessToken ?: throw ApiException(401, "not_authenticated")
        return apiFactory.create(base, secrets.gatewayCredentials, access)
            .oidcStatus(endpoint(base, "api/v1/auth/oidc/status"))
            .requireBody()
    }

    suspend fun refresh(): Boolean = refreshMutex.withLock {
        val generation = credentialGate.snapshot()
        val state = preferences.state.first()
        if (state.reauthenticationRequired) return@withLock false
        val base = state.baseUrl ?: return@withLock false
        val session = state.session ?: return@withLock false
        val originalAccess = secrets.accessToken
        val refresh = secrets.refreshToken
        if (refresh.isNullOrBlank()) {
            recordAuthenticationFailure(ApiException(401), base, session.userId, originalAccess, generation)
            return@withLock false
        }
        val auth = try {
            val supportsReplay = publicMetadata(base).refreshIdempotencySupported
            val hasAuthentikSession = authentik.isConfiguredFor(base)
            if (hasAuthentikSession && !authentik.refreshFor(base)) return@withLock false
            val oidcAccessToken = if (hasAuthentikSession) authentik.currentAccessTokenFor(base) else null
            var requestId: String? = null
            val requestStillCurrent = credentialGate.commitIfCurrent(generation) {
                if (SessionRecoveryPolicy.identity(preferences.state.first()) != SessionRecoveryPolicy.identity(state) ||
                    secrets.refreshToken != refresh) return@commitIfCurrent false
                if (supportsReplay) requestId = secrets.refreshRequestIdFor(refresh)
                true
            }
            if (!requestStillCurrent) return@withLock false
            apiFactory.create(base, secrets.gatewayCredentials, null).refresh(
                endpoint(base, "api/v1/auth/refresh"),
                RefreshRequest(refresh, oidcAccessToken, requestId),
            ).requireBody()
        } catch (failure: Throwable) {
            if (SessionRecoverySyncPolicy.requiresReauthentication(failure)) {
                recordAuthenticationFailure(failure, base, session.userId, originalAccess, generation)
                return@withLock false
            }
            // Transport uncertainty must keep the same persisted request UUID for replay.
            throw failure
        }
        if (auth.user.id != session.userId || auth.user.role != session.role.name ||
            auth.accessToken.isBlank() || auth.refreshToken.isBlank()) {
            recordAuthenticationFailure(ApiException(401), base, session.userId, originalAccess, generation)
            return@withLock false
        }
        // Refresh rotation is committed as one serialized operation. Other repositories cannot
        // race the same refresh token and accidentally overwrite the newer rotated credential.
        // Persist the rotated refresh credential first: after an interruption it can still mint
        // a fresh access token, whereas a new access token plus the invalidated old refresh token
        // would eventually strand the session.
        credentialGate.commitIfCurrent(generation) {
            val current = preferences.state.first()
            if (
                current.baseUrl != base ||
                SessionRecoveryPolicy.identity(current) != SessionRecoveryPolicy.identity(state) ||
                secrets.refreshToken != refresh
            ) {
                return@commitIfCurrent false
            }
            secrets.refreshToken = auth.refreshToken
            secrets.accessToken = auth.accessToken
            secrets.clearRefreshRequestId()
            true
        }
    }

    private suspend fun authenticate(
        outerBearerOverride: String? = null,
        onCommitted: (String) -> Unit = {},
        persistSession: Boolean = true,
        call: suspend (baseUrl: String, api: de.lunara.app.data.remote.LunaraApi) -> AuthResponseDto,
    ): AppSession {
        val generation = credentialGate.snapshot()
        val state = preferences.state.first()
        val base = state.baseUrl ?: throw ApiException(400, "server_not_configured")
        val api = apiFactory.create(base, secrets.gatewayCredentials, null, outerBearerOverride)
        val auth = call(base, api)
        val rawRole = auth.user.role
        val role = runCatching { UserRole.valueOf(rawRole) }.getOrElse {
            throw ApiException(403, "unsupported_role")
        }
        val committed = credentialGate.commitIfCurrent(generation) {
            val current = preferences.state.first()
            if (current.baseUrl != base || current.session != null) {
                return@commitIfCurrent false
            }
            secrets.refreshToken = auth.refreshToken
            secrets.accessToken = auth.accessToken
            secrets.clearRefreshRequestId()
            onCommitted(base)
            if (persistSession) preferences.saveSession(auth.user.id, auth.user.username, auth.user.displayName, role)
            if (persistSession) preferences.setReauthenticationRequired(base, auth.user.id, role, false)
            true
        }
        if (!committed) throw CredentialGenerationChangedException()
        if (persistSession) {
            syncScheduler.schedulePeriodic()
            syncScheduler.requestImmediate()
        }
        return AppSession(
            auth.user.id,
            auth.user.displayName,
            role,
            state.serverName ?: "Lunara",
            state.serverVersion.orEmpty(),
        )
    }

    private fun deviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(80)

    companion object { const val SUPPORTED_API_VERSION = "v1" }
}
