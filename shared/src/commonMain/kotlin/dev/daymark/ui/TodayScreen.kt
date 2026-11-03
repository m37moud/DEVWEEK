package dev.daymark.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.daymark.attendance.AttendanceSource
import dev.daymark.attendance.AttendanceStatus
import dev.daymark.attendance.CheckRejection
import dev.daymark.reporting.ReportHealth
import dev.daymark.reporting.SyncPassResult
import dev.daymark.resources.Res
import dev.daymark.resources.arrival_at
import dev.daymark.resources.attendance_zone
import dev.daymark.resources.check_attendance
import dev.daymark.resources.check_changed
import dev.daymark.resources.check_denied
import dev.daymark.resources.check_no_tracker
import dev.daymark.resources.checking_attendance
import dev.daymark.resources.confirmed_at
import dev.daymark.resources.deadline_at
import dev.daymark.resources.history
import dev.daymark.resources.load_failed
import dev.daymark.resources.location_before_window
import dev.daymark.resources.location_disabled
import dev.daymark.resources.location_inaccurate
import dev.daymark.resources.location_outside
import dev.daymark.resources.location_stale
import dev.daymark.resources.location_timeout
import dev.daymark.resources.location_unavailable
import dev.daymark.resources.month_counts
import dev.daymark.resources.month_so_far
import dev.daymark.resources.next_attendance
import dev.daymark.resources.not_tracked_date
import dev.daymark.resources.open_settings
import dev.daymark.resources.planned_date
import dev.daymark.resources.report_observed_sync
import dev.daymark.resources.report_ready
import dev.daymark.resources.source_automatic
import dev.daymark.resources.source_correction
import dev.daymark.resources.source_manual
import dev.daymark.resources.source_none
import dev.daymark.resources.source_time_off
import dev.daymark.resources.status_absent
import dev.daymark.resources.status_awaiting
import dev.daymark.resources.status_day_off
import dev.daymark.resources.status_holiday
import dev.daymark.resources.status_late
import dev.daymark.resources.status_present
import dev.daymark.resources.status_scheduled
import dev.daymark.resources.status_vacation
import dev.daymark.resources.this_week
import dev.daymark.resources.today
import dev.daymark.resources.today_place
import dev.daymark.resources.today_report_attention
import dev.daymark.resources.today_report_local
import dev.daymark.resources.today_report_pending
import dev.daymark.resources.tracking_not_started
import dev.daymark.resources.welcome_name
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.isolated
import dev.daymark.settings.str
import org.jetbrains.compose.resources.StringResource

@Composable
fun TodayScreen(
    model: TodayViewModel,
    displayName: String,
    onHistory: () -> Unit,
    dashboard: TodayDashboard? = null,
    report: ReportHealth = ReportHealth()
) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val language = LocalAppLanguage.current
    val summaryReady =
        dashboard == null || (dashboard.ready && (dashboard.date == null || dashboard.date == state.date))
    LifecycleStartEffect(model) { model.setActive(true); onStopOrDispose { model.setActive(false) } }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).safeDrawingPadding()
            .padding(24.dp).testTag("today-root"),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            str(Res.string.welcome_name, isolated(displayName)),
            style = MaterialTheme.typography.headlineLarge
        )
        Text(str(Res.string.today), style = MaterialTheme.typography.titleLarge)
        state.date?.let {
            Text(
                DisplayFormats.date(it, language),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!state.ready || !summaryReady) CircularProgressIndicator()
                else state.outcome?.let { outcome ->
                    Text(
                        str(outcome.status.label()),
                        Modifier.testTag("today-status"),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        str(outcome.source.label()),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    outcome.confirmedAt?.let { at ->
                        state.plan?.let { plan ->
                            Text(
                                str(
                                    Res.string.confirmed_at,
                                    DisplayFormats.time(at, plan.zoneId, language)
                                )
                            )
                        }
                    }
                } ?: Text(str(Res.string.tracking_not_started))
                dashboard?.takeIf { it.ready && it.placeName.isNotEmpty() }?.let { summary ->
                    Text(
                        str(Res.string.today_place, isolated(summary.placeName)),
                        modifier = Modifier.testTag("today-place")
                    )
                }
                state.plan?.let { plan ->
                    Text(
                        str(
                            Res.string.arrival_at,
                            DisplayFormats.time(plan.arrival, plan.zoneId, language)
                        )
                    )
                    Text(
                        str(
                            Res.string.deadline_at,
                            DisplayFormats.time(plan.deadline, plan.zoneId, language)
                        )
                    )
                    Text(
                        str(Res.string.attendance_zone, isolated(plan.zoneId)),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        state.error?.let {
            Text(
                str(it.label()),
                Modifier.testTag("today-error"),
                color = MaterialTheme.colorScheme.error
            )
        }
        if (state.error == CheckRejection.LOCATION_DENIED || state.error == CheckRejection.LOCATION_DISABLED) {
            TextButton(onClick = model::openLocationSettings) { Text(str(Res.string.open_settings)) }
        }
        Button(
            onClick = model::checkAttendance,
            enabled = state.canCheck && summaryReady,
            modifier = Modifier.fillMaxWidth().testTag("today-check")
        ) {
            Text(str(if (state.busy) Res.string.checking_attendance else Res.string.check_attendance))
        }
        OutlinedButton(
            onClick = onHistory,
            modifier = Modifier.fillMaxWidth().testTag("today-history")
        ) { Text(str(Res.string.history)) }
        dashboard?.takeIf { summaryReady }?.let { summary ->
            if (summary.failed) Text(
                str(Res.string.load_failed),
                color = MaterialTheme.colorScheme.error
            ) else {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            str(Res.string.month_so_far),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            str(
                                Res.string.month_counts,
                                DisplayFormats.number(summary.present, language),
                                DisplayFormats.number(summary.late, language),
                                DisplayFormats.number(summary.absent, language)
                            ), modifier = Modifier.testTag("today-month-counts")
                        )
                        Text(str(Res.string.this_week), style = MaterialTheme.typography.titleSmall)
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                .testTag("today-week"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            summary.week.forEach { day ->
                                val label =
                                    if (day.planned) str(Res.string.planned_date) else day.status?.let {
                                        str(it.label())
                                    } ?: str(Res.string.not_tracked_date)
                                val description =
                                    "${DisplayFormats.date(day.date, language)}: $label"
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = if (day.date == summary.date) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.width(64.dp)
                                        .semantics(mergeDescendants = true) {
                                            contentDescription = description
                                        }) {
                                    Column(
                                        Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            str(day.date.dayOfWeek.shortLabel()),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        Text(
                                            DisplayFormats.number(day.date.day, language),
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(if (day.planned) "○" else attendanceGlyph(day.status))
                                    }
                                }
                            }
                        }
                        AttendanceLegend()
                    }
                }
                summary.nextAttendance?.let { next ->
                    Text(
                        str(
                            Res.string.next_attendance,
                            DisplayFormats.date(next.key.date, language),
                            DisplayFormats.time(next.arrival, next.zoneId, language)
                        ), modifier = Modifier.testTag("today-next")
                    )
                }
            }
        }
        val reportCopy = when {
            report.binding == null -> str(Res.string.today_report_local)
            report.issues.isNotEmpty() || report.result in setOf(
                SyncPassResult.AuthorizationRequired,
                SyncPassResult.SchemaConflict,
                SyncPassResult.ReportUnavailable,
                SyncPassResult.ContentTooLarge
            ) -> str(Res.string.today_report_attention)

            report.pendingCount > 0 || report.busy || report.result == SyncPassResult.Pending -> str(
                Res.string.today_report_pending,
                DisplayFormats.number(report.pendingCount, language)
            )

            report.lastSync != null -> str(Res.string.report_observed_sync)
            else -> str(Res.string.report_ready)
        }
        Text(
            reportCopy,
            modifier = Modifier.testTag("today-report-state"),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal fun AttendanceStatus.label(): StringResource = when (this) {
    AttendanceStatus.SCHEDULED -> Res.string.status_scheduled
    AttendanceStatus.AWAITING_CHECK -> Res.string.status_awaiting
    AttendanceStatus.PRESENT -> Res.string.status_present
    AttendanceStatus.LATE -> Res.string.status_late
    AttendanceStatus.ABSENT -> Res.string.status_absent
    AttendanceStatus.DAY_OFF -> Res.string.status_day_off
    AttendanceStatus.HOLIDAY -> Res.string.status_holiday
    AttendanceStatus.VACATION -> Res.string.status_vacation
}

internal fun AttendanceSource.label(): StringResource = when (this) {
    AttendanceSource.NONE -> Res.string.source_none
    AttendanceSource.AUTOMATIC_GPS -> Res.string.source_automatic
    AttendanceSource.MANUAL_GPS -> Res.string.source_manual
    AttendanceSource.MANUAL_CORRECTION -> Res.string.source_correction
    AttendanceSource.MANUAL_TIME_OFF -> Res.string.source_time_off
}

internal fun CheckRejection.label(): StringResource = when (this) {
    CheckRejection.LOCATION_DENIED -> Res.string.check_denied
    CheckRejection.LOCATION_DISABLED -> Res.string.location_disabled
    CheckRejection.LOCATION_TIMEOUT -> Res.string.location_timeout
    CheckRejection.STALE_FIX -> Res.string.location_stale
    CheckRejection.INACCURATE_FIX -> Res.string.location_inaccurate
    CheckRejection.OUTSIDE_AREA -> Res.string.location_outside
    CheckRejection.LOCAL_SAVE_FAILED -> Res.string.load_failed
    CheckRejection.BEFORE_WINDOW -> Res.string.location_before_window
    CheckRejection.NO_TRACKER -> Res.string.check_no_tracker
    CheckRejection.STATE_CHANGED, CheckRejection.AFTER_AUTOMATIC_WINDOW, CheckRejection.DAY_OFF -> Res.string.check_changed
    else -> Res.string.location_unavailable
}
