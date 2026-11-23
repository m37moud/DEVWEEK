package dev.daymark.android.previews

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.daymark.history.AttendancePeriodSettings
import dev.daymark.history.VacationYearBasis
import dev.daymark.models.plan.AnnualVacationPolicy
import dev.daymark.models.plan.AnnualVacationSettings
import dev.daymark.models.settings.AppSettings
import dev.daymark.presentation.features.settings.AnnualVacationSettingsUiState
import dev.daymark.presentation.features.settings.SettingsActions
import dev.daymark.presentation.features.settings.SettingsUiState
import dev.daymark.presentation.features.settings.component.ReportActions
import dev.daymark.presentation.features.settings.component.SettingsAttendancePeriodCard
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.WithPlatformLocale
import dev.daymark.ui.theme.DaymarkTheme
import kotlinx.datetime.LocalDate

private val samplePreviewActions = SettingsActions(
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
        retryReport = {},
        deleteLocalData = {}
    )
)

private val sampleVacationConfiguration = AnnualVacationSettings(
    active = AnnualVacationPolicy(
        effectiveYear = 2026,
        annualAllowanceDays = 25,
        carryOverUnusedDays = true
    ),
    pending = null,
    nextEffectiveYear = 2027
)

private val sampleAttendanceDate = LocalDate(year = 2026, month = 11, day = 19)

private val sampleCustomPeriod = AttendancePeriodSettings(
    closingDay = 20,
    vacationYearBasis = VacationYearBasis.ATTENDANCE_PERIOD
)

@Preview(name = "Attendance Period Card Light", locale = "en")
@Composable
private fun SettingsAttendancePeriodCardLightPreview() {
    AttendancePeriodSample(
        theme = ThemePreference.LIGHT,
        period = AttendancePeriodSettings(),
        language = AppLanguage.EN
    )
}

@Preview(name = "Attendance Period Card Dark Custom", locale = "en")
@Composable
private fun SettingsAttendancePeriodCardDarkPreview() {
    AttendancePeriodSample(
        theme = ThemePreference.DARK,
        period = sampleCustomPeriod,
        language = AppLanguage.EN
    )
}

@Preview(name = "Attendance Period Editor Arabic RTL", locale = "ar")
@Composable
private fun SettingsAttendancePeriodCardArabicPreview() {
    AttendancePeriodSample(
        theme = ThemePreference.DARK,
        period = sampleCustomPeriod,
        language = AppLanguage.AR,
        initiallyEditing = true
    )
}

@Preview(name = "Attendance Period Editor Narrow Large Font", locale = "ar", widthDp = 320, fontScale = 1.5f)
@Composable
private fun SettingsAttendancePeriodCardNarrowPreview() {
    val density = LocalDensity.current.density
    CompositionLocalProvider(LocalDensity provides Density(density = density, fontScale = 1.5f)) {
        Box(modifier = Modifier.widthIn(max = 320.dp).verticalScroll(state = rememberScrollState())) {
            AttendancePeriodSample(
                theme = ThemePreference.LIGHT,
                period = sampleCustomPeriod,
                language = AppLanguage.AR,
                initiallyEditing = true
            )
        }
    }
}

@Composable
private fun AttendancePeriodSample(
    theme: ThemePreference,
    period: AttendancePeriodSettings,
    language: AppLanguage,
    initiallyEditing: Boolean = false
) {
    val direction = if (language == AppLanguage.AR) LayoutDirection.Rtl else LayoutDirection.Ltr
    WithPlatformLocale(language = language) {
        CompositionLocalProvider(LocalAppLanguage provides language, LocalLayoutDirection provides direction) {
            DaymarkTheme(preference = theme) {
                SettingsAttendancePeriodCard(
                    state = SettingsUiState(
                        ready = true,
                        attendanceDate = sampleAttendanceDate,
                        settings = AppSettings(attendancePeriod = period),
                        annualVacation = AnnualVacationSettingsUiState(configuration = sampleVacationConfiguration)
                    ),
                    actions = samplePreviewActions,
                    modifier = Modifier.safeDrawingPadding().padding(all = 16.dp),
                    initiallyEditing = initiallyEditing
                )
            }
        }
    }
}
