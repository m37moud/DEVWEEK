package dev.daymark.data.local.dashboard

import androidx.room3.withReadTransaction
import dev.daymark.data.local.DaymarkDatabase
import dev.daymark.data.local.plan.planning
import dev.daymark.models.TodayDashboard
import dev.daymark.models.WeekDaySummary
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.plan.timeOffOn
import dev.daymark.repository.DashboardDataSource
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.mapLatest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Read-only projection; it neither requests GPS nor changes scheduling/attendance. */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayDashboardReader(private val db: DaymarkDatabase, private val clock: Clock) :
    DashboardDataSource {
    override fun observe(): Flow<TodayDashboard> = db.dao().observeTracker().mapLatest {
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
