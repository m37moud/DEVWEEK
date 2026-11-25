package dev.daymark.android.previews

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.daymark.app.HostPlatform
import dev.daymark.location.LocationCapabilities
import dev.daymark.models.ConfigurationHealth
import dev.daymark.models.settings.AppSettings
import dev.daymark.presentation.features.settings.SettingsActions
import dev.daymark.presentation.features.settings.SettingsUiState
import dev.daymark.presentation.features.settings.component.ReportActions
import dev.daymark.presentation.features.settings.component.SettingsTrackingCard
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.WithPlatformLocale
import dev.daymark.ui.theme.DaymarkTheme
import dev.daymark.ui.theme.LocalConfigurationHealth
import kotlinx.datetime.LocalDate

private val previewSettingsActions = SettingsActions(
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

private val enabledLocationCapabilities = LocationCapabilities(
    foregroundGranted = true,
    backgroundGranted = true,
    preciseGranted = true,
    servicesEnabled = true
)

private val disabledLocationCapabilities = LocationCapabilities(
    foregroundGranted = false,
    backgroundGranted = false,
    preciseGranted = false,
    servicesEnabled = false
)

@Preview(name = "Tracking Card Android Light Summary")
@Composable
private fun SettingsTrackingCardAndroidLightSummaryPreview() {
    DaymarkTheme(preference = ThemePreference.LIGHT) {
        SettingsTrackingCard(
            platform = HostPlatform.ANDROID,
            state = SettingsUiState(
                ready = true,
                automaticEnabled = true,
                capabilities = enabledLocationCapabilities,
                settings = AppSettings(
                    automaticAttemptLimit = 2,
                    displayName = "Alex Lee",
                    language = LanguagePreference.EN,
                    theme = ThemePreference.LIGHT
                )
            ),
            actions = previewSettingsActions,
            modifier = Modifier.padding(all = 16.dp)
        )
    }
}

@Preview(name = "Tracking Card Android Dark")
@Composable
private fun SettingsTrackingCardAndroidDarkPreview() {
    DaymarkTheme(preference = ThemePreference.DARK) {
        SettingsTrackingCard(
            platform = HostPlatform.ANDROID,
            state = SettingsUiState(
                ready = true,
                automaticEnabled = false,
                capabilities = disabledLocationCapabilities,
                settings = AppSettings(
                    displayName = "Alex Lee",
                    language = LanguagePreference.EN,
                    theme = ThemePreference.DARK
                )
            ),
            actions = previewSettingsActions,
            modifier = Modifier.padding(all = 16.dp)
        )
    }
}

@Preview(name = "Tracking Card Warning and Attention")
@Composable
private fun SettingsTrackingCardWarningPreview() {
    CompositionLocalProvider(
        LocalConfigurationHealth provides ConfigurationHealth(
            scheduleOk = false,
            remindersOk = false,
            reminderThrough = LocalDate(year = 2027, month = kotlinx.datetime.Month.JANUARY, day = 15)
        )
    ) {
        DaymarkTheme(preference = ThemePreference.LIGHT) {
            SettingsTrackingCard(
                platform = HostPlatform.ANDROID,
                state = SettingsUiState(
                    ready = true,
                    permissionDenied = true,
                    automaticEnabled = false,
                    capabilities = disabledLocationCapabilities,
                    settings = AppSettings(
                        displayName = "Alex Lee",
                        language = LanguagePreference.EN,
                        theme = ThemePreference.LIGHT
                    )
                ),
                actions = previewSettingsActions,
                modifier = Modifier.padding(all = 16.dp)
            )
        }
    }
}

@Preview(name = "Tracking Card iOS Arabic RTL", locale = "ar")
@Composable
private fun SettingsTrackingCardIosArabicPreview() {
    WithPlatformLocale(language = AppLanguage.AR) {
        CompositionLocalProvider(
            LocalAppLanguage provides AppLanguage.AR,
            LocalLayoutDirection provides LayoutDirection.Rtl,
            LocalConfigurationHealth provides ConfigurationHealth(
                reminderThrough = LocalDate(year = 2027, month = kotlinx.datetime.Month.JANUARY, day = 15)
            )
        ) {
            DaymarkTheme(preference = ThemePreference.DARK) {
                SettingsTrackingCard(
                    platform = HostPlatform.IOS,
                    state = SettingsUiState(
                        ready = true,
                        reminderPermissionGranted = true,
                        capabilities = enabledLocationCapabilities,
                        settings = AppSettings(
                            displayName = "أحمد",
                            language = LanguagePreference.AR,
                            theme = ThemePreference.DARK,
                            remindersEnabled = true
                        )
                    ),
                    actions = previewSettingsActions,
                    modifier = Modifier.padding(all = 16.dp)
                )
            }
        }
    }
}

@Preview(name = "Tracking Card Narrow & Large Font", widthDp = 320, fontScale = 2f)
@Composable
private fun SettingsTrackingCardNarrowLargeFontPreview() {
    DaymarkTheme(preference = ThemePreference.LIGHT) {
        SettingsTrackingCard(
            platform = HostPlatform.ANDROID,
            state = SettingsUiState(
                ready = true,
                automaticEnabled = true,
                capabilities = enabledLocationCapabilities,
                settings = AppSettings(
                    automaticAttemptLimit = 3,
                    displayName = "Alex Lee",
                    language = LanguagePreference.EN,
                    theme = ThemePreference.LIGHT
                )
            ),
            actions = previewSettingsActions,
            modifier = Modifier.padding(all = 8.dp)
        )
    }
}

@Preview(name = "Tracking Card Busy Disabled")
@Composable
private fun SettingsTrackingCardBusyPreview() {
    DaymarkTheme(preference = ThemePreference.LIGHT) {
        SettingsTrackingCard(
            platform = HostPlatform.ANDROID,
            state = SettingsUiState(
                ready = true,
                busy = true,
                automaticEnabled = true,
                capabilities = enabledLocationCapabilities,
                settings = AppSettings(
                    displayName = "Alex Lee",
                    language = LanguagePreference.EN,
                    theme = ThemePreference.LIGHT
                )
            ),
            actions = previewSettingsActions,
            modifier = Modifier.padding(all = 16.dp)
        )
    }
}
