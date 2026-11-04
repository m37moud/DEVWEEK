package dev.daymark.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.daymark.app.AppGraph
import dev.daymark.app.HostPlatform
import dev.daymark.app.SetupStep
import dev.daymark.attendance.TrackerCategory
import dev.daymark.resources.Res
import dev.daymark.resources.cancel
import dev.daymark.resources.daymark_splash
import dev.daymark.resources.draft_saved
import dev.daymark.resources.onboarding_appearance
import dev.daymark.resources.onboarding_back
import dev.daymark.resources.onboarding_finish_customize
import dev.daymark.resources.onboarding_get_started
import dev.daymark.resources.onboarding_language
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
import dev.daymark.settings.AppSettings
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.str
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
    model: SetupViewModel,
    preferences: SettingsViewModel,
    graph: AppGraph,
    onFinish: (SetupFinish) -> Unit
) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val preferenceState by preferences.uiState.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val actions = SetupActions(model::updateDraft, {
        keyboard?.hide()
        model.useCurrentLocation()
    }, graph.permissions::openSystemSettings)
    val controls =
        OnboardingActions(
            { keyboard?.hide(); model.advance() },
            { keyboard?.hide(); model.back() },
            model::retryLoad,
            model::retrySave,
            model::resetDraft
        ) { destination ->
            if (!model.uiState.value.busy && !model.uiState.value.completed) {
                keyboard?.hide()
                onFinish(destination)
                model.submit()
            }
        }
    val shown = state.copy(busy = state.busy || preferenceState.busy)
    NavigationBackHandler(
        rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = state.draft.step != SetupStep.WELCOME, onBackCompleted = model::back
    )
    OnboardingLayout(shown, controls, preferences = {
        OnboardingPreferences(
            preferenceState.settings,
            preferences::setLanguage,
            preferences::setTheme
        )
        if (preferenceState.storageError) SetupError(SetupIssue.LOCAL_SAVE_FAILED)
    }) {
        if (shown.ready) OnboardingStep(shown.draft.step) {
            OnboardingHeading(shown.draft.step)
            SetupStepContent(shown, actions, graph.platform) { keyboard?.hide(); model.edit(it) }
        }
    }
}

@Composable
private fun OnboardingLayout(
    state: SetupUiState,
    actions: OnboardingActions,
    preferences: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxSize().imePadding().testTag("setup-form")) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            preferences()
            if (!state.ready) SetupRecovery(state, actions) else {
                OnboardingProgress(state.draft.step)
                content()
            }
        }
        if (state.ready) SetupStepActions(state, actions)
    }
}

@Composable
private fun OnboardingPreferences(
    settings: AppSettings,
    onLanguage: (LanguagePreference) -> Unit,
    onTheme: (ThemePreference) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(str(Res.string.onboarding_language), style = MaterialTheme.typography.labelLarge)
        Language(onLanguage, settings)
        Text(str(Res.string.onboarding_appearance), style = MaterialTheme.typography.labelLarge)
        Theme(onTheme, settings)
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
        str(step.title()), style = MaterialTheme.typography.headlineLarge,
        modifier = Modifier.focusRequester(focus).focusable().semantics {
            heading()
            liveRegion = LiveRegionMode.Polite
        }.testTag("setup-heading")
    )
}

@Composable
private fun SetupStepContent(
    state: SetupUiState,
    actions: SetupActions,
    platform: HostPlatform,
    onEdit: (SetupStep) -> Unit
) {
    when (state.draft.step) {
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
            if (state.draft.category == TrackerCategory.WORK) OpeningBalance(
                state,
                actions.updateDraft
            )
        }

        SetupStep.REVIEW -> SetupReview(state, platform, onEdit)
    }
}

@Composable
private fun SetupWelcome() {
    Image(
        painterResource(Res.drawable.daymark_splash), contentDescription = null,
        modifier = Modifier.fillMaxWidth().height(160.dp)
    )
    Text(str(Res.string.setup_description), style = MaterialTheme.typography.titleMedium)
    Text(
        str(Res.string.onboarding_welcome_body),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SetupStepActions(state: SetupUiState, actions: OnboardingActions) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SetupSaveStatus(state, actions.retrySave)
        if (state.issue != null && !state.issue.isLocationIssue()) SetupError(state.issue)
        if (state.draft.step == SetupStep.REVIEW) {
            Button(
                onClick = { actions.finish(SetupFinish.TODAY) }, enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().testTag("setup-start")
            ) {
                Text(str(Res.string.start_tracking))
            }
            OutlinedButton(
                onClick = { actions.finish(SetupFinish.CUSTOMIZE) }, enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().testTag("setup-customize")
            ) {
                Text(str(Res.string.onboarding_finish_customize))
            }
        } else Button(
            onClick = actions.advance, enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().testTag("setup-next")
        ) {
            Text(str(if (state.draft.step == SetupStep.WELCOME) Res.string.onboarding_get_started else Res.string.onboarding_next))
        }
        if (state.draft.step != SetupStep.WELCOME) TextButton(
            onClick = actions.back,
            enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("setup-back")
        ) {
            Text(str(Res.string.onboarding_back))
        }
    }
}

@Composable
private fun SetupSaveStatus(state: SetupUiState, onRetry: () -> Unit) {
    if (state.issue == SetupIssue.LOCAL_SAVE_FAILED) {
        TextButton(
            onClick = onRetry,
            enabled = !state.busy,
            modifier = Modifier.testTag("setup-save-retry")
        ) {
            Text(str(Res.string.retry))
        }
    } else Text(
        str(if (state.draftSaved) Res.string.draft_saved else Res.string.onboarding_unsaved),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(if (state.draftSaved) "setup-draft-saved" else "setup-draft-unsaved")
    )
}

@Composable
private fun SetupRecovery(state: SetupUiState, actions: OnboardingActions) {
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    if (state.busy) CircularProgressIndicator() else {
        Text(str(Res.string.storage_unavailable), modifier = Modifier.testTag("setup-load-error"))
        Button(onClick = actions.retryLoad, modifier = Modifier.testTag("setup-retry")) {
            Text(
                str(
                    Res.string.retry
                )
            )
        }
        if (state.unreadable) TextButton(onClick = {
            confirmReset = true
        }) { Text(str(Res.string.onboarding_reset)) }
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text(str(Res.string.onboarding_reset)) },
        text = { Text(str(Res.string.onboarding_reset_confirmation)) },
        confirmButton = {
            TextButton(onClick = {
                confirmReset = false; actions.reset()
            }) { Text(str(Res.string.reset)) }
        },
        dismissButton = {
            TextButton(onClick = {
                confirmReset = false
            }) { Text(str(Res.string.cancel)) }
        }
    )
}

internal fun SetupStep.title() = when (this) {
    SetupStep.WELCOME -> Res.string.onboarding_welcome
    SetupStep.PROFILE -> Res.string.onboarding_profile
    SetupStep.PLACE -> Res.string.onboarding_place
    SetupStep.SCHEDULE -> Res.string.schedule
    SetupStep.REVIEW -> Res.string.onboarding_review
}
