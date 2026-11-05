package dev.daymark.presentation.component

import androidx.compose.runtime.getValue
import dev.daymark.models.attendance.AttendanceSource
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.models.attendance.CheckRejection
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.presentation.component.label
import dev.daymark.presentation.features.setup.SetupIssue
import dev.daymark.resources.Res
import dev.daymark.resources.check_changed
import dev.daymark.resources.check_denied
import dev.daymark.resources.check_no_tracker
import dev.daymark.resources.course
import dev.daymark.resources.friday
import dev.daymark.resources.invalid_setup
import dev.daymark.resources.load_failed
import dev.daymark.resources.local_save_failed
import dev.daymark.resources.location_before_window
import dev.daymark.resources.location_denied
import dev.daymark.resources.location_disabled
import dev.daymark.resources.location_inaccurate
import dev.daymark.resources.location_outside
import dev.daymark.resources.location_stale
import dev.daymark.resources.location_timeout
import dev.daymark.resources.location_unavailable
import dev.daymark.resources.monday
import dev.daymark.resources.saturday
import dev.daymark.resources.school
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
import dev.daymark.resources.sunday
import dev.daymark.resources.thursday
import dev.daymark.resources.tuesday
import dev.daymark.resources.wednesday
import dev.daymark.resources.work
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.isolated
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.StringResource

internal fun TrackerCategory.label(): StringResource = when (this) {
    TrackerCategory.WORK -> Res.string.work; TrackerCategory.SCHOOL -> Res.string.school; TrackerCategory.COURSE -> Res.string.course
}

internal fun DayOfWeek.label(): StringResource = when (this) {
    DayOfWeek.MONDAY -> Res.string.monday; DayOfWeek.TUESDAY -> Res.string.tuesday; DayOfWeek.WEDNESDAY -> Res.string.wednesday
    DayOfWeek.THURSDAY -> Res.string.thursday; DayOfWeek.FRIDAY -> Res.string.friday; DayOfWeek.SATURDAY -> Res.string.saturday; DayOfWeek.SUNDAY -> Res.string.sunday
}

internal fun SetupIssue.label(): StringResource = when (this) {
    SetupIssue.INVALID_INPUT -> Res.string.invalid_setup; SetupIssue.LOCATION_DENIED -> Res.string.location_denied
    SetupIssue.LOCATION_DISABLED -> Res.string.location_disabled; SetupIssue.LOCATION_TIMEOUT -> Res.string.location_timeout
    SetupIssue.LOCATION_UNAVAILABLE -> Res.string.location_unavailable; SetupIssue.LOCATION_STALE -> Res.string.location_stale
    SetupIssue.LOCATION_INACCURATE -> Res.string.location_inaccurate; SetupIssue.LOCAL_SAVE_FAILED -> Res.string.local_save_failed
}

internal fun localTimeLabel(time: LocalTime, language: AppLanguage): String {
    val zero = DisplayFormats.number(0, language).single()
    return isolated(
        DisplayFormats.number(time.hour, language).padStart(2, zero) + ":" + DisplayFormats.number(
            time.minute,
            language
        ).padStart(2, zero)
    )
}

internal fun SetupIssue.isLocationIssue() =
    this != SetupIssue.INVALID_INPUT && this != SetupIssue.LOCAL_SAVE_FAILED

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
