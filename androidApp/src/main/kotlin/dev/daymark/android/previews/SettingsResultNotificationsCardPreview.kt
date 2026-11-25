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
import dev.daymark.notifications.ResultNotificationStatus
import dev.daymark.presentation.features.settings.SettingsUiState
import dev.daymark.presentation.features.settings.component.ResultNotificationsEditorState
import dev.daymark.presentation.features.settings.component.SettingsResultNotificationsCard
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.ThemePreference
import dev.daymark.ui.theme.DaymarkTheme

@Preview(name = "Result Notifications Light Editing")
@Composable
private fun SettingsResultNotificationsCardLightEnabledPreview() {
    DaymarkTheme(preference = ThemePreference.LIGHT) {
        SettingsResultNotificationsCard(
            state = SettingsUiState(
                ready = true,
                resultNotificationsSupported = true,
                androidResultStatus = ResultNotificationStatus(appAllowed = true),
                settings = AppSettings(
                    displayName = "Alex Lee",
                    language = LanguagePreference.EN,
                    theme = ThemePreference.LIGHT,
                    resultNotificationsEnabled = true,
                    resultFailureSoundEnabled = true
                )
            ),
            onToggleNotifications = {},
            modifier = Modifier.padding(all = 16.dp),
            initialEditorState = ResultNotificationsEditorState.EDITING
        )
    }
}

@Preview(name = "Result Notifications Dark Disabled")
@Composable
private fun SettingsResultNotificationsCardDarkDisabledPreview() {
    DaymarkTheme(preference = ThemePreference.DARK) {
        SettingsResultNotificationsCard(
            state = SettingsUiState(
                ready = true,
                resultNotificationsSupported = true,
                androidResultStatus = ResultNotificationStatus(appAllowed = true),
                settings = AppSettings(
                    displayName = "Alex Lee",
                    language = LanguagePreference.EN,
                    theme = ThemePreference.DARK,
                    resultNotificationsEnabled = false
                )
            ),
            onToggleNotifications = {},
            modifier = Modifier.padding(all = 16.dp)
        )
    }
}

@Preview(name = "Result Notifications Permission Denied")
@Composable
private fun SettingsResultNotificationsCardPermissionDeniedPreview() {
    DaymarkTheme(preference = ThemePreference.LIGHT) {
        SettingsResultNotificationsCard(
            state = SettingsUiState(
                ready = true,
                resultNotificationsSupported = true,
                androidResultStatus = ResultNotificationStatus(appAllowed = false),
                settings = AppSettings(
                    displayName = "Alex Lee",
                    language = LanguagePreference.EN,
                    theme = ThemePreference.LIGHT,
                    resultNotificationsEnabled = true
                )
            ),
            onToggleNotifications = {},
            modifier = Modifier.padding(all = 16.dp)
        )
    }
}

@Preview(
    name = "Result Notifications Arabic RTL Busy Editor",
    widthDp = 320,
    fontScale = 1.5f,
    locale = "ar"
)
@Composable
private fun SettingsResultNotificationsCardArabicPreview() {
    CompositionLocalProvider(
        LocalAppLanguage provides AppLanguage.AR,
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        DaymarkTheme(preference = ThemePreference.DARK) {
            SettingsResultNotificationsCard(
                state = SettingsUiState(
                    ready = true,
                    busy = true,
                    resultNotificationsSupported = true,
                    androidResultStatus = ResultNotificationStatus(appAllowed = true, legacyChannelOnly = true),
                    settings = AppSettings(
                        displayName = "أحمد",
                        language = LanguagePreference.AR,
                        theme = ThemePreference.DARK,
                        resultNotificationsEnabled = true
                    )
                ),
                onToggleNotifications = {},
                modifier = Modifier.padding(all = 16.dp),
                initialEditorState = ResultNotificationsEditorState.EDITING_WITH_HELP
            )
        }
    }
}
