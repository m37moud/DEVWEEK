package dev.daymark.data.local.attendance

import dev.daymark.data.local.DatabaseTransactions
import dev.daymark.data.local.dao.AttendanceDao
import dev.daymark.data.local.dao.PlanningDao
import dev.daymark.data.local.dao.ProfileDao
import dev.daymark.data.local.plan.PlanningSnapshotReader
import dev.daymark.data.local.plan.earlierVacationDates
import dev.daymark.data.local.plan.historyRecords
import dev.daymark.data.local.plan.nextHistoryBoundary
import dev.daymark.history.HistoryRange
import dev.daymark.models.attendance.TrackerCategory
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Projects calendar gaps from the same snapshot as saved attendance without writing records. */
internal class HistoryRangeReader(
    private val profileDao: ProfileDao,
    private val attendanceDao: AttendanceDao,
    private val planningDao: PlanningDao,
    private val transactions: DatabaseTransactions,
    private val clock: Clock
) {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observe(from: LocalDate, through: LocalDate): Flow<HistoryRange> {
        require(from <= through)
        return profileDao.observeTracker().flatMapLatest { tracker ->
            if (tracker == null) flowOf(
                HistoryRange(
                    emptyList(), null,
                    clock.now().toLocalDateTime(TimeZone.UTC).date
                )
            )
            else combine(
                attendanceDao.observeDays(tracker.id),
                planningDao.observeTimeOff(tracker.id)
            ) { _, _ -> Unit }
                .flatMapLatest { snapshots(from, through) }
        }
    }

    private fun snapshots(from: LocalDate, through: LocalDate): Flow<HistoryRange> = flow {
        while (true) {
            val now = clock.now()
            val snapshot = transactions.withReadTransaction {
                PlanningSnapshotReader(profileDao, attendanceDao, planningDao).load(now)
            } ?: break
            val records = snapshot.historyRecords(from, through)
            emit(
                HistoryRange(
                    records,
                    snapshot.firstDate,
                    snapshot.today,
                    snapshot.earlierVacationDates(from, through),
                    TrackerCategory.valueOf(snapshot.active.category)
                )
            )
            val next = snapshot.nextHistoryBoundary(records)
            delay((next - clock.now()).coerceAtLeast(1.milliseconds))
        }
    }
}
