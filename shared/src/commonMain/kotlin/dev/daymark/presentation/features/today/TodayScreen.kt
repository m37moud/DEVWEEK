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
import dev.daymark.models.DayDashboardDay
import dev.daymark.models.NextAttendance
import dev.daymark.models.NextAttendanceKey
import dev.daymark.models.TodayDashboard
import dev.daymark.models.attendance.AttendanceSource
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.models.attendance.CheckRejection
import dev.daymark.models.attendance.DayOutcome
import dev.daymark.models.attendance.DayPlan
import dev.daymark.models.attendance.DayPlanKey
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
import kotlinx.datetime.LocalTime

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
@Composable
private fun TodayScreenPreview() {
    val actions = TodayActions({}, {})
    val sampleDate = LocalDate(2026, 10, 27)
    val plan = DayPlan(
        key = DayPlanKey(1L, sampleDate),
        isScheduled = true,
        arrival = LocalTime(9, 0),
        deadline = LocalTime(9, 5),
        zoneId = "Africa/Cairo"
    )
    val weekDays = (26..31).map { dayNumber ->
        val date = LocalDate(2026, 10, dayNumber)
        DayDashboardDay(
            date = date,
            status = if (dayNumber == 27) AttendanceStatus.AWAITING_CHECK else AttendanceStatus.PRESENT,
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
        nextAttendance = NextAttendance(
            key = NextAttendanceKey(1L, LocalDate(2026, 10, 28)),
            arrival = LocalTime(9, 0),
            zoneId = "Africa/Cairo"
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
                    source = AttendanceSource.NONE
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
@Composable
private fun TodayScreenConfirmedDarkPreview() {
    val actions = TodayActions({}, {})
    val sampleDate = LocalDate(2026, 10, 27)
    val plan = DayPlan(
        key = DayPlanKey(1L, sampleDate),
        isScheduled = true,
        arrival = LocalTime(9, 0),
        deadline = LocalTime(9, 5),
        zoneId = "Africa/Cairo"
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
                    source = AttendanceSource.AUTOMATIC_GPS,
                    confirmedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(1793084520000)
                ),
                dashboard = TodayDashboard(ready = true, date = sampleDate, placeName = "Harbor Office"),
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
