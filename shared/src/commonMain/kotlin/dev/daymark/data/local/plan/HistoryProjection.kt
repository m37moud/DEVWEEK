package dev.daymark.data.local.plan

import dev.daymark.models.attendance.HistoryRecord
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus

/** Uses frozen rows first and projects gaps without creating attendance evidence. */
internal suspend fun PlanningStore.historyRecords(
    from: LocalDate,
    through: LocalDate
): List<HistoryRecord> {
    require(from <= through)
    val records = mutableListOf<HistoryRecord>()
    var date = maxOf(from, firstDate)
    val start = Instant.parse(tracker.trackingStartsAt)
    while (date <= through) {
        currentCoroutineContext().ensureActive()
        val plan = plan(date)
        if (date in rows || plan.arrival >= start) records += HistoryRecord(
            plan,
            rows[date]?.evidence(),
            outcome(date)
        )
        date = date.plus(1, DateTimeUnit.DAY)
    }
    return records
}

internal fun PlanningStore.nextHistoryBoundary(records: List<HistoryRecord>): Instant {
    val midnight =
        today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(TimeZone.of(active.schedule().zoneId))
    val attendance = records.asSequence()
        .flatMap { sequenceOf(it.plan.arrival, it.plan.deadline + 1.nanoseconds) }
    val configuration = versions.asSequence().map { Instant.parse(it.effectiveAt) }
    return (attendance + configuration).filter { it > now }.minOrNull()?.coerceAtMost(midnight)
        ?: midnight
}
