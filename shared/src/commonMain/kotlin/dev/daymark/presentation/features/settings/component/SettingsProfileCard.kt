package dev.daymark.presentation.features.settings.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.daymark.presentation.features.settings.SettingsUiState
import dev.daymark.reporting.REPORT_TEXT_LIMIT
import dev.daymark.reporting.exceedsReportTextLimit
import dev.daymark.resources.Res
import dev.daymark.resources.cancel
import dev.daymark.resources.invalid_name
import dev.daymark.resources.profile_character_count
import dev.daymark.resources.profile_name
import dev.daymark.resources.profile_too_long
import dev.daymark.resources.save
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.str

@Composable
internal fun SettingsProfileCard(state: SettingsUiState, onSave: (String) -> Unit) {
    val savedName = state.settings.displayName
    // Close only when the persisted name changes; failed saves keep the draft open.
    var editing by rememberSaveable(savedName) { mutableStateOf(false) }
    var input by rememberSaveable(savedName) { mutableStateOf(savedName) }
    SettingsEditorCard(
        SettingsEditorHeader(
            title = Res.string.profile_name,
            editTag = "settings-profile-edit",
            closeLabel = Res.string.cancel,
            editing = editing,
            enabled = !state.busy
        ),
        onToggle = {
            input = savedName
            editing = !editing
        }
    ) {
        if (editing) {
            ProfileNameEditor(state, input, { input = it }, { onSave(input) })
        } else {
            Text(
                text = savedName.ifBlank { "—" },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.testTag("settings-name")
            )
        }
    }
}

@Composable
private fun ProfileNameEditor(
    state: SettingsUiState,
    activeName: String,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit
) {
    val exceedsLimit = exceedsReportTextLimit(activeName)
    val canSave =
        !state.busy && !exceedsLimit && activeName.isNotBlank() && activeName != state.settings.displayName
    val language = LocalAppLanguage.current
    ProfileNameField(activeName, onNameChange, state, exceedsLimit)
    ProfileNameValidation(state.invalidName, exceedsLimit, language)
    Button(onClick = onSave, enabled = canSave) {
        Text(str(Res.string.save))
    }
}

@Composable
private fun ProfileNameField(
    activeName: String,
    onNameChange: (String) -> Unit,
    state: SettingsUiState,
    exceedsLimit: Boolean
) {
    val language = LocalAppLanguage.current
    OutlinedTextField(
        value = activeName,
        onValueChange = onNameChange,
        enabled = !state.busy,
        label = { Text(str(Res.string.profile_name)) },
        singleLine = true,
        isError = state.invalidName || exceedsLimit,
        supportingText = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = str(
                        Res.string.profile_character_count,
                        DisplayFormats.number(activeName.length, language),
                        DisplayFormats.number(REPORT_TEXT_LIMIT, language)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (exceedsLimit) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings-name")
    )
}

@Composable
private fun ProfileNameValidation(
    invalidName: Boolean,
    exceedsLimit: Boolean,
    language: AppLanguage
) {
    AnimatedVisibility(
        visible = exceedsLimit,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Text(
            text = str(
                Res.string.profile_too_long,
                DisplayFormats.number(REPORT_TEXT_LIMIT, language)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    AnimatedVisibility(
        visible = invalidName,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Text(
            text = str(Res.string.invalid_name),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}
