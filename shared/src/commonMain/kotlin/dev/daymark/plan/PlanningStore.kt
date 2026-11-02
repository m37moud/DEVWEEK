package dev.daymark.plan

import dev.daymark.attendance.*
import dev.daymark.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal fun TimeOffEntity.entry() = TimeOffEntry(id, TimeOffKind.valueOf(kind), LocalDate.parse(startDate), LocalDate.parse(endDate), Instant.parse(updatedAt))
internal fun TimeOffEntry.entity(trackerId: String) = TimeOffEntity(id, trackerId, kind.name, from.toString(), through.toString(), null, updatedAt.toString())
internal fun PlanVersionEntity.change() = PlanChange(schedule(), TrackerCategory.valueOf(category), place())
internal fun projectedPlan(versions: List<PlanVersionEntity>, key: DayKey): DayPlan? {
    val version = versions.filter { LocalDate.parse(it.effectiveDate) <= key.date }
        .maxWithOrNull(compareBy<PlanVersionEntity> { LocalDate.parse(it.effectiveDate) }.thenBy { it.version }) ?: return null
    val plan = resolveDay(key, version.schedule(), TrackerCategory.valueOf(version.category), version.place(), version.version)
    // A zone change must not create an attendance obligation whose window began before the change activated.
    return if (version.version != versions.minOf { it.version } && plan.arrival < Instant.parse(version.effectiveAt))
        plan.copy(isScheduled = false) else plan
}
internal data class PlanningStore(val tracker: TrackerEntity, val versions: List<PlanVersionEntity>, val rows: Map<LocalDate, AttendanceEntity>,
    val entries: List<TimeOffEntry>, val adjustments: List<BalanceAdjustmentEntity>, val now: Instant) {
    val active = versions.filter { Instant.parse(it.effectiveAt) <= now }
        .maxWithOrNull(compareBy<PlanVersionEntity> { Instant.parse(it.effectiveAt) }.thenBy { it.version }) ?: versions.first()
    val today = now.toLocalDateTime(TimeZone.of(active.schedule().zoneId)).date
    val firstDate = LocalDate.parse(versions.first().effectiveDate)
    // Tracker identity prevents a preview from an erased profile matching a newly created profile at revision zero.
    val revision = Uuid.parse(tracker.id).toLongs { high, low -> ((high xor low) ushr 2) + tracker.changeRevision }
    fun plan(date: LocalDate, expectations: List<PlanVersionEntity> = versions): DayPlan =
        rows[date]?.plan() ?: requireNotNull(projectedPlan(expectations, DayKey(tracker.id, date)))
    fun balance(off: List<TimeOffEntry> = entries, expectations: List<PlanVersionEntity> = versions): Int =
        availableVacationDays(tracker.openingBalance, adjustments.map { it.delta }, off) { plan(it, expectations) }
    fun outcome(date: LocalDate, off: List<TimeOffEntry> = entries, expectations: List<PlanVersionEntity> = versions) =
        evaluateDay(plan(date, expectations), rows[date]?.evidence(), timeOffOn(off, date), now)
}
internal suspend fun DaymarkDao.planning(now: Instant): PlanningStore? {
    val tracker = tracker() ?: return null
    val versions = versions(tracker.id)
    if (versions.isEmpty()) return null
    return PlanningStore(tracker, versions, days(tracker.id).associateBy { LocalDate.parse(it.date) },
        timeOff(tracker.id).map { it.entry() }, balanceAdjustments(tracker.id), now)
}
internal fun invalidPreview(reason: PlanRejection) = TimeOffPreview(0, emptyList(), emptyList(), 0, 0, reason)
internal fun TimeOffPreview.failure(): PlanMutationResult? = when (rejection) {
    null -> null
    PlanRejection.INSUFFICIENT_BALANCE -> PlanMutationResult.InsufficientBalance
    PlanRejection.LOCAL_SAVE_FAILED -> PlanMutationResult.LocalSaveFailed
    else -> PlanMutationResult.Invalid(rejection)
}
internal suspend fun <T> storageResult(failed: T, block: suspend () -> T): T = try { block() }
catch (cancelled: CancellationException) { throw cancelled }
catch (_: Exception) { failed }
