package dev.daymark.data.local.plan

import dev.daymark.attendance.evaluateDay
import dev.daymark.models.attendance.DayPlan
import dev.daymark.models.attendance.HistoryRecord
import dev.daymark.plan.TimeOffKind
import dev.daymark.plan.dates
import dev.daymark.plan.timeOffOn
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
        if (date in rows || plan.arrival >= start) records += historyRecord(plan)
        date = date.plus(1, DateTimeUnit.DAY)
    }
    return records
}

internal fun PlanningStore.historyRecord(plan: DayPlan): HistoryRecord {
    val evidence = rows[plan.key.date]?.evidence()
    val entry = dayEntry(plan)
    val outcome = evaluateDay(plan, evidence, entry?.kind, now)
    val missionNote = entry?.takeIf { it.kind == TimeOffKind.WORK_MISSION }?.note
    return HistoryRecord(plan, evidence, outcome, missionNote)
}

internal fun PlanningStore.earlierVacationDates(from: LocalDate, through: LocalDate): List<LocalDate> {
    if (from >= firstDate) return emptyList()
    val end = minOf(through, today, firstDate.plus(-1, DateTimeUnit.DAY))
    return entries.asSequence()
        .filter { it.kind == TimeOffKind.VACATION && it.through >= from && it.from <= end }
        .flatMap { dates(maxOf(from, it.from), minOf(end, it.through)) }
        .distinct()
        .filter { timeOffOn(entries, it) == TimeOffKind.VACATION }
        .sorted()
        .toList()
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
