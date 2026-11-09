package dev.daymark.presentation.features.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import dev.daymark.presentation.features.setup.component.OnboardingProgress
import dev.daymark.presentation.features.setup.component.OnboardingStep
import dev.daymark.presentation.features.setup.component.OpeningBalance
import dev.daymark.presentation.features.setup.component.SetupActions
import dev.daymark.presentation.features.setup.component.SetupPlace
import dev.daymark.presentation.features.setup.component.SetupProfile
import dev.daymark.presentation.features.setup.component.SetupRecovery
import dev.daymark.presentation.features.setup.component.SetupReview
import dev.daymark.presentation.features.setup.component.SetupSchedule
import dev.daymark.presentation.features.setup.component.SetupStepActions
import dev.daymark.presentation.features.setup.component.SetupWelcome
import dev.daymark.resources.Res
import dev.daymark.resources.onboarding_place
import dev.daymark.resources.onboarding_profile
import dev.daymark.resources.onboarding_review
import dev.daymark.resources.onboarding_welcome
import dev.daymark.resources.schedule
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.str
import dev.daymark.ui.theme.DaymarkTheme

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
