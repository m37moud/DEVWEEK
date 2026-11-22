package dev.daymark.android.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.daymark.history.AttendancePeriodSettings
import dev.daymark.history.HistoryMetrics
import dev.daymark.models.TodayDashboard
import dev.daymark.models.WeekDaySummary
import dev.daymark.models.attendance.AttendanceSource
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.models.attendance.CheckRejection
import dev.daymark.models.attendance.DayKey
import dev.daymark.models.attendance.DayOutcome
import dev.daymark.models.attendance.DayPlan
import dev.daymark.models.attendance.RegisteredPlace
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.reporting.ReportHealth
import dev.daymark.models.plan.VacationBalanceSummary
import dev.daymark.presentation.features.today.TodayVacationBalanceUiState
import dev.daymark.presentation.features.today.TodayActions
import dev.daymark.presentation.features.today.TodayContent
import dev.daymark.presentation.features.today.TodayUiState
import dev.daymark.settings.ThemePreference
import dev.daymark.ui.theme.DaymarkTheme
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

@Preview
@Preview(name = "Awaiting narrow large text", widthDp = 320, fontScale = 2f)
@Composable
private fun TodayScreenPreview() {
    val actions = TodayActions({}, {}, { _, _ -> }, { _, _ -> }, {})
    val sampleDate = LocalDate(2026, 10, 27)
    val plan = DayPlan(
        key = DayKey("preview-tracker", sampleDate),
        category = TrackerCategory.WORK,
        arrival = Instant.fromEpochMilliseconds(1793084400000),
        deadline = Instant.fromEpochMilliseconds(1793084700000),
        zoneId = "Africa/Cairo",
        scheduleVersion = 1L,
        place = RegisteredPlace(30.0, 31.0, 100, 1L),
        isScheduled = true
    )
    val weekDays = (26..31).map { dayNumber ->
        val date = LocalDate(2026, 10, dayNumber)
        WeekDaySummary(
            date = date,
            status = when (dayNumber) {
                26 -> AttendanceStatus.PRESENT
                27 -> AttendanceStatus.AWAITING_CHECK
                28 -> AttendanceStatus.HOLIDAY
                29 -> AttendanceStatus.VACATION
                30 -> AttendanceStatus.WORK_MISSION
                else -> AttendanceStatus.DAY_OFF
            },
            planned = dayNumber > 27
        )
    }
    val dashboard = TodayDashboard(
        ready = true,
        date = sampleDate,
        period = AttendancePeriodSettings(20).periodFor(sampleDate),
        placeName = "Harbor Office",
        metrics = HistoryMetrics(
            attendance = 16,
            late = 2,
            absent = 1,
            leaveRest = 3,
            holidays = 1,
            workMissions = 1
        ),
        week = weekDays,
        nextAttendance = plan.copy(
            key = DayKey("preview-tracker", LocalDate(2026, 10, 30)),
            arrival = Instant.fromEpochMilliseconds(1793343600000),
            deadline = Instant.fromEpochMilliseconds(1793343900000)
        )
    )

    DaymarkTheme(ThemePreference.LIGHT) {
        TodayContent(
            state = TodayUiState(
                ready = true,
                active = true,
                activeCategory = TrackerCategory.WORK,
                vacationBalance = TodayVacationBalanceUiState(
                    summary = VacationBalanceSummary(20, 7, 13, year = 2027)
                ),
                date = sampleDate,
                plan = plan,
                canCheck = true,
                outcome = DayOutcome(
                    status = AttendanceStatus.AWAITING_CHECK,
                    source = AttendanceSource.NONE,
                    confirmedAt = null,
                    offReason = null
                ),
                dashboard = dashboard,
                report = ReportHealth()
            ),
            displayName = "Alex",
            onHistory = {},
            actions = actions
        )
    }
}

@Preview
@Preview(name = "Corrected narrow large text", widthDp = 320, fontScale = 2f)
@Composable
private fun TodayScreenCorrectedDarkPreview() {
    val actions = TodayActions({}, {}, { _, _ -> }, { _, _ -> }, {})
    val sampleDate = LocalDate(2026, 10, 27)
    val plan = DayPlan(
        key = DayKey("preview-tracker", sampleDate),
        category = TrackerCategory.WORK,
        arrival = Instant.fromEpochMilliseconds(1793084400000),
        deadline = Instant.fromEpochMilliseconds(1793084700000),
        zoneId = "Africa/Cairo",
        scheduleVersion = 1L,
        place = RegisteredPlace(30.0, 31.0, 100, 1L),
        isScheduled = true
    )
    DaymarkTheme(ThemePreference.DARK) {
        TodayContent(
            state = TodayUiState(
                ready = true,
                active = true,
                activeCategory = TrackerCategory.WORK,
                vacationBalance = TodayVacationBalanceUiState(
                    summary = VacationBalanceSummary(20, 7, 13, year = 2027)
                ),
                date = sampleDate,
                plan = plan,
                canCheck = false,
                outcome = DayOutcome(
                    status = AttendanceStatus.PRESENT,
                    source = AttendanceSource.MANUAL_CORRECTION,
                    confirmedAt = Instant.fromEpochMilliseconds(1793084520000),
                    offReason = null
                ),
                dashboard = TodayDashboard(
                    ready = true,
                    date = sampleDate,
                    placeName = "Harbor Office"
                ),
                report = ReportHealth()
            ),
            displayName = "Alex",
            onHistory = {},
            actions = actions
        )
    }
}

@Preview
@Composable
private fun TodayScreenErrorPreview() {
    val actions = TodayActions({}, {}, { _, _ -> }, { _, _ -> })
    val sampleDate = LocalDate(2026, 10, 27)
    DaymarkTheme(ThemePreference.LIGHT) {
        TodayContent(
            state = TodayUiState(
                ready = true,
                active = true,
                date = sampleDate,
                error = CheckRejection.LOCATION_DENIED,
                dashboard = TodayDashboard(ready = true)
            ),
            displayName = "Alex",
            onHistory = {},
            actions = actions
        )
    }
}
