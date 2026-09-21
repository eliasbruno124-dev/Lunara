package de.lunara.app.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.lunara.app.R
import de.lunara.app.data.remote.dto.CreatePersonalTipRequest
import java.util.UUID

internal object PersonalTipDraftPolicy {
    fun valid(title: String, body: String, action: String): Boolean =
        listOf(title to 160, body to 600, action to 240).all { (text, maximum) ->
            text.trim().length in 1..maximum && text.none(Character::isISOControl)
        }
}

@Composable
internal fun PersonalTipsPanel(userId: String, baseUrl: String?, viewModel: PersonalTipsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.userId != userId || state.baseUrl != baseUrl) return
    PersonalTipsEditor(state, viewModel::save, viewModel::disable, viewModel::reload)
}

@Composable
internal fun PersonalTipsEditor(state: PersonalTipsState, onSave: (CreatePersonalTipRequest) -> Unit, onDisable: (String) -> Unit, onReload: () -> Unit) {
    var draftId by remember(state.userId) { mutableStateOf<String?>(null) }
    var title by remember(state.userId) { mutableStateOf("") }
    var body by remember(state.userId) { mutableStateOf("") }
    var action by remember(state.userId) { mutableStateOf("") }
    var removeId by remember(state.userId) { mutableStateOf<String?>(null) }
    LaunchedEffect(state.savedRevision) { if (state.savedRevision > 0) draftId = null }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.personal_tips_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.personal_tips_explanation), style = MaterialTheme.typography.bodySmall)
        state.tips.forEach { tip ->
            Text(tip.titleDe, style = MaterialTheme.typography.titleSmall)
            Text(tip.callToActionDe)
            TextButton(onClick = { removeId = tip.id }, enabled = !state.busy) { Text(stringResource(R.string.personal_tip_disable)) }
        }
        Button(onClick = { draftId = UUID.randomUUID().toString(); title = ""; body = ""; action = "" }, enabled = !state.busy) { Text(stringResource(R.string.personal_tip_add)) }
        if (state.error) {
            Text(stringResource(R.string.personal_tip_error), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onReload, enabled = !state.busy) { Text(stringResource(R.string.relationship_refresh)) }
        }
        if (state.savedRevision > 0) Text(stringResource(R.string.personal_tip_saved), color = MaterialTheme.colorScheme.tertiary)
    }
    draftId?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!state.busy) draftId = null },
            title = { Text(stringResource(R.string.personal_tip_add)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.personal_tip_share_notice))
                    OutlinedTextField(title, { title = it.take(160) }, label = { Text(stringResource(R.string.personal_tip_title_field)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(body, { body = it.take(600) }, label = { Text(stringResource(R.string.personal_tip_body_field)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(action, { action = it.take(240) }, label = { Text(stringResource(R.string.personal_tip_action_field)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    if (state.error) Text(stringResource(R.string.personal_tip_error), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { TextButton(onClick = { onSave(CreatePersonalTipRequest(id, title.trim(), body.trim(), action.trim())) }, enabled = !state.busy && PersonalTipDraftPolicy.valid(title, body, action)) { Text(stringResource(R.string.personal_tip_share)) } },
            dismissButton = { TextButton(onClick = { draftId = null }, enabled = !state.busy) { Text(stringResource(R.string.cancel)) } },
        )
    }
    removeId?.let { id ->
        AlertDialog(onDismissRequest = { removeId = null }, title = { Text(stringResource(R.string.personal_tip_disable)) },
            text = { Text(stringResource(R.string.personal_tip_disable_notice)) },
            confirmButton = { TextButton(onClick = { removeId = null; onDisable(id) }) { Text(stringResource(R.string.personal_tip_disable)) } },
            dismissButton = { TextButton(onClick = { removeId = null }) { Text(stringResource(R.string.cancel)) } })
    }
}
