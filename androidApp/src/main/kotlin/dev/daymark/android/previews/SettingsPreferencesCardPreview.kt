package dev.daymark.android.previews

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.daymark.models.settings.AppSettings
import dev.daymark.presentation.features.settings.SettingsActions
import dev.daymark.presentation.features.settings.SettingsUiState
import dev.daymark.presentation.features.settings.component.ReportActions
import dev.daymark.presentation.features.settings.component.SettingsPreferencesCard
import dev.daymark.presentation.features.settings.component.SettingsPreferencesSection
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.WithPlatformLocale
import dev.daymark.ui.theme.DaymarkTheme

private val sampleSettingsActions = SettingsActions(
    addWidget = {},
    refreshWidget = {},
    setDisplayName = {},
    setLanguage = {},
    setTheme = {},
    saveAnnualVacationPolicy = { _, _, _ -> },
    setAutomation = {},
    setRemindersEnabled = {},
    requestReminders = {},
    setResultNotificationsEnabled = {},
    openSystemSettings = {},
    previewPeriod = {},
    confirmPeriodChange = {},
    dismissPeriodReview = {},
    reports = ReportActions(
        connectReport = {},
        reconnectReport = {},
        selectReport = {},
        rebuildReport = {},
        createReplacementReport = {},
        disconnectReport = {},
        retryReport = {}
    ) {}
)

@Preview(name = "Preferences Card Light")
@Composable
private fun SettingsPreferencesCardLightPreview() {
    DaymarkTheme(preference = ThemePreference.LIGHT) {
        SettingsPreferencesCard(
            languagePreference = LanguagePreference.EN,
            themePreference = ThemePreference.SYSTEM,
            onLanguageChange = {},
            onThemeChange = {},
            busy = false,
            modifier = Modifier.padding(all = 16.dp)
        )
    }
}

@Preview(name = "Preferences Card Dark")
@Composable
private fun SettingsPreferencesCardDarkPreview() {
    DaymarkTheme(preference = ThemePreference.DARK) {
        SettingsPreferencesCard(
            languagePreference = LanguagePreference.AR,
            themePreference = ThemePreference.DARK,
            onLanguageChange = {},
            onThemeChange = {},
            busy = false,
            modifier = Modifier.padding(all = 16.dp)
        )
    }
}

@Preview(name = "Preferences Section Collapsed Light")
@Composable
private fun SettingsPreferencesSectionLightPreview() {
    DaymarkTheme(preference = ThemePreference.LIGHT) {
        SettingsPreferencesSection(
            state = SettingsUiState(
                ready = true,
                settings = AppSettings(
                    displayName = "Alex Lee",
                    language = LanguagePreference.EN,
                    theme = ThemePreference.SYSTEM
                )
            ),
            actions = sampleSettingsActions
        )
    }
}

@Preview(name = "Preferences Options Arabic RTL", locale = "ar")
@Composable
private fun SettingsPreferencesOptionsArabicPreview() {
    WithPlatformLocale(language = AppLanguage.AR) {
        CompositionLocalProvider(
            LocalAppLanguage provides AppLanguage.AR,
            LocalLayoutDirection provides LayoutDirection.Rtl
        ) {
            DaymarkTheme(preference = ThemePreference.DARK) {
                SettingsPreferencesCard(
                    languagePreference = LanguagePreference.AR,
                    themePreference = ThemePreference.DARK,
                    onLanguageChange = {},
                    onThemeChange = {},
                    busy = false
                )
            }
        }
    }
}

@Preview(name = "Preferences Options Narrow & Large Font", widthDp = 320, fontScale = 2f)
@Composable
private fun SettingsPreferencesOptionsNarrowPreview() {
    DaymarkTheme(preference = ThemePreference.LIGHT) {
        SettingsPreferencesCard(
            languagePreference = LanguagePreference.SYSTEM,
            themePreference = ThemePreference.LIGHT,
            onLanguageChange = {},
            onThemeChange = {},
            busy = false
        )
    }
}

@Preview(name = "Preferences Options Disabled")
@Composable
private fun SettingsPreferencesOptionsDisabledPreview() {
    DaymarkTheme(preference = ThemePreference.LIGHT) {
        SettingsPreferencesCard(
            languagePreference = LanguagePreference.EN,
            themePreference = ThemePreference.SYSTEM,
            onLanguageChange = {},
            onThemeChange = {},
            busy = true
        )
    }
}
