package dev.daymark.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.daymark.attendance.*
import dev.daymark.resources.*
import dev.daymark.settings.*
import org.jetbrains.compose.resources.StringResource

@Composable fun TodayScreen(model: TodayViewModel, displayName: String, onHistory: () -> Unit) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val language = LocalAppLanguage.current
    LifecycleStartEffect(model) { model.setActive(true); onStopOrDispose { model.setActive(false) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).safeDrawingPadding().padding(24.dp).testTag("today-root"),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(str(Res.string.welcome_name, isolated(displayName)), style = MaterialTheme.typography.headlineLarge)
        Text(str(Res.string.today), style = MaterialTheme.typography.titleLarge)
        state.date?.let { Text(DisplayFormats.date(it, language), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!state.ready) CircularProgressIndicator()
                else state.outcome?.let { outcome ->
                    Text(str(outcome.status.label()), Modifier.testTag("today-status"), style = MaterialTheme.typography.headlineMedium)
                    Text(str(outcome.source.label()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    outcome.confirmedAt?.let { at -> state.plan?.let { plan -> Text(str(Res.string.confirmed_at, DisplayFormats.time(at, plan.zoneId, language))) } }
                } ?: Text(str(Res.string.tracking_not_started))
                state.plan?.let { plan ->
                    Text(str(Res.string.arrival_at, DisplayFormats.time(plan.arrival, plan.zoneId, language)))
                    Text(str(Res.string.deadline_at, DisplayFormats.time(plan.deadline, plan.zoneId, language)))
                    Text(str(Res.string.attendance_zone, isolated(plan.zoneId)), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        state.error?.let { Text(str(it.label()), Modifier.testTag("today-error"), color = MaterialTheme.colorScheme.error) }
        if (state.error == CheckRejection.LOCATION_DENIED || state.error == CheckRejection.LOCATION_DISABLED) {
            TextButton(onClick = model::openLocationSettings) { Text(str(Res.string.open_settings)) }
        }
        Button(onClick = model::checkAttendance, enabled = state.canCheck, modifier = Modifier.fillMaxWidth().testTag("today-check")) {
            Text(str(if (state.busy) Res.string.checking_attendance else Res.string.check_attendance))
        }
        OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth().testTag("today-history")) { Text(str(Res.string.history)) }
        Text(str(Res.string.local_records), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
