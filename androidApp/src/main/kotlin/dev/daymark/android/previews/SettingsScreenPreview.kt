package dev.daymark.android.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.daymark.app.HostPlatform
import dev.daymark.models.settings.AppSettings
import dev.daymark.presentation.features.settings.SettingsActions
import dev.daymark.presentation.features.settings.SettingsContent
import dev.daymark.presentation.features.settings.SettingsUiState
import dev.daymark.presentation.features.settings.component.ReportActions
import dev.daymark.settings.ThemePreference
import dev.daymark.ui.theme.DaymarkTheme

@Preview
@Composable
private fun SettingsScreenAndroidPreview() {
    val sampleActions = SettingsActions(
        addWidget = {},
        refreshWidget = {},
        setDisplayName = {},
        setLanguage = {},
        setTheme = {},
        setAutomation = {},
        setRemindersEnabled = {},
        requestReminders = {},
        setResultNotificationsEnabled = {},
        openSystemSettings = {},
        reports = ReportActions({}, {}, {}, {}, {}, {}, {}, {})
    )
    DaymarkTheme(ThemePreference.LIGHT) {
        SettingsContent(
            state = SettingsUiState(
                ready = true,
                permissionRequestFailed = true,
                resultNotificationsSupported = true,
                widgetStatus = dev.daymark.widgets.WidgetSetupStatus.NOT_ADDED,
                widgetCanPin = true,
                settings = AppSettings(displayName = "Alex Lee")
            ),
            platform = HostPlatform.ANDROID,
            actions = sampleActions
        )
    }
}

@Preview
@Composable
private fun SettingsScreenIosDarkPreview() {
    val sampleActions = SettingsActions(
        addWidget = {},
        refreshWidget = {},
        setDisplayName = {},
        setLanguage = {},
        setTheme = {},
        setAutomation = {},
        setRemindersEnabled = {},
        requestReminders = {},
        setResultNotificationsEnabled = {},
        openSystemSettings = {},
        reports = ReportActions({}, {}, {}, {}, {}, {}, {}, {})
    )
    DaymarkTheme(ThemePreference.DARK) {
        SettingsContent(
            state = SettingsUiState(
                ready = true,
                resultNotificationsSupported = true,
                widgetStatus = dev.daymark.widgets.WidgetSetupStatus.ADDED,
                settings = AppSettings(displayName = "Alex Lee", theme = ThemePreference.DARK),
                reportBusy = true
            ),
            platform = HostPlatform.IOS,
            actions = sampleActions
        )
    }
}
