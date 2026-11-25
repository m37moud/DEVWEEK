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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.daymark.app.HostPlatform
import dev.daymark.app.SetupStep
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.history.AttendancePeriodSettings
import dev.daymark.presentation.component.AttendancePeriodEditor
import dev.daymark.presentation.component.AttendancePeriodEditorState
import dev.daymark.resources.attendance_period_vacation_work_only
import dev.daymark.presentation.features.settings.component.SettingsPreferencesCard
import dev.daymark.presentation.features.setup.component.OnboardingProgress
import dev.daymark.presentation.features.setup.component.OnboardingStep
import dev.daymark.presentation.features.setup.component.OpeningBalance
import dev.daymark.presentation.features.setup.component.SetupActions
import dev.daymark.presentation.features.setup.component.SetupAutomation
import dev.daymark.presentation.features.setup.component.SetupPlace
import dev.daymark.presentation.features.setup.component.SetupProfile
import dev.daymark.presentation.features.setup.component.SetupRecovery
import dev.daymark.presentation.features.setup.component.SetupReview
import dev.daymark.presentation.features.setup.component.SetupSchedule
import dev.daymark.presentation.features.setup.component.SetupStepActions
import dev.daymark.presentation.features.setup.component.SetupWelcome
import dev.daymark.resources.Res
import dev.daymark.resources.attendance_period
import dev.daymark.resources.onboarding_automation
import dev.daymark.resources.onboarding_place
import dev.daymark.resources.onboarding_preferences
import dev.daymark.resources.onboarding_profile
import dev.daymark.resources.onboarding_review
import dev.daymark.resources.onboarding_welcome
import dev.daymark.resources.schedule
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.str
import org.koin.compose.viewmodel.koinViewModel

data class OnboardingActions(
    val advance: () -> Unit,
    val back: () -> Unit,
    val retryLoad: () -> Unit,
    val retrySave: () -> Unit,
    val reset: () -> Unit,
    val retryPreferences: () -> Unit,
    val finish: () -> Unit
)

@Composable
fun SetupScreen(
    platform: HostPlatform,
    viewModel: SetupViewModel = koinViewModel(),
    appLanguage: AppLanguage = LocalAppLanguage.current
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshAttendanceOptions()
        onPauseOrDispose {}
    }
    val place = state.draft.place
    LaunchedEffect(
        viewModel,
        place?.latitude,
        place?.longitude,
        appLanguage,
        state.draft.step,
        state.placeRegistrationRevision
    ) {
        if (place != null && state.draft.step == SetupStep.PLACE) {
            viewModel.showPlaceAddress(place, appLanguage)
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val actions = SetupActions(
        updateDraft = viewModel::updateDraft,
        registerPlace = {
            keyboard?.hide()
            viewModel.useCurrentLocation()
        },
        openSettings = viewModel::openLocationSettings,
        setLanguage = viewModel::setLanguage,
        setTheme = viewModel::setTheme,
        setAutomaticCheck = viewModel::setAutomaticCheck,
        setResultNotifications = viewModel::setResultNotifications,
        openNotificationSettings = viewModel::openResultNotificationSettings
    )
    val controls = OnboardingActions(
        advance = { keyboard?.hide(); viewModel.advance() },
        back = { keyboard?.hide(); viewModel.back() },
        retryLoad = viewModel::retryLoad,
        retrySave = viewModel::retrySave,
        reset = viewModel::resetDraft,
        retryPreferences = viewModel::retryPreferences,
        finish = {
            keyboard?.hide()
            viewModel.submit()
        }
    )
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = state.draft.step != SetupStep.WELCOME && !state.interactionBusy && !state.completed,
        onBackCompleted = viewModel::back
    )
    OnboardingLayout(state = state, actions = controls, platform = platform) {
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
fun OnboardingLayout(
    state: SetupUiState,
    actions: OnboardingActions,
    platform: HostPlatform = HostPlatform.ANDROID,
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
                OnboardingProgress(step = state.draft.step, platform = platform)
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
            SetupStepActions(state = state, actions = actions)
        }
    }
}

@Composable
fun OnboardingHeading(step: SetupStep) {
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
fun SetupStepContent(
    step: SetupStep,
    state: SetupUiState,
    actions: SetupActions,
    platform: HostPlatform,
    onEdit: (SetupStep) -> Unit
) {
    when (step) {
        SetupStep.WELCOME -> SetupWelcome()

        SetupStep.PREFERENCE -> SettingsPreferencesCard(
            languagePreference = state.preferences.language,
            themePreference = state.preferences.theme,
            onLanguageChange = actions.setLanguage,
            onThemeChange = actions.setTheme,
            busy = state.interactionBusy
        )

        SetupStep.PROFILE -> SetupProfile(state = state, onDraftChange = actions.updateDraft)
        SetupStep.ATTENDANCE_PERIOD -> {
            AttendancePeriodEditor(
                state = AttendancePeriodEditorState(
                    rule = AttendancePeriodSettings(state.draft.attendanceClosingDay, state.draft.vacationYearBasis),
                    enabled = !state.interactionBusy,
                    showVacationYear = true
                ),
                onChange = { rule ->
                    actions.updateDraft(
                        state.draft.copy(
                            attendanceClosingDay = rule.closingDay,
                            vacationYearBasis = rule.vacationYearBasis
                        )
                    )
                }
            )
            Text(str(Res.string.attendance_period_vacation_work_only), style = MaterialTheme.typography.bodySmall)
        }

        SetupStep.PLACE -> SetupPlace(state = state, actions = actions)
        SetupStep.SCHEDULE -> {
            state.draft.schedule?.let { schedule ->
                SetupSchedule(
                    schedule = schedule,
                    busy = state.busy,
                    onScheduleChange = { actions.updateDraft(state.draft.copy(schedule = it)) }
                )
            }
            if (state.draft.category == TrackerCategory.WORK) {
                OpeningBalance(state, actions.updateDraft)
            }
        }

        SetupStep.AUTOMATION -> SetupAutomation(state = state, actions = actions)
        SetupStep.REVIEW -> SetupReview(state = state, platform = platform, onEdit = onEdit)
    }
}

internal fun SetupStep.title() = when (this) {
    SetupStep.WELCOME -> Res.string.onboarding_welcome
    SetupStep.PREFERENCE -> Res.string.onboarding_preferences
    SetupStep.PROFILE -> Res.string.onboarding_profile
    SetupStep.ATTENDANCE_PERIOD -> Res.string.attendance_period
    SetupStep.PLACE -> Res.string.onboarding_place
    SetupStep.SCHEDULE -> Res.string.schedule
    SetupStep.AUTOMATION -> Res.string.onboarding_automation
    SetupStep.REVIEW -> Res.string.onboarding_review
}
