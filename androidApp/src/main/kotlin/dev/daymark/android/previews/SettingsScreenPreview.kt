package dev.daymark.android.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.daymark.app.HostPlatform
import dev.daymark.history.AttendancePeriodSettings
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
import dev.daymark.settings.ThemePreference
import dev.daymark.ui.theme.DaymarkTheme

@Preview(name = "Settings with a custom attendance period")
@Preview(name = "Settings vacation year at large text", widthDp = 320, fontScale = 2f)
@Composable
private fun SettingsScreenAndroidPreview() {
    val sampleActions = SettingsActions(
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
            actions = sampleActions
        ) { AttendanceSettingsSample() }
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
    DaymarkTheme(ThemePreference.DARK) {
        SettingsContent(
            state = SettingsUiState(
                ready = true,
                resultNotificationsSupported = true,
                widgetStatus = dev.daymark.widgets.WidgetSetupStatus.ADDED,
                settings = AppSettings(displayName = "Alex Lee", theme = ThemePreference.DARK),
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
            actions = sampleActions
        ) { AttendanceSettingsSample() }
    }
}


@Composable
private fun AttendanceSettingsSample() {
    val saved = PlanChange(sampleSchedule, TrackerCategory.WORK, RegisteredPlace(30.04, 31.24, 150, 1))
    AttendanceSettingsContent(
        state = AttendanceSettingsUiState(plan = PlanState(saved, null, null), draft = saved, ready = true),
        actions = AttendanceSettingsActions({}, {}, {}, {}, {}, {})
    )
}
