package dev.daymark.presentation.features.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import dev.daymark.presentation.features.today.component.TodayErrorCallout
import dev.daymark.presentation.features.today.component.TodayHeroCard
import dev.daymark.presentation.features.today.component.TodayMonthMetrics
import dev.daymark.presentation.features.today.component.TodayNextAttendanceBanner
import dev.daymark.presentation.features.today.component.TodayReportStatus
import dev.daymark.presentation.features.today.component.TodayWeekStrip
import dev.daymark.resources.Res
import dev.daymark.resources.load_failed
import dev.daymark.resources.welcome_name
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.isolated
import dev.daymark.settings.str
import dev.daymark.ui.theme.DaymarkTheme
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

private data class TodayActions(
    val checkAttendance: () -> Unit,
    val openLocationSettings: () -> Unit
)

@Composable
fun TodayScreen(model: TodayViewModel, displayName: String, onHistory: () -> Unit) {
    val state by model.uiState.collectAsStateWithLifecycle()
    LifecycleStartEffect(model) {
        model.setActive(true)
        onStopOrDispose { model.setActive(false) }
    }
    val actions = remember(model) {
        TodayActions(model::checkAttendance, model::openLocationSettings)
    }
    TodayContent(state, displayName, onHistory, actions)
}

@Composable
private fun TodayContent(
    state: TodayUiState,
    displayName: String,
    onHistory: () -> Unit,
    actions: TodayActions
) {
    val dashboard = state.dashboard
    val report = state.report
    val language = LocalAppLanguage.current
    val summaryReady = dashboard.ready && (dashboard.date == null || dashboard.date == state.date)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("today-root"),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.date?.let {
                Text(
                    text = DisplayFormats.date(it, language),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = str(Res.string.welcome_name, isolated(displayName)),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        TodayHeroCard(
            state = state,
            summaryReady = summaryReady,
            language = language,
            onCheckAttendance = actions.checkAttendance,
            onHistory = onHistory
        )

        state.error?.let { rejection ->
            TodayErrorCallout(
                error = rejection,
                onOpenSettings = actions.openLocationSettings
            )
        }

        dashboard.takeIf { summaryReady }?.let { summary ->
            if (summary.failed) {
                Text(
                    text = str(Res.string.load_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TodayMonthMetrics(
                            summary = summary,
                            language = language,
                            onHistory = onHistory
                        )

                        TodayWeekStrip(
                            week = summary.week,
                            currentDate = summary.date,
                            language = language
                        )
                    }
                }

                summary.nextAttendance?.let { next ->
                    TodayNextAttendanceBanner(
                        next = next,
                        language = language
                    )
                }
            }
        }

        TodayReportStatus(
            report = report,
            language = language
        )
    }
}

@Preview
@Preview(name = "Awaiting narrow large text", widthDp = 320, fontScale = 2f)
@Composable
private fun TodayScreenPreview() {
    val actions = TodayActions({}, {})
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
                30 -> AttendanceStatus.SCHEDULED
                else -> AttendanceStatus.DAY_OFF
            },
            planned = dayNumber > 27
        )
    }
    val dashboard = TodayDashboard(
        ready = true,
        date = sampleDate,
        placeName = "Harbor Office",
        present = 14,
        late = 2,
        absent = 1,
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
    val actions = TodayActions({}, {})
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
    val actions = TodayActions({}, {})
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
