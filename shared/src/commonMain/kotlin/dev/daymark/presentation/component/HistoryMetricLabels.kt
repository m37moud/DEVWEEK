package dev.daymark.presentation.component

import androidx.compose.runtime.Composable
import dev.daymark.history.HistoryMetric
import dev.daymark.history.HistoryMetrics
import dev.daymark.resources.Res
import dev.daymark.resources.history_attendance
import dev.daymark.resources.history_holidays
import dev.daymark.resources.history_work_days
import dev.daymark.resources.history_work_missions
import dev.daymark.resources.history_leave_rest
import dev.daymark.resources.history_late_minutes
import dev.daymark.resources.history_under_minute
import dev.daymark.resources.status_absent
import dev.daymark.resources.status_late
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.str
import kotlin.time.Duration

internal fun HistoryMetric.metricLabel(showWorkDaysLabel: Boolean = false) = when (this) {
    HistoryMetric.ATTENDANCE -> if (showWorkDaysLabel) Res.string.history_work_days else Res.string.history_attendance
    HistoryMetric.WORK_MISSION -> Res.string.history_work_missions
    HistoryMetric.LATE -> Res.string.status_late
    HistoryMetric.ABSENT -> Res.string.status_absent
    HistoryMetric.LEAVE_REST -> Res.string.history_leave_rest
    HistoryMetric.HOLIDAY -> Res.string.history_holidays
}

@Composable
internal fun lateDurationLabel(duration: Duration): String =
    if (duration > Duration.ZERO && duration.inWholeMinutes == 0L) str(Res.string.history_under_minute)
    else str(
        Res.string.history_late_minutes,
        DisplayFormats.number(
            duration.inWholeMinutes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            LocalAppLanguage.current
        )
    )

internal fun metricCount(metric: HistoryMetric, metrics: HistoryMetrics): Int =
    when (metric) {
        HistoryMetric.ATTENDANCE -> metrics.attendance
        HistoryMetric.LATE -> metrics.late
        HistoryMetric.ABSENT -> metrics.absent
        HistoryMetric.LEAVE_REST -> metrics.leaveRest
        HistoryMetric.HOLIDAY -> metrics.holidays
        HistoryMetric.WORK_MISSION -> metrics.workMissions
    }
