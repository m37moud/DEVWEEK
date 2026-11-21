package dev.daymark.data.local.plan

import dev.daymark.attendance.evaluateDay
import dev.daymark.data.local.AttendanceEntity
import dev.daymark.data.local.BalanceAdjustmentEntity
import dev.daymark.data.local.PlanVersionEntity
import dev.daymark.data.local.TimeOffEntity
import dev.daymark.data.local.TrackerEntity
import dev.daymark.history.AttendancePeriodSettings
import dev.daymark.models.attendance.DayKey
import dev.daymark.models.attendance.DayPlan
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.plan.AnnualVacationPolicy
import dev.daymark.models.plan.PlanChange
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.models.plan.PlanRejection
import dev.daymark.models.plan.PlanReviewToken
import dev.daymark.models.plan.TimeOffEntry
import dev.daymark.models.plan.TimeOffPreview
import dev.daymark.plan.TimeOffKind
import dev.daymark.plan.resolveDay
import dev.daymark.plan.timeOffOn
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

internal fun TimeOffEntity.entry() = TimeOffEntry(
    id,
    TimeOffKind.valueOf(kind),
    LocalDate.parse(startDate),
    LocalDate.parse(endDate),
    Instant.parse(updatedAt)
)

internal fun TimeOffEntry.entity(trackerId: String) = TimeOffEntity(
    id,
    trackerId,
    kind.name,
    from.toString(),
    through.toString(),
    null,
    updatedAt.toString()
)

internal fun PlanVersionEntity.change() =
    PlanChange(schedule(), TrackerCategory.valueOf(category), place())

internal fun projectedPlan(versions: List<PlanVersionEntity>, key: DayKey): DayPlan? {
    val version = versions.filter { LocalDate.parse(it.effectiveDate) <= key.date }
        .maxWithOrNull(compareBy<PlanVersionEntity> { LocalDate.parse(it.effectiveDate) }.thenBy { it.version })
        ?: return null
    val plan = resolveDay(
        key,
        version.schedule(),
        TrackerCategory.valueOf(version.category),
        version.place(),
        version.version
    )
    // A zone change must not create an attendance obligation whose window began before the change activated.
    return if (version.version != versions.minOf { it.version } && plan.arrival < Instant.parse(
            version.effectiveAt
        ))
        plan.copy(isScheduled = false) else plan
}

internal data class PlanningStore(
    val tracker: TrackerEntity,
    val versions: List<PlanVersionEntity>,
    val rows: Map<LocalDate, AttendanceEntity>,
    val entries: List<TimeOffEntry>,
    val adjustments: List<BalanceAdjustmentEntity>,
    val now: Instant,
    val policies: List<AnnualVacationPolicy>,
    val periodSettings: AttendancePeriodSettings = AttendancePeriodSettings()
) {
    val active = versions.filter { Instant.parse(it.effectiveAt) <= now }
        .maxWithOrNull(compareBy<PlanVersionEntity> { Instant.parse(it.effectiveAt) }.thenBy { it.version })
        ?: versions.first()
    val today = now.toLocalDateTime(TimeZone.of(active.schedule().zoneId)).date
    val vacationYear = periodSettings.vacationYear(today)
    val firstDate = LocalDate.parse(versions.first().effectiveDate)

    // Tracker identity prevents a preview from an erased profile matching a newly created profile at revision zero.
    val revision = Uuid.parse(tracker.id)
        .toLongs { high, low -> ((high xor low) ushr 2) + tracker.changeRevision }

    val reviewToken = PlanReviewToken(revision, today)

    fun plan(date: LocalDate, expectations: List<PlanVersionEntity> = versions): DayPlan =
        rows[date]?.plan() ?: requireNotNull(projectedPlan(expectations, DayKey(tracker.id, date)))

    fun balance(
        off: List<TimeOffEntry> = entries,
        expectations: List<PlanVersionEntity> = versions
    ): Int = balanceSummary(off, expectations).remainingDays

    fun balanceSummary(
        off: List<TimeOffEntry> = entries,
        expectations: List<PlanVersionEntity> = versions
    ) = balances(off, expectations).getValue(balanceYear(vacationYear))

    fun outcome(
        date: LocalDate,
        off: List<TimeOffEntry> = entries,
        expectations: List<PlanVersionEntity> = versions
    ) =
        evaluateDay(plan(date, expectations), rows[date]?.evidence(), timeOffOn(off, date), now)
}


internal fun invalidPreview(reason: PlanRejection) =
    TimeOffPreview(0, emptyList(), emptyList(), 0, 0, reason)

internal fun TimeOffPreview.failure(): PlanMutationResult? = when (rejection) {
    null -> null
    PlanRejection.INSUFFICIENT_BALANCE -> shortfallYear?.let({ PlanMutationResult.InsufficientYear(it) })
        ?: PlanMutationResult.InsufficientBalance

    PlanRejection.LOCAL_SAVE_FAILED -> PlanMutationResult.LocalSaveFailed
    else -> PlanMutationResult.Invalid(rejection)
}

internal suspend fun <T> storageResult(failed: T, block: suspend () -> T): T = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    failed
}
