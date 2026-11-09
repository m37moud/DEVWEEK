package dev.daymark.presentation.features.setup.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Regular
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Backspace
import compose.icons.fontawesomeicons.solid.Backward
import compose.icons.fontawesomeicons.solid.Undo
import compose.icons.fontawesomeicons.solid.UndoAlt
import dev.daymark.app.SetupStep
import dev.daymark.navigation.SetupFinish
import dev.daymark.presentation.component.isLocationIssue
import dev.daymark.presentation.features.setup.OnboardingActions
import dev.daymark.presentation.features.setup.SetupIssue
import dev.daymark.presentation.features.setup.SetupUiState
import dev.daymark.resources.Res
import dev.daymark.resources.draft_saved
import dev.daymark.resources.onboarding_back
import dev.daymark.resources.onboarding_finish_customize
import dev.daymark.resources.onboarding_get_started
import dev.daymark.resources.onboarding_next
import dev.daymark.resources.onboarding_unsaved
import dev.daymark.resources.retry
import dev.daymark.resources.start_tracking
import dev.daymark.resources.onboarding_preferences_failed
import dev.daymark.settings.str

@Composable
internal fun SetupStepActions(state: SetupUiState, actions: OnboardingActions) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SetupSaveStatus(state, actions.retrySave)
            if (state.preferencesError) {
                Text(
                    str(Res.string.onboarding_preferences_failed),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .testTag("setup-preferences-error")
                        .semantics { liveRegion = LiveRegionMode.Polite }
                )
                TextButton(
                    onClick = actions.retryPreferences,
                    enabled = !state.busy && !state.preferencesSaving,
                    modifier = Modifier.testTag("setup-preferences-retry")
                ) { Text(str(Res.string.retry)) }
            }
            if (state.issue != null && !state.issue.isLocationIssue()) {
                SetupError(state.issue)
            }
            if (state.draft.step == SetupStep.REVIEW) {
                Button(
                    onClick = { actions.finish(SetupFinish.TODAY) },
                    enabled = !state.interactionBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("setup-start")
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(str(Res.string.start_tracking))
                    }
                }
                OutlinedButton(
                    onClick = { actions.finish(SetupFinish.CUSTOMIZE) },
                    enabled = !state.interactionBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("setup-customize")
                ) {
                    Text(str(Res.string.onboarding_finish_customize))
                }
                TextButton(
                    onClick = actions.back,
                    enabled = !state.interactionBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .testTag("setup-back")
                ) {
                    Text(str(Res.string.onboarding_back))
                }
            } else if (state.draft.step == SetupStep.WELCOME) {
                Button(
                    onClick = actions.advance,
                    enabled = !state.interactionBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("setup-next")
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(str(Res.string.onboarding_get_started))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = actions.back,
                        enabled = !state.interactionBusy,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("setup-back"),
                        elevation = ButtonDefaults.elevatedButtonElevation(10.dp)
                    ) {
                        IconButton(
                            onClick = actions.back,
                            enabled = !state.interactionBusy,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("setup-back-icon")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = str(Res.string.onboarding_back),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Button(
                        onClick = actions.advance,
                        enabled = !state.interactionBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("setup-next")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ){

                            Text(str(Res.string.onboarding_next))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupSaveStatus(state: SetupUiState, onRetry: () -> Unit) {
    if (state.issue == SetupIssue.LOCAL_SAVE_FAILED) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
            TextButton(
                onClick = onRetry,
                enabled = !state.interactionBusy,
                modifier = Modifier.testTag("setup-save-retry")
            ) {
                Text(str(Res.string.retry))
            }
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (state.draftSaved) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    )
            )
            Text(
                text = str(if (state.draftSaved) Res.string.draft_saved else Res.string.onboarding_unsaved),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(if (state.draftSaved) "setup-draft-saved" else "setup-draft-unsaved")
            )
        }
    }
}
