package dev.daymark.data.local.reporting

import dev.daymark.data.local.plan.PlanningStore
import dev.daymark.data.local.plan.earlierVacationDates
import dev.daymark.data.local.plan.historyRecords
import dev.daymark.history.historyMetrics
import dev.daymark.models.reporting.ProjectionKey
import dev.daymark.models.reporting.RawRange
import dev.daymark.models.reporting.ReportProjection
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.serialization.json.JsonPrimitive

internal const val ATTENDANCE_PERIODS_TAB = "Attendance periods"

internal val periodMachineColumns = listOf(
    "period", "from", "through", "attendance", "late", "absent", "leave", "holiday", "lateMinutes"
)
internal val periodHumanColumns = listOf(
    "Period / الفترة", "From / من", "Through / إلى", "Attendance / الحضور",
    "Late / متأخر", "Absent / غائب", "Leave / إجازة", "Holiday / عطلة", "Late minutes / دقائق التأخير"
)

internal suspend fun PlanningStore.projectAttendancePeriods(
    revision: Long,
    previousCapacity: Int
): ReportProjection {
    val rows = attendancePeriodRows()
    val capacity = maxOf(1, previousCapacity, rows.size)
    val blank = List(periodMachineColumns.size) { JsonPrimitive("") }
    val range = "'$ATTENDANCE_PERIODS_TAB'!A3:I${capacity + 2}"
    return ReportProjection(
        ProjectionKey.AttendancePeriods,
        revision,
        listOf(RawRange(range, List(capacity) { rows.getOrNull(it) ?: blank }))
    )
}

private suspend fun PlanningStore.attendancePeriodRows(): List<List<JsonPrimitive>> {
    val earlier = entries.minOfOrNull { it.from }
        ?.let { earlierVacationDates(it, today) }.orEmpty()
    val records = if (firstDate <= today) historyRecords(firstDate, today) else emptyList()
    val byMonth = records.groupBy { periodSettings.periodFor(it.plan.key.date).month }
    val earlierByMonth = earlier.groupBy { periodSettings.periodFor(it).month }
    val firstMonth = periodSettings.periodFor(firstDate).month
    val lastMonth = periodSettings.periodFor(today).month
    val trackedMonths = generateSequence(firstMonth) { it.plus(1, DateTimeUnit.MONTH) }
        .takeWhile { it <= lastMonth }
    return (trackedMonths + earlierByMonth.keys.asSequence()).distinct().sorted().map { month ->
        val period = periodSettings.period(month)
        val metrics = historyMetrics(byMonth[month].orEmpty(), today, earlierByMonth[month].orEmpty())
        listOf(
            JsonPrimitive(month.toString().substringBeforeLast('-')),
            JsonPrimitive(period.from.toString()),
            JsonPrimitive(period.through.toString()),
            JsonPrimitive(metrics.attendance),
            JsonPrimitive(metrics.late),
            JsonPrimitive(metrics.absent),
            JsonPrimitive(metrics.leaveRest),
            JsonPrimitive(metrics.holidays),
            JsonPrimitive(metrics.lateDuration.inWholeMinutes)
        )
    }.toList()
}
