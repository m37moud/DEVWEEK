package dev.daymark.presentation.features.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.daymark.app.HostPlatform
import dev.daymark.app.SetupStep
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.navigation.SetupFinish
import dev.daymark.presentation.component.isLocationIssue
import dev.daymark.presentation.features.setup.component.OnboardingProgress
import dev.daymark.presentation.features.setup.component.OnboardingStep
import dev.daymark.presentation.features.setup.component.OpeningBalance
import dev.daymark.presentation.features.setup.component.SetupActions
import dev.daymark.presentation.features.setup.component.SetupError
import dev.daymark.presentation.features.setup.component.SetupPlace
import dev.daymark.presentation.features.setup.component.SetupProfile
import dev.daymark.presentation.features.setup.component.SetupReview
import dev.daymark.presentation.features.setup.component.SetupSchedule
import dev.daymark.presentation.features.setup.component.SetupWelcome
import dev.daymark.resources.Res
import dev.daymark.resources.cancel
import dev.daymark.resources.draft_saved
import dev.daymark.resources.onboarding_back
import dev.daymark.resources.onboarding_finish_customize
import dev.daymark.resources.onboarding_get_started
import dev.daymark.resources.onboarding_illustration
import dev.daymark.resources.onboarding_next
import dev.daymark.resources.onboarding_place
import dev.daymark.resources.onboarding_profile
import dev.daymark.resources.onboarding_reset
import dev.daymark.resources.onboarding_reset_confirmation
import dev.daymark.resources.onboarding_review
import dev.daymark.resources.onboarding_unsaved
import dev.daymark.resources.onboarding_welcome
import dev.daymark.resources.onboarding_welcome_body
import dev.daymark.resources.reset
import dev.daymark.resources.retry
import dev.daymark.resources.schedule
import dev.daymark.resources.setup_description
import dev.daymark.resources.start_tracking
import dev.daymark.resources.storage_unavailable
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.str
import dev.daymark.ui.theme.DaymarkTheme
import org.jetbrains.compose.resources.painterResource

internal data class OnboardingActions(
    val advance: () -> Unit,
    val back: () -> Unit,
    val retryLoad: () -> Unit,
    val retrySave: () -> Unit,
    val reset: () -> Unit,
    val finish: (SetupFinish) -> Unit
)

@Composable
fun SetupScreen(
    viewModel: SetupViewModel,
    platform: HostPlatform,
    onFinish: (SetupFinish) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val language = LocalAppLanguage.current
    val place = state.draft.place
    LaunchedEffect(
        viewModel,
        place?.latitude,
        place?.longitude,
        language,
        state.draft.step,
        state.placeRegistrationRevision
    ) {
        if (place != null && state.draft.step == SetupStep.PLACE) {
            viewModel.showPlaceAddress(place, language)
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val actions = SetupActions(
        updateDraft = viewModel::updateDraft,
        registerPlace = {
            keyboard?.hide()
            viewModel.useCurrentLocation()
        },
        openSettings = viewModel::openLocationSettings
    )
    val controls = OnboardingActions(
        advance = { keyboard?.hide(); viewModel.advance() },
        back = { keyboard?.hide(); viewModel.back() },
        retryLoad = viewModel::retryLoad,
        retrySave = viewModel::retrySave,
        reset = viewModel::resetDraft
    ) { destination ->
        if (!viewModel.uiState.value.busy && !viewModel.uiState.value.completed) {
            keyboard?.hide()
            onFinish(destination)
            viewModel.submit()
        }
    }
    NavigationBackHandler(
        rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = state.draft.step != SetupStep.WELCOME,
        onBackCompleted = viewModel::back
    )
    OnboardingLayout(state, controls) {
        if (state.ready) {
            OnboardingStep(state.draft.step) { step ->
                OnboardingHeading(step)
                SetupStepContent(
                    step = step,
                    state = state,
                    actions = actions,
                    platform = platform
                ) {
                    keyboard?.hide()
                    viewModel.edit(it)
                }
            }
        }
    }
}

@Composable
private fun OnboardingLayout(
    state: SetupUiState,
    actions: OnboardingActions,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .testTag("setup-form")
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!state.ready) {
                SetupRecovery(state, actions)
            } else {
                OnboardingProgress(state.draft.step)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        content = content
                    )
                }
            }
        }
        if (state.ready) {
            SetupStepActions(state, actions)
        }
    }
}

@Composable
private fun OnboardingHeading(step: SetupStep) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(step) {
        withFrameNanos { }
        focus.requestFocus()
    }
    Text(
        text = str(step.title()),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .focusRequester(focus)
            .focusable()
            .semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            }
            .testTag("setup-heading")
    )
}

@Composable
private fun SetupStepContent(
    step: SetupStep,
    state: SetupUiState,
    actions: SetupActions,
    platform: HostPlatform,
    onEdit: (SetupStep) -> Unit
) {
    when (step) {
        SetupStep.WELCOME -> SetupWelcome()
        SetupStep.PROFILE -> SetupProfile(state, actions.updateDraft)
        SetupStep.PLACE -> SetupPlace(state, actions)
        SetupStep.SCHEDULE -> {
            state.draft.schedule?.let { schedule ->
                SetupSchedule(
                    schedule,
                    state.busy
                ) { actions.updateDraft(state.draft.copy(schedule = it)) }
            }
            if (state.draft.category == TrackerCategory.WORK) {
                OpeningBalance(state, actions.updateDraft)
            }
        }

        SetupStep.REVIEW -> SetupReview(state, platform, onEdit)
    }
}


@Composable
private fun SetupStepActions(state: SetupUiState, actions: OnboardingActions) {
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
            if (state.issue != null && !state.issue.isLocationIssue()) {
                SetupError(state.issue)
            }
            if (state.draft.step == SetupStep.REVIEW) {
                Button(
                    onClick = { actions.finish(SetupFinish.TODAY) },
                    enabled = !state.busy,
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
                    enabled = !state.busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("setup-customize")
                ) {
                    Text(str(Res.string.onboarding_finish_customize))
                }
                TextButton(
                    onClick = actions.back,
                    enabled = !state.busy,
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
                    enabled = !state.busy,
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = actions.back,
                        enabled = !state.busy,
                        modifier = Modifier
                            .weight(0.4f)
                            .heightIn(min = 48.dp)
                            .testTag("setup-back")
                    ) {
                        Text(str(Res.string.onboarding_back), maxLines = 1)
                    }
                    Button(
                        onClick = actions.advance,
                        enabled = !state.busy,
                        modifier = Modifier
                            .weight(0.6f)
                            .heightIn(min = 48.dp)
                            .testTag("setup-next")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(str(Res.string.onboarding_next), maxLines = 1)
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
                enabled = !state.busy,
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

@Composable
private fun SetupRecovery(state: SetupUiState, actions: OnboardingActions) {
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    if (state.busy) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(48.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = str(Res.string.storage_unavailable),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.testTag("setup-load-error")
                )
                Button(
                    onClick = actions.retryLoad,
                    modifier = Modifier.testTag("setup-retry")
                ) {
                    Text(str(Res.string.retry))
                }
                if (state.unreadable) {
                    TextButton(onClick = { confirmReset = true }) {
                        Text(str(Res.string.onboarding_reset))
                    }
                }
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(str(Res.string.onboarding_reset)) },
            text = { Text(str(Res.string.onboarding_reset_confirmation)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    actions.reset()
                }) {
                    Text(str(Res.string.reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(str(Res.string.cancel))
                }
            }
        )
    }
}

internal fun SetupStep.title() = when (this) {
    SetupStep.WELCOME -> Res.string.onboarding_welcome
    SetupStep.PROFILE -> Res.string.onboarding_profile
    SetupStep.PLACE -> Res.string.onboarding_place
    SetupStep.SCHEDULE -> Res.string.schedule
    SetupStep.REVIEW -> Res.string.onboarding_review
}

@Preview
@Composable
private fun SetupScreenPreview() {
    val state = SetupUiState(ready = true)
    val controls = OnboardingActions({}, {}, {}, {}, {}) {}
    DaymarkTheme(ThemePreference.LIGHT) {
        OnboardingLayout(state, controls) {
            OnboardingHeading(state.draft.step)
            SetupStepContent(
                state.draft.step,
                state,
                SetupActions({}, {}, {}),
                HostPlatform.ANDROID
            ) {}
        }
    }
}

@Preview(name = "Dark Theme")
@Composable
private fun SetupScreenDarkPreview() {
    val state = SetupUiState(ready = true)
    val controls = OnboardingActions({}, {}, {}, {}, {}) {}
    DaymarkTheme(ThemePreference.DARK) {
        OnboardingLayout(state, controls) {
            OnboardingHeading(state.draft.step)
            SetupStepContent(
                state.draft.step,
                state,
                SetupActions({}, {}, {}),
                HostPlatform.ANDROID
            ) {}
        }
    }
}

@Preview(name = "Narrow large text", widthDp = 320, fontScale = 2f)
@Composable
private fun SetupScreenNarrowLargeTextPreview() {
    val state = SetupUiState(ready = true)
    val controls = OnboardingActions({}, {}, {}, {}, {}) {}
    DaymarkTheme(ThemePreference.LIGHT) {
        OnboardingLayout(state, controls) {
            OnboardingHeading(state.draft.step)
            SetupStepContent(
                state.draft.step,
                state,
                SetupActions({}, {}, {}),
                HostPlatform.ANDROID
            ) {}
        }
    }
}
