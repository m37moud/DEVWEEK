package dev.daymark.ui

import androidx.room3.withReadTransaction
import dev.daymark.attendance.AttendanceStatus
import dev.daymark.attendance.DayPlan
import dev.daymark.data.DaymarkDatabase
import dev.daymark.plan.planning
import dev.daymark.plan.timeOffOn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.mapLatest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.time.Clock
import kotlin.time.Instant

data class WeekDaySummary(val date: LocalDate, val status: AttendanceStatus?, val planned: Boolean)
data class TodayDashboard(
    val ready: Boolean = false, val failed: Boolean = false, val date: LocalDate? = null,
    val placeName: String = "", val present: Int = 0, val late: Int = 0, val absent: Int = 0,
    val week: List<WeekDaySummary> = emptyList(), val nextAttendance: DayPlan? = null
)

/** Read-only projection; it neither requests GPS nor changes scheduling/attendance. */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayDashboardReader(private val db: DaymarkDatabase, private val clock: Clock) {
    fun observe(): Flow<TodayDashboard> = db.dao().observeTracker().mapLatest {
        db.withReadTransaction {
            val state = db.dao().planning(clock.now()) ?: return@withReadTransaction TodayDashboard(
                ready = true
            )
            val start = Instant.parse(state.tracker.trackingStartsAt)
            val plan = state.plan(state.today)
            val place =
                state.versions.firstOrNull { version -> version.version == plan.scheduleVersion }?.placeName.orEmpty()
            val statuses =
                state.rows.filter { (date, _) -> date.year == state.today.year && date.month == state.today.month }.keys.map { date ->
                    state.outcome(date).status
                }
            val monday = state.today.minus(state.today.dayOfWeek.ordinal, DateTimeUnit.DAY)
            val week = List(7) { offset ->
                val date = monday.plus(offset, DateTimeUnit.DAY)
                val status = if (date < state.firstDate) null else {
                    val day = state.plan(date)
                    if (day.isScheduled && day.arrival < start && state.rows[date] == null && timeOffOn(
                            state.entries,
                            date
                        ) == null
                    ) null else state.outcome(date).status
                }
                WeekDaySummary(date, status, date > state.today && state.rows[date] == null)
            }
            val off = state.outcome(state.today).status in setOf(
                AttendanceStatus.DAY_OFF,
                AttendanceStatus.HOLIDAY,
                AttendanceStatus.VACATION
            )
            val next = if (!off) null else (1..3660).firstNotNullOfOrNull { offset ->
                val date = state.today.plus(offset, DateTimeUnit.DAY)
                val day = state.plan(date)
                day.takeIf {
                    it.isScheduled && timeOffOn(
                        state.entries,
                        date
                    ) == null && it.arrival >= state.now && it.arrival >= start
                }
            }
            TodayDashboard(
                true,
                false,
                state.today,
                place,
                statuses.count { it == AttendanceStatus.PRESENT },
                statuses.count { it == AttendanceStatus.LATE },
                statuses.count { it == AttendanceStatus.ABSENT },
                week,
                next
            )
        }
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(TodayDashboard(ready = true, failed = true))
    }
}

/** ISO Monday-first display grid; only actual repository records supply historical outcomes. */
fun calendarWeeks(year: Int, month: Int): List<List<LocalDate?>> {
    val first = LocalDate(year, month, 1)
    val last = first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).day
    val offset = first.dayOfWeek.ordinal
    val cells = ((offset + last + 6) / 7) * 7
    return List(cells) { index ->
        (index - offset + 1).takeIf { it in 1..last }?.let { LocalDate(year, month, it) }
    }.chunked(7)
}
