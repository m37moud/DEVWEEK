package dev.daymark.presentation.features.settings.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.daymark.reporting.REPORT_TEXT_LIMIT
import dev.daymark.reporting.exceedsReportTextLimit
import dev.daymark.resources.Res
import dev.daymark.resources.cancel
import dev.daymark.resources.edit
import dev.daymark.resources.invalid_name
import dev.daymark.resources.profile_name
import dev.daymark.resources.profile_character_count
import dev.daymark.resources.profile_too_long
import dev.daymark.resources.save
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.str

@Composable
internal fun SettingsProfileCard(
    savedName: String,
    currentInput: String?,
    onNameChange: (String) -> Unit,
    onSaveName: () -> Unit,
    busy: Boolean,
    invalidName: Boolean,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    // Close only when the persisted name changes; failed saves keep the draft open.
    var isEditing by rememberSaveable(savedName) { mutableStateOf(false) }
    val activeName = currentInput ?: savedName

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        AnimatedContent(
            targetState = isEditing,
            transitionSpec = {
                if (targetState) {
                    (fadeIn() + slideInVertically { height -> height / 4 })
                        .togetherWith(fadeOut() + slideOutVertically { height -> -height / 4 })
                } else {
                    (fadeIn() + slideInVertically { height -> -height / 4 })
                        .togetherWith(fadeOut() + slideOutVertically { height -> height / 4 })
                }
            },
            label = "ProfileCardModeTransition"
        ) { editing ->
            if (editing) {
                ProfileEditMode(
                    activeName = activeName,
                    savedName = savedName,
                    onNameChange = onNameChange,
                    onSave = onSaveName,
                    onCancel = {
                        onNameChange(savedName)
                        isEditing = false
                    },
                    busy = busy,
                    invalidName = invalidName,
                    language = language
                )
            } else {
                ProfileViewMode(
                    displayName = savedName,
                    onStartEdit = {
                        onNameChange(savedName)
                        isEditing = true
                    },
                    busy = busy
                )
            }
        }
    }
}

@Composable
private fun ProfileViewMode(
    displayName: String,
    onStartEdit: () -> Unit,
    busy: Boolean
) {
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = str(Res.string.profile_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            FilledTonalIconButton(
                onClick = onStartEdit,
                enabled = !busy,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = str(Res.string.edit),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Text(
            text = displayName.ifBlank { "—" },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("settings-name")
        )
    }
}

@Composable
private fun ProfileEditMode(
    activeName: String,
    savedName: String,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    busy: Boolean,
    invalidName: Boolean,
    language: AppLanguage
) {
    val exceedsLimit = exceedsReportTextLimit(activeName)
    val hasChanges = activeName != savedName
    val canSave = !busy && !exceedsLimit && activeName.isNotBlank() && hasChanges

    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = str(Res.string.profile_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        OutlinedTextField(
            value = activeName,
            onValueChange = onNameChange,
            enabled = !busy,
            label = { Text(str(Res.string.profile_name)) },
            singleLine = true,
            isError = invalidName || exceedsLimit,
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onCancel,
                enabled = !busy
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = str(Res.string.cancel))
            }

            Button(
                onClick = onSave,
                enabled = canSave
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = str(Res.string.save))
            }
        }
    }
}
