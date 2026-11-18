package dev.daymark.android.previews

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import dev.daymark.app.HostPlatform
import dev.daymark.app.SetupStep
import dev.daymark.models.settings.AppSettings
import dev.daymark.models.setup.SetupDraft
import dev.daymark.presentation.features.setup.OnboardingActions
import dev.daymark.presentation.features.setup.OnboardingHeading
import dev.daymark.presentation.features.setup.OnboardingLayout
import dev.daymark.presentation.features.setup.SetupStepContent
import dev.daymark.presentation.features.setup.SetupUiState
import dev.daymark.presentation.features.setup.component.SetupActions
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.WithPlatformLocale
import dev.daymark.ui.theme.DaymarkTheme


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

@Preview(name = "Preferences light")
@Preview(name = "Preferences narrow large text", widthDp = 320, fontScale = 2f)
@Composable
private fun SetupPreferencesLightPreview() {
    SetupActionsPreview(ThemePreference.LIGHT, AppLanguage.EN, SetupStep.PREFERENCE)
}

@Preview(name = "Preferences dark")
@Composable
private fun SetupPreferencesDarkPreview() {
    SetupActionsPreview(ThemePreference.DARK, AppLanguage.EN, SetupStep.PREFERENCE)
}

@Preview(name = "Preferences Arabic RTL", locale = "ar")
@Composable
private fun SetupPreferencesArabicPreview() {
    SetupActionsPreview(ThemePreference.LIGHT, AppLanguage.AR, SetupStep.PREFERENCE)
}

@Composable
private fun SetupActionsPreview(theme: ThemePreference, language: AppLanguage, step: SetupStep) {
    val state = SetupUiState(
        ready = true, preferencesReady = true, draftSaved = true,
        draft = SetupDraft(step = step),
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
    SetupActionsPreview(ThemePreference.LIGHT, AppLanguage.AR, SetupStep.WELCOME)
}
