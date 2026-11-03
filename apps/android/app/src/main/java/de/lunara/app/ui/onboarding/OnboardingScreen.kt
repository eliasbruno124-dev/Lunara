package de.lunara.app.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import android.app.Activity
import android.os.Build
import de.lunara.app.core.locale.AppLanguage
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.lunara.app.BuildConfig
import de.lunara.app.R
import de.lunara.app.data.remote.dto.MetaDto
import de.lunara.app.ui.components.LunaraHeader
import de.lunara.app.ui.components.LunaraWordmark
import de.lunara.app.ui.scanner.lunaraQrScanOptions
import de.lunara.app.ui.theme.LunaraTheme
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract

@Composable
fun OnboardingRoute(viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shownLanguage = LocalConfiguration.current.locales[0].language
    LaunchedEffect(shownLanguage) {
        viewModel.chooseLanguage(if (shownLanguage == AppLanguage.GERMAN) AppLanguage.GERMAN else AppLanguage.ENGLISH)
    }
    val authentikLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.completeAuthentikLogin(result.data)
    }
    OnboardingScreen(
        state = state,
        onLanguage = { language ->
            viewModel.chooseLanguage(language)
            AppLanguage.set(context, language)
            // Android 13+ recreates the activity itself after the per-app language changes.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) (context as? Activity)?.recreate()
        },
        onContinue = viewModel::continueFromIntro,
        onLocalMode = viewModel::beginLocalSetup,
        onLocalSubmit = viewModel::submitLocalSetup,
        onBack = viewModel::back,
        onServerUrl = viewModel::setServerUrl,
        onScannedServerUrl = viewModel::acceptScannedServerUrl,
        onAllowHttp = viewModel::setAllowHttp,
        onTestConnection = { viewModel.testConnection() },
        onConfirmServerChange = viewModel::confirmServerChange,
        onCancelServerChange = viewModel::cancelServerChange,
        onAuthMode = viewModel::setAuthMode,
        onUsername = viewModel::setUsername,
        onPassword = viewModel::setPassword,
        onDisplayName = viewModel::setDisplayName,
        onBootstrapToken = viewModel::setBootstrapToken,
        onInvitationCode = viewModel::setInvitationCode,
        onInvalidInvitationQr = viewModel::reportInvalidInvitationQr,
        onQrCameraUnavailable = viewModel::reportQrCameraUnavailable,
        onAuthentikLogin = {
            viewModel.beginAuthentikLogin { authentikLauncher.launch(it) }
        },
        onSubmit = viewModel::submitAuthentication,
    )
}

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onLanguage: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onServerUrl: (String) -> Unit,
    onScannedServerUrl: (String) -> Unit,
    onAllowHttp: (Boolean) -> Unit,
    onTestConnection: () -> Unit,
    onConfirmServerChange: () -> Unit,
    onCancelServerChange: () -> Unit,
    onAuthMode: (AuthMode) -> Unit,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onDisplayName: (String) -> Unit,
    onBootstrapToken: (String) -> Unit,
    onInvitationCode: (String) -> Unit,
    onInvalidInvitationQr: () -> Unit = {},
    onQrCameraUnavailable: () -> Unit = {},
    onAuthentikLogin: () -> Unit = {},
    onSubmit: () -> Unit,
    onLocalMode: () -> Unit = {},
    onLocalSubmit: () -> Unit = {},
) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (state.stage != OnboardingStage.INTRO) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back_label))
                    }
                    Spacer(Modifier.weight(1f))
                    LunaraWordmark(compact = true)
                }
            }
            when (state.stage) {
                OnboardingStage.INTRO -> IntroStep(state.language, onLanguage, onContinue, onLocalMode)
                OnboardingStage.LOCAL -> LocalModeStep(state, onDisplayName, onLocalSubmit)
                OnboardingStage.SERVER -> ServerStep(
                    state,
                    onServerUrl,
                    onScannedServerUrl,
                    onAllowHttp,
                    onTestConnection,
                    onQrCameraUnavailable,
                )
                OnboardingStage.AUTH -> AuthStep(
                    state, onAuthMode, onUsername, onPassword, onDisplayName,
                    onBootstrapToken, onInvitationCode, onInvalidInvitationQr,
                    onQrCameraUnavailable, onAuthentikLogin, onSubmit,
                )
            }
        }
    }
    if (state.serverChangeConfirmationRequired) {
        AlertDialog(
            onDismissRequest = onCancelServerChange,
            title = { Text(stringResource(R.string.server_change_title)) },
            text = { Text(stringResource(R.string.server_change_warning)) },
            confirmButton = {
                TextButton(onClick = onConfirmServerChange) { Text(stringResource(R.string.server_change_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = onCancelServerChange) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun IntroStep(
    language: String,
    onLanguage: (String) -> Unit,
    onContinue: () -> Unit,
    onLocalMode: () -> Unit,
) {
    LunaraWordmark()
    Column(
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(R.string.design_intro_eyebrow).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
        Text(
            stringResource(R.string.design_intro_title),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.design_intro_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.language_label),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.CenterVertically).padding(end = 4.dp),
        )
        FilterChip(language == "de", { onLanguage("de") }, { Text(stringResource(R.string.language_german)) }, modifier = Modifier.heightIn(min = 48.dp))
        FilterChip(language == "en", { onLanguage("en") }, { Text(stringResource(R.string.language_english)) }, modifier = Modifier.heightIn(min = 48.dp))
    }
    NoticeCard(stringResource(R.string.medical_notice))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            onClick = onLocalMode,
            modifier = Modifier.fillMaxWidth().testTag("local-mode-start"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                }
                Text(stringResource(R.string.use_on_this_device), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.design_local_path_summary), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Surface(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.connect_server_and_partner), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.design_server_path_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun LocalModeStep(
    state: OnboardingUiState,
    onDisplayName: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    LunaraHeader(
        eyebrow = stringResource(R.string.local_mode_badge),
        title = stringResource(R.string.local_mode_title),
        body = stringResource(R.string.local_mode_setup_summary),
    )
    OutlinedTextField(
        value = state.displayName,
        onValueChange = onDisplayName,
        label = { Text(stringResource(R.string.display_name)) },
        singleLine = true,
        isError = state.error == OnboardingError.DISPLAY_NAME,
        modifier = Modifier.fillMaxWidth().testTag("local-display-name"),
    )
    NoticeCard(stringResource(R.string.local_mode_privacy_notice))
    ErrorText(state.error)
    Button(
        onClick = onSubmit,
        enabled = !state.isBusy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("local-mode-confirm"),
    ) {
        if (state.isBusy) CircularProgressIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
        else Icon(Icons.Rounded.Lock, contentDescription = null)
        Text(stringResource(R.string.start_local_mode), Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun ServerStep(
    state: OnboardingUiState,
    onServerUrl: (String) -> Unit,
    onScannedServerUrl: (String) -> Unit,
    onAllowHttp: (Boolean) -> Unit,
    onTestConnection: () -> Unit,
    onQrCameraUnavailable: () -> Unit,
) {
    var revealCredentialUrl by remember { mutableStateOf(false) }
    val containsGatewayCredentials = remember(state.serverUrl) {
        state.serverUrl.substringBefore("@", missingDelimiterValue = "").contains("://")
    }
    val scanPrompt = stringResource(R.string.scan_qr_prompt)
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        when {
            result.contents != null -> onScannedServerUrl(result.contents)
            result.originalIntent?.getBooleanExtra(Intents.Scan.MISSING_CAMERA_PERMISSION, false) == true -> {
                onQrCameraUnavailable()
            }
        }
    }
    LunaraHeader(
        eyebrow = stringResource(R.string.design_server_step),
        title = stringResource(R.string.server_connect_title),
        body = stringResource(R.string.design_server_intro),
    )
    OutlinedTextField(
        value = state.serverUrl,
        onValueChange = onServerUrl,
        label = { Text(stringResource(R.string.server_url)) },
        placeholder = { Text(stringResource(R.string.server_url_example)) },
        singleLine = true,
        visualTransformation = if (containsGatewayCredentials && !revealCredentialUrl) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        trailingIcon = if (containsGatewayCredentials) {
            {
                IconButton(onClick = { revealCredentialUrl = !revealCredentialUrl }) {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = stringResource(
                            if (revealCredentialUrl) R.string.hide_gateway_url else R.string.show_gateway_url,
                        ),
                    )
                }
            }
        } else null,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    )
    OutlinedButton(
        onClick = {
            scanLauncher.launch(
                lunaraQrScanOptions(scanPrompt),
            )
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null)
        Text(stringResource(R.string.scan_server_qr), Modifier.padding(start = 8.dp))
    }
    GatewayHelp()
    if (BuildConfig.ALLOW_INSECURE_HTTP) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = state.allowInsecureHttp, onCheckedChange = onAllowHttp)
            Text(stringResource(R.string.development_http), modifier = Modifier.padding(start = 4.dp))
        }
        if (state.allowInsecureHttp) NoticeCard(stringResource(R.string.development_http_warning), isError = true)
    }
    ErrorText(state.error)
    Button(
        onClick = onTestConnection,
        enabled = !state.isBusy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        if (state.isBusy) {
            CircularProgressIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            Text(stringResource(R.string.testing_connection), Modifier.padding(start = 12.dp))
        } else {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null)
            Text(stringResource(R.string.test_connection), Modifier.padding(start = 10.dp))
        }
    }
}

@Composable
private fun GatewayHelp() {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                stringResource(if (expanded) R.string.design_gateway_help_close else R.string.design_gateway_help),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (expanded) NoticeCard(stringResource(R.string.gateway_hint))
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AuthStep(
    state: OnboardingUiState,
    onAuthMode: (AuthMode) -> Unit,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onDisplayName: (String) -> Unit,
    onBootstrapToken: (String) -> Unit,
    onInvitationCode: (String) -> Unit,
    onInvalidInvitationQr: () -> Unit,
    onQrCameraUnavailable: () -> Unit,
    onAuthentikLogin: () -> Unit,
    onSubmit: () -> Unit,
) {
    val joinPolicy = authentikJoinPolicy(state.meta)
    val primaryAuthentikIdentity = usesPrimaryAuthentikIdentity(state.meta)
    val localIdentityFields = requiresLocalIdentityFields(state) && !state.invitationConfirmationReady
    val title = when (state.authMode) {
        AuthMode.LOGIN -> R.string.login_title
        AuthMode.SETUP -> R.string.setup_title
        AuthMode.REGISTER -> R.string.register_title
        AuthMode.JOIN -> R.string.join_title
    }
    LunaraHeader(eyebrow = stringResource(R.string.design_account_step), title = stringResource(title))
    state.meta?.let {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                Text(
                    stringResource(R.string.connected_to, it.name, it.version, it.apiVersion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
    if (state.meta?.setupRequired != true) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(state.authMode == AuthMode.LOGIN, { onAuthMode(AuthMode.LOGIN) }, { Text(stringResource(R.string.use_login)) }, modifier = Modifier.heightIn(min = 48.dp))
            FilterChip(state.authMode == AuthMode.JOIN, { onAuthMode(AuthMode.JOIN) }, { Text(stringResource(R.string.use_invitation)) }, modifier = Modifier.heightIn(min = 48.dp))
            if (registrationAvailable(state) && !primaryAuthentikIdentity) {
                FilterChip(
                    state.authMode == AuthMode.REGISTER,
                    { onAuthMode(AuthMode.REGISTER) },
                    { Text(stringResource(R.string.use_registration)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }

    if (state.authMode == AuthMode.LOGIN && state.meta?.oidc != null) {
        Button(
            onClick = onAuthentikLogin,
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null)
            Text(
                stringResource(R.string.sign_in_with_provider, state.meta.oidc.providerName),
                Modifier.padding(start = 10.dp),
            )
        }
        if (!primaryAuthentikIdentity) {
            Text(
                stringResource(R.string.or_use_lunara_account),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                stringResource(R.string.authentik_primary_identity_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (state.authMode == AuthMode.JOIN && joinPolicy != AuthentikJoinPolicy.NONE) {
        NoticeCard(
            stringResource(
                if (primaryAuthentikIdentity) {
                    R.string.authentik_primary_join_summary
                } else if (state.authentikGatewayReady) {
                    R.string.authentik_join_ready
                } else if (joinPolicy == AuthentikJoinPolicy.LEGACY_OPTIONAL) {
                    R.string.authentik_join_legacy_summary
                } else {
                    R.string.authentik_join_summary
                },
            ),
        )
        if (!primaryAuthentikIdentity) {
            OutlinedButton(
                onClick = onAuthentikLogin,
                enabled = !state.isBusy && !state.authentikGatewayReady,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(
                    if (state.authentikGatewayReady) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                    contentDescription = null,
                )
                Text(
                    stringResource(
                        if (state.authentikGatewayReady) {
                            R.string.authentik_join_confirmed
                        } else {
                            R.string.authentik_prepare_join
                        },
                        requireNotNull(state.meta?.oidc).providerName,
                    ),
                    Modifier.padding(start = 10.dp),
                )
            }
        }
    }

    if (state.authMode != AuthMode.LOGIN) {
        RoleCard(isTracker = state.authMode == AuthMode.SETUP || state.authMode == AuthMode.REGISTER)
    }
    if (state.authMode != AuthMode.LOGIN && localIdentityFields) {
        OutlinedTextField(
            state.displayName,
            onDisplayName,
            label = { Text(stringResource(R.string.display_name)) },
            singleLine = true,
            isError = state.error == OnboardingError.DISPLAY_NAME,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        )
    }
    if (state.authMode == AuthMode.SETUP) {
        SecretField(
            state.bootstrapToken,
            onBootstrapToken,
            stringResource(R.string.bootstrap_token),
            state.error == OnboardingError.BOOTSTRAP_TOKEN || state.error == OnboardingError.BOOTSTRAP_REJECTED,
        )
        Text(
            stringResource(R.string.bootstrap_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (state.authMode == AuthMode.JOIN) {
        val invitationScanPrompt = stringResource(R.string.scan_invitation_qr_prompt)
        OutlinedTextField(
            state.invitationCode,
            onInvitationCode,
            label = { Text(stringResource(R.string.invitation_code)) },
            singleLine = true,
            isError = state.error == OnboardingError.INVITATION_CODE,
            modifier = Modifier.fillMaxWidth(),
        )
        val invitationScanner = rememberLauncherForActivityResult(ScanContract()) { result ->
            when {
                result.contents != null -> {
                    parseInvitationQrPayload(result.contents, state.serverUrl)?.let(onInvitationCode)
                        ?: onInvalidInvitationQr()
                }
                result.originalIntent?.getBooleanExtra(Intents.Scan.MISSING_CAMERA_PERMISSION, false) == true -> {
                    onQrCameraUnavailable()
                }
            }
        }
        OutlinedButton(
            onClick = {
                invitationScanner.launch(
                    lunaraQrScanOptions(invitationScanPrompt),
                )
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null)
            Text(stringResource(R.string.scan_invitation_qr), Modifier.padding(start = 8.dp))
        }
        if (primaryAuthentikIdentity || state.invitationConfirmationReady) {
            Button(
                onClick = if (state.invitationConfirmationReady) onSubmit else onAuthentikLogin,
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                if (state.isBusy) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Lock, contentDescription = null)
                }
                Text(
                    if (state.invitationConfirmationReady) {
                        stringResource(R.string.connect_with_invitation)
                    } else {
                        stringResource(
                            R.string.authentik_join_with_provider,
                            requireNotNull(state.meta?.oidc).providerName,
                        )
                    },
                    Modifier.padding(start = 10.dp),
                )
            }
        }
    }
    if (localIdentityFields) {
        OutlinedTextField(
            state.username,
            onUsername,
            label = { Text(stringResource(R.string.username)) },
            singleLine = true,
            isError = state.error == OnboardingError.USERNAME || state.error == OnboardingError.USERNAME_IN_USE,
            modifier = Modifier.fillMaxWidth(),
        )
        SecretField(
            state.password,
            onPassword,
            stringResource(R.string.password),
            state.error == OnboardingError.PASSWORD || state.error == OnboardingError.PASSWORD_REQUIRED,
        )
        if (state.authMode != AuthMode.LOGIN) {
            Text(
                stringResource(R.string.password_length_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ErrorText(state.error)
        val button = when (state.authMode) {
            AuthMode.LOGIN -> R.string.sign_in
            AuthMode.SETUP -> R.string.finish_setup
            AuthMode.REGISTER -> R.string.create_relationship
            AuthMode.JOIN -> R.string.connect_with_invitation
        }
        Button(onSubmit, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            if (state.isBusy) CircularProgressIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else Icon(Icons.Rounded.Lock, contentDescription = null)
            Text(stringResource(button), Modifier.padding(start = 10.dp))
        }
    } else {
        ErrorText(state.error)
    }
}

@Composable
private fun SecretField(value: String, onValue: (String) -> Unit, label: String, isError: Boolean = false) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Rounded.Lock else Icons.Rounded.Info,
                    contentDescription = stringResource(if (visible) R.string.design_hide_secret else R.string.design_show_secret, label),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RoleCard(isTracker: Boolean) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(if (isTracker) R.string.role_tracker_title else R.string.role_partner_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(if (isTracker) R.string.role_tracker_subtitle else R.string.role_partner_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun NoticeCard(text: String, isError: Boolean = false) {
    val contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth().background(
            if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow,
            RoundedCornerShape(12.dp),
        ).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Rounded.Info, contentDescription = null, tint = contentColor, modifier = Modifier.padding(top = 2.dp).size(16.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = contentColor, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ErrorText(error: OnboardingError?) {
    val id = when (error) {
        OnboardingError.INVALID_URL -> R.string.error_invalid_url
        OnboardingError.HTTPS_REQUIRED -> R.string.error_https_required
        OnboardingError.INCOMPLETE_CREDENTIALS -> R.string.error_credentials
        OnboardingError.CREDENTIALS_REQUIRE_HTTPS -> R.string.error_credentials_require_https
        OnboardingError.QR_CREDENTIALS -> R.string.error_qr_credentials
        OnboardingError.QR_CAMERA -> R.string.error_qr_camera
        OnboardingError.DNS -> R.string.error_dns
        OnboardingError.TLS -> R.string.error_tls
        OnboardingError.TIMEOUT -> R.string.error_timeout
        OnboardingError.GATEWAY_AUTH -> R.string.error_gateway_auth
        OnboardingError.GATEWAY_AUTH_JOIN -> R.string.error_gateway_auth_join
        OnboardingError.API -> R.string.error_api
        OnboardingError.USERNAME -> R.string.error_username
        OnboardingError.USERNAME_IN_USE -> R.string.error_username_in_use
        OnboardingError.PASSWORD_REQUIRED -> R.string.error_password_required
        OnboardingError.PASSWORD -> R.string.error_password
        OnboardingError.DISPLAY_NAME -> R.string.error_display_name
        OnboardingError.BOOTSTRAP_TOKEN -> R.string.error_bootstrap_token
        OnboardingError.BOOTSTRAP_REJECTED -> R.string.error_bootstrap_rejected
        OnboardingError.SETUP_COMPLETE -> R.string.error_setup_complete
        OnboardingError.INVITATION_CODE -> R.string.error_invitation_code
        OnboardingError.INVITATION_INVALID -> R.string.error_invitation_invalid
        OnboardingError.INVITATION_UNAVAILABLE -> R.string.error_invitation_unavailable
        OnboardingError.INVITATION_ACCOUNT_NOT_EMPTY -> R.string.error_invitation_account_not_empty
        OnboardingError.INVITATION_ACCEPT_CONFLICT -> R.string.error_invitation_accept_conflict
        OnboardingError.PARTNER_ALREADY_CONNECTED -> R.string.error_partner_already_connected
        OnboardingError.INVALID_CREDENTIALS -> R.string.error_invalid_credentials
        OnboardingError.REGISTRATION_UNAVAILABLE -> R.string.error_registration_unavailable
        OnboardingError.OIDC_CANCELLED -> R.string.error_oidc_cancelled
        OnboardingError.OIDC_NOT_LINKED -> R.string.error_oidc_not_linked
        OnboardingError.OIDC_FAILED -> R.string.error_oidc_failed
        OnboardingError.GENERIC -> R.string.error_generic
        null -> return
    }
    Text(
        stringResource(id),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Preview(showBackground = true, locale = "de")
@Composable
private fun IntroPreview() = LunaraTheme {
    OnboardingScreen(
        state = OnboardingUiState(),
        onLanguage = {}, onContinue = {}, onBack = {}, onServerUrl = {}, onScannedServerUrl = {},
        onAllowHttp = {}, onTestConnection = {}, onConfirmServerChange = {}, onCancelServerChange = {},
        onAuthMode = {}, onUsername = {}, onPassword = {}, onDisplayName = {},
        onBootstrapToken = {}, onInvitationCode = {}, onInvalidInvitationQr = {},
        onQrCameraUnavailable = {}, onAuthentikLogin = {}, onSubmit = {},
    )
}

@Preview(showBackground = true, locale = "en")
@Composable
private fun LoginPreview() = LunaraTheme {
    OnboardingScreen(
        OnboardingUiState(
            stage = OnboardingStage.AUTH,
            language = "en",
            meta = MetaDto("Lunara", "1.0.3", "v1", false, false, "2026-08-13T12:00:00Z"),
        ),
        onLanguage = {}, onContinue = {}, onBack = {}, onServerUrl = {}, onScannedServerUrl = {},
        onAllowHttp = {}, onTestConnection = {}, onConfirmServerChange = {}, onCancelServerChange = {},
        onAuthMode = {}, onUsername = {}, onPassword = {}, onDisplayName = {},
        onBootstrapToken = {}, onInvitationCode = {}, onAuthentikLogin = {}, onSubmit = {},
    )
}
