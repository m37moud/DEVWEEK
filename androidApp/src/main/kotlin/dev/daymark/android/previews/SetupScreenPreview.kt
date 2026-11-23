package dev.daymark.android.previews

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import dev.daymark.app.HostPlatform
import dev.daymark.app.SetupStep
import dev.daymark.history.VacationYearBasis
import dev.daymark.models.attendance.RegisteredPlace
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.settings.AppSettings
import dev.daymark.models.setup.SetupDraft
import dev.daymark.plan.WeeklySchedule
import dev.daymark.presentation.features.setup.OnboardingActions
import dev.daymark.presentation.features.setup.OnboardingHeading
import dev.daymark.presentation.features.setup.OnboardingLayout
import dev.daymark.presentation.features.setup.SetupIssue
import dev.daymark.presentation.features.setup.SetupStepContent
import dev.daymark.presentation.features.setup.SetupUiState
import dev.daymark.presentation.features.setup.component.SetupActions
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.WithPlatformLocale
import dev.daymark.ui.theme.DaymarkTheme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime


@Preview(name = "Welcome light")
@Preview(name = "Welcome narrow large text", widthDp = 320, fontScale = 2f)
@Composable
private fun SetupScreenPreview() {
    val state = SetupUiState(ready = true, preferencesReady = true)
    val controls = OnboardingActions({}, {}, {}, {}, {}, {}) {}
    DaymarkTheme(ThemePreference.LIGHT) {
        OnboardingLayout(state, controls) {
            OnboardingHeading(state.draft.step)
            SetupStepContent(
                state.draft.step,
                state,
                SetupActions({}, {}, {}, {}, {}),
                HostPlatform.ANDROID
            ) {}
        }
    }
}

@Preview(name = "Dark Theme")
@Composable
private fun SetupScreenDarkPreview() {
    val state = SetupUiState(ready = true, preferencesReady = true)
    val controls = OnboardingActions({}, {}, {}, {}, {}, {}) {}
    DaymarkTheme(ThemePreference.DARK) {
        OnboardingLayout(state, controls) {
            OnboardingHeading(state.draft.step)
            SetupStepContent(
                state.draft.step,
                state,
                SetupActions({}, {}, {}, {}, {}),
                HostPlatform.ANDROID
            ) {}
        }
    }
}

@Preview(name = "Narrow large text", widthDp = 320, fontScale = 2f)
@Preview(name = "Profile reduced height", widthDp = 360, heightDp = 400)
@Composable
private fun SetupScreenNarrowLargeTextPreview() {
    val state = SetupUiState(
        ready = true,
        preferencesReady = true,
        draft = SetupDraft(step = SetupStep.PROFILE)
    )
    val controls = OnboardingActions({}, {}, {}, {}, {}, {}) {}
    DaymarkTheme(ThemePreference.LIGHT) {
        OnboardingLayout(state, controls) {
            OnboardingHeading(state.draft.step)
            SetupStepContent(
                state.draft.step,
                state,
                SetupActions({}, {}, {}, {}, {}),
                HostPlatform.ANDROID
            ) {}
        }
    }
}

@Preview(name = "Attendance period")
@Preview(name = "Attendance period large text", widthDp = 320, fontScale = 2f)
@Composable
private fun SetupAttendancePeriodPreview() {
    SetupActionsPreview(
        ThemePreference.LIGHT,
        AppLanguage.EN,
        SetupDraft(
            step = SetupStep.ATTENDANCE_PERIOD,
            attendanceClosingDay = 20,
            vacationYearBasis = VacationYearBasis.ATTENDANCE_PERIOD
        )
    )
}

@Preview(name = "Preferences light")
@Preview(name = "Preferences narrow large text", widthDp = 320, fontScale = 2f)
@Composable
private fun SetupPreferencesLightPreview() {
    SetupActionsPreview(
        theme = ThemePreference.LIGHT,
        language = AppLanguage.EN,
        draft = SetupDraft(step = SetupStep.PREFERENCE)
    )
}

@Preview(name = "Preferences dark")
@Composable
private fun SetupPreferencesDarkPreview() {
    SetupActionsPreview(
        theme = ThemePreference.DARK,
        language = AppLanguage.EN,
        draft = SetupDraft(step = SetupStep.PREFERENCE)
    )
}

@Preview(name = "Preferences Arabic RTL", locale = "ar")
@Composable
private fun SetupPreferencesArabicPreview() {
    SetupActionsPreview(
        theme = ThemePreference.LIGHT,
        language = AppLanguage.AR,
        draft = SetupDraft(step = SetupStep.PREFERENCE)
    )
}

@Composable
private fun SetupActionsPreview(
    theme: ThemePreference,
    language: AppLanguage,
    draft: SetupDraft,
    issue: SetupIssue? = null
) {
    val state = SetupUiState(
        ready = true, preferencesReady = true, draftSaved = true,
        draft = draft,
        issue = issue,
        preferences = AppSettings(
            language = if (language == AppLanguage.AR) LanguagePreference.AR else LanguagePreference.EN,
            theme = theme
        )
    )
    WithPlatformLocale(language) {
        CompositionLocalProvider(
            LocalAppLanguage provides language,
            LocalLayoutDirection provides if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
        ) {
            DaymarkTheme(theme) { SetupActionsPreviewContent(state) }
        }
    }
}

@Composable
private fun SetupActionsPreviewContent(state: SetupUiState) {
    OnboardingLayout(state, OnboardingActions({}, {}, {}, {}, {}, {}) {}) {
        OnboardingHeading(state.draft.step)
        SetupStepContent(
            state.draft.step, state, SetupActions({}, {}, {}, {}, {}), HostPlatform.ANDROID
        ) {}
    }
}

@Preview(name = "Welcome Arabic RTL", locale = "ar")
@Composable
private fun SetupWelcomeArabicPreview() {
    SetupActionsPreview(ThemePreference.LIGHT, AppLanguage.AR, SetupDraft(step = SetupStep.WELCOME))
}

private val reviewPreviewDraft = SetupDraft(
    displayName = "Alex",
    category = TrackerCategory.WORK,
    placeName = "Office",
    place = RegisteredPlace(0.0, 0.0, 100, 1),
    schedule = WeeklySchedule(
        days = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY
        ),
        arrival = LocalTime(9, 0),
        overrides = mapOf(DayOfWeek.FRIDAY to LocalTime(8, 30)),
        graceMinutes = 5,
        zoneId = "Africa/Cairo",
        departure = LocalTime(17, 0),
        departureOverrides = mapOf(DayOfWeek.THURSDAY to LocalTime(16, 0))
    ),
    openingBalanceText = "15",
    step = SetupStep.REVIEW
)

@Preview(name = "Review light")
@Composable
private fun SetupReviewLightPreview() {
    SetupActionsPreview(ThemePreference.LIGHT, AppLanguage.EN, reviewPreviewDraft)
}

@Preview(name = "Review dark")
@Composable
private fun SetupReviewDarkPreview() {
    SetupActionsPreview(ThemePreference.DARK, AppLanguage.EN, reviewPreviewDraft)
}

@Preview(name = "Review narrow large text", widthDp = 320, fontScale = 2f)
@Composable
private fun SetupReviewLargeTextPreview() {
    SetupActionsPreview(ThemePreference.LIGHT, AppLanguage.EN, reviewPreviewDraft)
}

@Preview(name = "Review retry in Arabic", locale = "ar", widthDp = 320, fontScale = 2f)
@Composable
private fun SetupReviewRetryPreview() {
    SetupActionsPreview(
        theme = ThemePreference.DARK,
        language = AppLanguage.AR,
        draft = reviewPreviewDraft,
        issue = SetupIssue.COMPLETION_FAILED
    )
}
