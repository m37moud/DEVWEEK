package dev.daymark.data.local.attendance

import dev.daymark.data.local.DatabaseTransactions
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Projects calendar gaps from the same snapshot as saved attendance without writing records. */
internal class HistoryRangeReader(
    private val snapshots: PlanningSnapshotReader,
    private val transactions: DatabaseTransactions,
    private val clock: Clock
) {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observe(from: LocalDate, through: LocalDate): Flow<HistoryRange> {
        require(from <= through)
        return transactions.observeChanges("trackers", "attendance", "time_off", "plan_versions")
            .flatMapLatest { readRange(from = from, through = through) }
    }

    private fun readRange(from: LocalDate, through: LocalDate): Flow<HistoryRange> = flow {
        while (true) {
            val now = clock.now()
            val snapshot = transactions.withReadTransaction {
                snapshots.load(now = now, attendanceDates = from..through)
            }
            if (snapshot == null) {
                emit(
                    HistoryRange(
                        records = emptyList(),
                        firstDate = null,
                        today = now.toLocalDateTime(timeZone = TimeZone.UTC).date
                    )
                )
                break
            }
            val records = snapshot.historyRecords(from = from, through = through)
            emit(
                HistoryRange(
                    records = records,
                    firstDate = snapshot.firstDate,
                    today = snapshot.today,
                    earlierVacationDates = snapshot.earlierVacationDates(
                        from = from,
                        through = through
                    ),
                    category = TrackerCategory.valueOf(snapshot.active.category)
                )
            )
            val next = snapshot.nextHistoryBoundary(records = records)
            delay((next - clock.now()).coerceAtLeast(1.milliseconds))
        }
    }
}
