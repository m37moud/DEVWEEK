package dev.daymark.data.local.dashboard

import dev.daymark.data.local.DatabaseTransactions
import dev.daymark.data.local.dao.ProfileDao
import dev.daymark.data.local.plan.PlanningSnapshotReader
import dev.daymark.data.local.plan.PlanningStore
import dev.daymark.data.local.plan.earlierVacationDates
import dev.daymark.data.local.plan.historyRecords
import dev.daymark.data.local.plan.nextHistoryBoundary
import dev.daymark.history.AttendancePeriod
import dev.daymark.history.historyMetrics
import dev.daymark.logging.AppLogger
import dev.daymark.models.attendance.HistoryRecord
import dev.daymark.models.TodayDashboard
import dev.daymark.models.WeekDaySummary
import dev.daymark.models.attendance.AttendanceStatus
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
                val period = state.periodSettings.periodFor(state.today)
                val records = state.historyRecords(period.from, period.through)
                emit(dashboard(state, period, records, state.earlierVacationDates(period.from, period.through)))
                delay((state.nextHistoryBoundary(records) - clock.now()).coerceAtLeast(1.milliseconds))
                AppLogger.d("TodayDashboardReader", "Calendar boundary reached")
            }
        }
    }.catch { failure ->
        if (failure is CancellationException) throw failure
        AppLogger.e("TodayDashboardReader", "Dashboard read failed", failure)
        emit(TodayDashboard(ready = true, failed = true))
    }

    private fun dashboard(
        state: PlanningStore,
        period: AttendancePeriod,
        records: List<HistoryRecord>,
        earlierVacationDates: List<LocalDate>
    ): TodayDashboard {
        val plan = state.plan(state.today)
        val place =
            state.versions.firstOrNull { it.version == plan.scheduleVersion }?.placeName.orEmpty()
        return TodayDashboard(
            ready = true,
            date = state.today,
            period = period,
            placeName = place,
            metrics = historyMetrics(records, state.today, earlierVacationDates),
            week = week(state),
            nextAttendance = nextAttendance(state)
        )
    }

    private fun week(state: PlanningStore): List<WeekDaySummary> {
        val start = Instant.parse(state.tracker.trackingStartsAt)
        val monday = state.today.minus(state.today.dayOfWeek.ordinal, DateTimeUnit.DAY)
        return List(7) { offset ->
            val date = monday.plus(offset, DateTimeUnit.DAY)
            val plan = date.takeIf { it >= state.firstDate }?.let { state.plan(it) }
            val entry = plan?.let(state::dayEntry)
            val tracked = plan != null &&
                    (!plan.isScheduled || plan.arrival >= start || date in state.rows || entry != null)
            val status = if (tracked) state.outcome(date).status else null
            WeekDaySummary(date, status, date > state.today && date !in state.rows && entry == null)
        }
    }

    private fun nextAttendance(state: PlanningStore): dev.daymark.models.attendance.DayPlan? {
        val off = state.outcome(state.today).status in setOf(
            AttendanceStatus.DAY_OFF, AttendanceStatus.HOLIDAY, AttendanceStatus.VACATION, AttendanceStatus.WORK_MISSION
        )
        if (!off) return null
        val start = Instant.parse(state.tracker.trackingStartsAt)
        return (1..3660).firstNotNullOfOrNull { offset ->
            val date = state.today.plus(offset, DateTimeUnit.DAY)
            state.plan(date).takeIf {
                it.isScheduled && state.dayEntry(it) == null && it.arrival >= state.now && it.arrival >= start
            }
        }
    }
}
