package dev.daymark.history

import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.models.attendance.HistoryRecord
import dev.daymark.plan.TimeOffKind
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
enum class HistoryMetric {
    ATTENDANCE, LATE, ABSENT, LEAVE_REST, HOLIDAY;

    fun includes(record: HistoryRecord): Boolean = when (this) {
        ATTENDANCE -> record.outcome.status in setOf(
            AttendanceStatus.PRESENT,
            AttendanceStatus.LATE
        )

        LATE -> record.outcome.status == AttendanceStatus.LATE
        ABSENT -> record.outcome.status == AttendanceStatus.ABSENT
        LEAVE_REST -> record.outcome.status == AttendanceStatus.VACATION ||
                (record.outcome.status == AttendanceStatus.DAY_OFF && record.outcome.offReason == TimeOffKind.VACATION)

        HOLIDAY -> record.outcome.status == AttendanceStatus.HOLIDAY
    }
}

data class HistoryRange(
    val records: List<HistoryRecord>,
    val firstDate: LocalDate?,
    val today: LocalDate
)

data class HistoryMetrics(
    val attendance: Int = 0,
    val late: Int = 0,
    val absent: Int = 0,
    val leaveRest: Int = 0,
    val holidays: Int = 0,
    val lateDuration: Duration = Duration.ZERO
)

fun HistoryRecord.lateDuration(): Duration =
    if (outcome.status == AttendanceStatus.LATE) outcome.confirmedAt
        ?.let { (it - plan.arrival).coerceAtLeast(Duration.ZERO) } ?: Duration.ZERO
    else Duration.ZERO

fun historyMetrics(records: List<HistoryRecord>, today: LocalDate): HistoryMetrics {
    val elapsed = records.filter { it.plan.key.date <= today }
    return HistoryMetrics(
        attendance = elapsed.count(HistoryMetric.ATTENDANCE::includes),
        late = elapsed.count(HistoryMetric.LATE::includes),
        absent = elapsed.count(HistoryMetric.ABSENT::includes),
        leaveRest = elapsed.count(HistoryMetric.LEAVE_REST::includes),
        holidays = elapsed.count(HistoryMetric.HOLIDAY::includes),
        lateDuration = elapsed.fold(Duration.ZERO) { total, record -> total + record.lateDuration() }
    )
}
