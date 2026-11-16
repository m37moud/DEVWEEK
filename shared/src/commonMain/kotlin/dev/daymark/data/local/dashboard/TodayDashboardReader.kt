package dev.daymark.data.local.dashboard

import dev.daymark.data.local.DatabaseTransactions
import dev.daymark.data.local.dao.ProfileDao
import dev.daymark.data.local.plan.PlanningSnapshotReader
import dev.daymark.data.local.plan.PlanningStore
import dev.daymark.data.local.plan.historyRecords
import dev.daymark.data.local.plan.nextHistoryBoundary
import dev.daymark.history.historyMetrics
import dev.daymark.logging.AppLogger
import dev.daymark.models.attendance.HistoryRecord
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Read-only projection; it neither requests GPS nor changes scheduling/attendance. */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayDashboardReader(
    private val profileDao: ProfileDao,
    private val snapshots: PlanningSnapshotReader,
    private val transactions: DatabaseTransactions,
    private val clock: Clock
) :
    DashboardDataSource {
    override fun observe(): Flow<TodayDashboard> = profileDao.observeTracker().flatMapLatest {
        flow {
            while (true) {
                val state = transactions.withReadTransaction { snapshots.load(clock.now()) }
                if (state == null) {
                    emit(TodayDashboard(ready = true))
                    break
                }
                val from = LocalDate(state.today.year, state.today.month, 1)
                val through = from.plus(1, DateTimeUnit.MONTH).plus(-1, DateTimeUnit.DAY)
                val records = state.historyRecords(from, through)
                emit(dashboard(state, records))
                delay((state.nextHistoryBoundary(records) - clock.now()).coerceAtLeast(1.milliseconds))
                AppLogger.d("TodayDashboardReader", "Calendar boundary reached")
            }
        }
    }.catch { failure ->
        if (failure is CancellationException) throw failure
        AppLogger.e("TodayDashboardReader", "Dashboard read failed", failure)
        emit(TodayDashboard(ready = true, failed = true))
    }

    private fun dashboard(state: PlanningStore, records: List<HistoryRecord>): TodayDashboard {
        val plan = state.plan(state.today)
        val place =
            state.versions.firstOrNull { it.version == plan.scheduleVersion }?.placeName.orEmpty()
        return TodayDashboard(
            ready = true,
            date = state.today,
            placeName = place,
            metrics = historyMetrics(records, state.today),
            week = week(state),
            nextAttendance = nextAttendance(state)
        )
    }

    private fun week(state: PlanningStore): List<WeekDaySummary> {
        val start = Instant.parse(state.tracker.trackingStartsAt)
        val monday = state.today.minus(state.today.dayOfWeek.ordinal, DateTimeUnit.DAY)
        return List(7) { offset ->
            val date = monday.plus(offset, DateTimeUnit.DAY)
            val status = if (date < state.firstDate) null else {
                val day = state.plan(date)
                if (day.isScheduled && day.arrival < start && state.rows[date] == null &&
                    timeOffOn(state.entries, date) == null
                ) null else state.outcome(date).status
            }
            WeekDaySummary(date, status, date > state.today && state.rows[date] == null)
        }
    }

    private fun nextAttendance(state: PlanningStore): dev.daymark.models.attendance.DayPlan? {
        val off = state.outcome(state.today).status in setOf(
            AttendanceStatus.DAY_OFF, AttendanceStatus.HOLIDAY, AttendanceStatus.VACATION
        )
        if (!off) return null
        val start = Instant.parse(state.tracker.trackingStartsAt)
        return (1..3660).firstNotNullOfOrNull { offset ->
            val date = state.today.plus(offset, DateTimeUnit.DAY)
            state.plan(date).takeIf {
                it.isScheduled && timeOffOn(
                    state.entries,
                    date
                ) == null && it.arrival >= state.now && it.arrival >= start
            }
        }
    }
}
