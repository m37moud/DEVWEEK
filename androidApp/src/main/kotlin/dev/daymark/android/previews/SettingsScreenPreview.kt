package dev.daymark.android.previews

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import dev.daymark.app.HostPlatform
import dev.daymark.history.AttendancePeriodSettings
import dev.daymark.models.ConfigurationHealth
import dev.daymark.models.attendance.RegisteredPlace
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.plan.AnnualVacationPolicy
import dev.daymark.models.plan.AnnualVacationSettings
import dev.daymark.models.plan.PlanChange
import dev.daymark.models.plan.PlanState
import dev.daymark.models.settings.AppSettings
import dev.daymark.presentation.features.settings.AnnualVacationSettingsUiState
import dev.daymark.presentation.features.settings.AttendanceSettingsUiState
import dev.daymark.presentation.features.settings.component.AttendanceSettingsActions
import dev.daymark.presentation.features.settings.component.AttendanceSettingsContent
import dev.daymark.presentation.features.settings.SettingsActions
import dev.daymark.presentation.features.settings.SettingsContent
import dev.daymark.presentation.features.settings.SettingsUiState
import dev.daymark.presentation.features.settings.component.ReportActions
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.WithPlatformLocale
import dev.daymark.ui.theme.DaymarkTheme
import dev.daymark.ui.theme.LocalConfigurationHealth
import kotlinx.datetime.LocalDate

@Preview(name = "Settings summaries and pending vacation")
@Preview(name = "Settings vacation year at large text", widthDp = 320, fontScale = 2f)
@Composable
private fun SettingsScreenAndroidPreview() {
    DaymarkTheme(ThemePreference.LIGHT) {
        SettingsContent(
            state = SettingsUiState(
                ready = true,
                permissionRequestFailed = true,
                resultNotificationsSupported = true,
                widgetStatus = dev.daymark.widgets.WidgetSetupStatus.NOT_ADDED,
                widgetCanPin = true,
                settings = AppSettings(displayName = "Alex Lee", attendancePeriod = AttendancePeriodSettings(20)),
                annualVacation = AnnualVacationSettingsUiState(
                    configuration = AnnualVacationSettings(
                        active = null,
                        pending = AnnualVacationPolicy(2027, 20, false),
                        nextEffectiveYear = 2027
                    )
                )
            ),
            platform = HostPlatform.ANDROID,
            actions = settingsPreviewActions
        ) { AttendanceSettingsSample() }
    }
}

@Preview(name = "Settings iOS dark", locale = "ar", widthDp = 320)
@Composable
private fun SettingsScreenIosDarkPreview() {
    WithPlatformLocale(AppLanguage.AR) {
        CompositionLocalProvider(
            LocalAppLanguage provides AppLanguage.AR,
            LocalLayoutDirection provides LayoutDirection.Rtl,
            LocalConfigurationHealth provides ConfigurationHealth(reminderThrough = LocalDate(2027, 1, 15))
        ) {
            DaymarkTheme(ThemePreference.DARK) {
                SettingsContent(
                    state = SettingsUiState(
                        ready = true,
                        resultNotificationsSupported = true,
                        widgetStatus = dev.daymark.widgets.WidgetSetupStatus.ADDED,
                        settings = AppSettings(
                            displayName = "أحمد / Alex",
                            language = LanguagePreference.AR,
                            theme = ThemePreference.DARK,
                            remindersEnabled = true
                        ),
                        reportBusy = true,
                        annualVacation = AnnualVacationSettingsUiState(
                            configuration = AnnualVacationSettings(
                                active = AnnualVacationPolicy(2027, 20, true),
                                pending = null,
                                nextEffectiveYear = 2028
                            )
                        )
                    ),
                    platform = HostPlatform.IOS,
                    actions = settingsPreviewActions
                ) { AttendanceSettingsSample() }
            }
        }
    }
}



@Composable
private fun AttendanceSettingsSample() {
    val saved = PlanChange(sampleSchedule, TrackerCategory.WORK, RegisteredPlace(30.04, 31.24, 150, 1))
    AttendanceSettingsContent(
        state = AttendanceSettingsUiState(plan = PlanState(saved, null, null), draft = saved, ready = true),
        actions = AttendanceSettingsActions({}, {}, {}, {}, {}, {}, {})
    )
}

private val settingsPreviewActions = SettingsActions(
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
    reports = ReportActions({}, {}, {}, {}, {}, {}, {}, {})
)
