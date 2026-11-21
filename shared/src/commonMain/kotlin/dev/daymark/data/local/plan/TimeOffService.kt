@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.daymark.data.local.plan

import dev.daymark.attendance.AttendanceRepository
import dev.daymark.data.local.AnnualVacationPolicyEntity
import dev.daymark.data.local.AttendanceEntity
import dev.daymark.data.local.BalanceAdjustmentEntity
import dev.daymark.data.local.DatabaseTransactions
import dev.daymark.data.local.SettingEntity
import dev.daymark.data.local.dao.AttendanceDao
import dev.daymark.data.local.dao.DirtyProjectionDao
import dev.daymark.data.local.dao.PlanningDao
import dev.daymark.data.local.dao.ProfileDao
import dev.daymark.data.local.markAttendanceSummariesDirty
import dev.daymark.data.local.markDirty
import dev.daymark.data.local.settings.ATTENDANCE_PERIOD_KEY
import dev.daymark.data.local.settings.storedValue
import dev.daymark.history.AttendancePeriodSettings
import dev.daymark.models.attendance.DayKey
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.plan.AnnualVacationPolicy
import dev.daymark.models.plan.AnnualVacationSettings
import dev.daymark.models.plan.AttendancePeriodReview
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.models.plan.PlanRejection
import dev.daymark.models.plan.PlanReviewToken
import dev.daymark.models.plan.TimeOffCommand
import dev.daymark.models.plan.TimeOffDayChange
import dev.daymark.models.plan.TimeOffEntry
import dev.daymark.models.plan.TimeOffPreview
import dev.daymark.models.plan.VacationBalanceSummary
import dev.daymark.plan.dates
import dev.daymark.repository.TimeOffDataSource
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

// clean-code exception: constructor arity keeps cross-DAO transaction ownership explicit; revisit when responsibilities can split without changing atomicity.
class TimeOffService(
    private val profileDao: ProfileDao,
    private val attendanceDao: AttendanceDao,
    private val planningDao: PlanningDao,
    private val dirtyProjectionDao: DirtyProjectionDao,
    private val snapshots: PlanningSnapshotReader,
    private val transactions: DatabaseTransactions,
    private val clock: Clock,
    private val attendance: AttendanceRepository
) : TimeOffDataSource {
    override suspend fun previewAttendancePeriod(settings: AttendancePeriodSettings): AttendancePeriodReview? =
        storageResult(null) {
            transactions.withReadTransaction { snapshots.load(clock.now())?.reviewPeriod(settings) }
        }

    override suspend fun saveAttendancePeriod(review: AttendancePeriodReview): PlanMutationResult =
        storageResult(PlanMutationResult.LocalSaveFailed) {
            transactions.withWriteTransaction {
                val store = snapshots.load(clock.now())
                    ?: return@withWriteTransaction PlanMutationResult.StalePreview
                if (review != store.reviewPeriod(review.settings))
                    return@withWriteTransaction PlanMutationResult.StalePreview
                review.rejection?.let { return@withWriteTransaction it }
                if (store.periodSettings != review.settings) {
                    profileDao.putSetting(SettingEntity(ATTENDANCE_PERIOD_KEY, review.settings.storedValue()))
                    markBalanceChanged(store.tracker.id)
                }
                PlanMutationResult.Applied
            }
        }

    override fun observeEntries(): Flow<List<TimeOffEntry>> =
        profileDao.observeTracker().flatMapLatest { tracker ->
            if (tracker == null) flowOf(emptyList()) else planningDao.observeTimeOff(tracker.id)
                .map { entries -> entries.map { it.entry() } }
        }

    override fun observeAvailableBalance(): Flow<Int?> =
        observeVacationBalance().map { it?.remainingDays }

    override fun observeVacationBalance(): Flow<VacationBalanceSummary?> =
        observePlanningCalendar().map { it?.balanceSummary() }

    override fun observeAnnualVacationSettings(): Flow<AnnualVacationSettings?> =
        observePlanningCalendar().map { store ->
            store?.takeIf { it.active.category == TrackerCategory.WORK.name }?.let {
                val year = it.vacationYear
                AnnualVacationSettings(
                    active = it.policies.filter { policy -> policy.effectiveYear <= year }
                        .maxByOrNull { policy -> policy.effectiveYear },
                    pending = it.policies.find { policy -> policy.effectiveYear == year + 1 },
                    nextEffectiveYear = year + 1,
                    nextEffectiveDate = it.periodSettings.vacationYearStart(year + 1)
                )
            }
        }

    private fun observePlanningCalendar(): Flow<PlanningStore?> =
        profileDao.observeTracker().flatMapLatest { tracker ->
            if (tracker == null) flowOf(null) else dirtyProjectionDao.observeDirtyProjections(tracker.id)
                .flatMapLatest {
                    flow {
                        while (true) {
                            val store = transactions.withReadTransaction { snapshots.load(clock.now()) }
                            emit(store)
                            if (store == null) return@flow
                            delay((store.nextBalanceRefresh() - clock.now()).coerceAtLeast(1.milliseconds))
                        }
                    }
                }
        }

    override suspend fun updateAnnualVacationPolicy(
        annualAllowanceDays: Int,
        carryOverUnusedDays: Boolean,
        expectedEffectiveYear: Int
    ): PlanMutationResult = storageResult<PlanMutationResult>(PlanMutationResult.LocalSaveFailed) {
        if (annualAllowanceDays < 0) return@storageResult PlanMutationResult.Invalid(PlanRejection.INVALID_VALUE)
        transactions.withWriteTransaction {
            val store = snapshots.load(clock.now())
                ?: return@withWriteTransaction PlanMutationResult.Invalid(PlanRejection.INVALID_VALUE)
            if (store.active.category != TrackerCategory.WORK.name) {
                return@withWriteTransaction PlanMutationResult.Invalid(PlanRejection.INVALID_VALUE)
            }
            if (expectedEffectiveYear != store.vacationYear + 1) {
                return@withWriteTransaction PlanMutationResult.StalePreview
            }
            val pending = AnnualVacationPolicy(expectedEffectiveYear, annualAllowanceDays, carryOverUnusedDays)
            val versions = store.policies.filterNot { it.effectiveYear == expectedEffectiveYear } + pending
            val changed = store.copy(policies = versions)
            changed.balances().balanceShortfallComparedTo(store.balances())?.let {
                return@withWriteTransaction insufficientVacationBalance(it.key)
            }
            if (pending !in store.policies) {
                planningDao.putAnnualVacationPolicy(
                    AnnualVacationPolicyEntity(
                        store.tracker.id,
                        expectedEffectiveYear,
                        annualAllowanceDays,
                        carryOverUnusedDays
                    )
                )
                markBalanceChanged(store.tracker.id)
            }
            PlanMutationResult.Applied
        }
    }

    override suspend fun preview(command: TimeOffCommand): TimeOffPreview =
        storageResult(invalidPreview(PlanRejection.LOCAL_SAVE_FAILED)) {
            attendance.reconcile(clock.now())
            transactions.withReadTransaction {
                val store =
                    snapshots.load(clock.now()) ?: return@withReadTransaction invalidPreview(
                        PlanRejection.INVALID_VALUE
                    )
                proposal(store, command).preview
            }
        }

    override suspend fun apply(
        command: TimeOffCommand,
        review: PlanReviewToken
    ): PlanMutationResult =
        storageResult<PlanMutationResult>(PlanMutationResult.LocalSaveFailed) {
            transactions.withWriteTransaction {
                attendance.reconcile(clock.now())
                val store = snapshots.load(clock.now())
                    ?: return@withWriteTransaction PlanMutationResult.Invalid(PlanRejection.INVALID_VALUE)
                if (review != store.reviewToken) return@withWriteTransaction PlanMutationResult.StalePreview
                val proposal = proposal(store, command)
                proposal.preview.failure()?.let { return@withWriteTransaction it }
                if (proposal.entries == store.entries) return@withWriteTransaction PlanMutationResult.Applied
                val removed =
                    store.entries.filter { old -> proposal.entries.none { it.id == old.id } }
                removed.forEach { planningDao.deleteTimeOff(it.id) }
                proposal.entries.filter { it !in store.entries }
                    .forEach { planningDao.putTimeOff(it.entity(store.tracker.id)) }
                for (key in proposal.preview.affectedDays) {
                    // Future reservations are projections only: do not freeze future attendance expectations.
                    if (key.date <= store.today) {
                        val before = store.rows[key.date]
                        val row =
                            before ?: AttendanceEntity.fromPlan(store.plan(key.date), store.now)
                        val outcome = store.outcome(key.date, proposal.entries)
                        attendanceDao.putDay(
                            row.copy(
                                revision = (before?.revision ?: 0) + 1,
                                userEditRevision = row.userEditRevision + 1,
                                cachedStatus = outcome.status.name,
                                cachedSource = outcome.source.name,
                                cachedConfirmedAt = outcome.confirmedAt?.toString(),
                                updatedAt = store.now.toString()
                            )
                        )
                    }
                    dirtyProjectionDao.markDirty(key.trackerId, "DAY:${key.date}")
                }
                dirtyProjectionDao.markDirty(store.tracker.id, "TIME_OFF")
                dirtyProjectionDao.markAttendanceSummariesDirty(store.tracker.id)
                profileDao.advanceRevision(store.tracker.id)
                PlanMutationResult.Applied
            }
        }

    override suspend fun setAvailableBalance(
        targetDays: Int,
        note: String?,
        expectedYear: Int?
    ): PlanMutationResult {
        if (targetDays < 0) return PlanMutationResult.Invalid(PlanRejection.INVALID_VALUE)
        return storageResult<PlanMutationResult>(PlanMutationResult.LocalSaveFailed) {
            transactions.withWriteTransaction {
                val store = snapshots.load(clock.now())
                    ?: return@withWriteTransaction PlanMutationResult.Invalid(PlanRejection.INVALID_VALUE)
                val period = store.balanceYear(store.vacationYear)
                if (period != expectedYear) return@withWriteTransaction PlanMutationResult.StalePreview
                val difference = targetDays.toLong() - store.balance()
                if (difference !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) return@withWriteTransaction PlanMutationResult.Invalid(
                    PlanRejection.INVALID_VALUE
                )
                if (difference != 0L) {
                    val adjustment = BalanceAdjustmentEntity(
                        Uuid.random().toString(), store.tracker.id, difference.toInt(),
                        store.now.toString(), note, period
                    )
                    val changed = store.copy(adjustments = store.adjustments + adjustment)
                    changed.balances().balanceShortfallComparedTo(store.balances())?.let {
                        return@withWriteTransaction insufficientVacationBalance(it.key)
                    }
                    planningDao.insertBalanceAdjustment(adjustment)
                    markBalanceChanged(store.tracker.id)
                }
                PlanMutationResult.Applied
            }
        }
    }

    private suspend fun markBalanceChanged(trackerId: String) {
        dirtyProjectionDao.markAttendanceSummariesDirty(trackerId)
        profileDao.advanceRevision(trackerId)
    }

    private data class Proposal(val entries: List<TimeOffEntry>, val preview: TimeOffPreview)

    private fun proposal(store: PlanningStore, command: TimeOffCommand): Proposal {
        val before = store.entries
        val old = when (command) {
            is TimeOffCommand.Replace -> before.find { it.id == command.entryId }
            is TimeOffCommand.Remove -> before.find { it.id == command.entryId }
            else -> null
        }

        fun invalid(reason: PlanRejection) = Proposal(
            before,
            TimeOffPreview(
                store.revision,
                emptyList(),
                emptyList(),
                0,
                store.balance(),
                reason,
                reviewDate = store.today
            )
        )
        if (command !is TimeOffCommand.Add && old == null) return invalid(PlanRejection.INVALID_VALUE)
        val from = when (command) {
            is TimeOffCommand.Add -> command.from; is TimeOffCommand.Replace -> command.from; is TimeOffCommand.Remove -> old!!.from
        }
        val through = when (command) {
            is TimeOffCommand.Add -> command.through; is TimeOffCommand.Replace -> command.through; is TimeOffCommand.Remove -> old!!.through
        }
        if (from > through) return invalid(PlanRejection.INVALID_RANGE)
        val next = when (command) {
            is TimeOffCommand.Remove -> before.filterNot { it.id == old!!.id }
            else -> {
                val kind = when (command) {
                    is TimeOffCommand.Add -> command.kind; is TimeOffCommand.Replace -> command.kind
                }
                if (command is TimeOffCommand.Add && before.any { it.kind == kind && it.from == from && it.through == through }) before
                else if (old?.let { it.kind == kind && it.from == from && it.through == through } == true) before
                else before.filterNot { it.id == old?.id } + TimeOffEntry(
                    old?.id ?: Uuid.random().toString(), kind, from, through, store.now
                )
            }
        }
        val affected = (dates(from, through) + (old?.let { dates(it.from, it.through) }
            ?: emptySequence())).distinct().sorted().toList()
        return Proposal(next, timeOffPreview(store, next, affected))
    }

    private fun timeOffPreview(
        store: PlanningStore,
        entries: List<TimeOffEntry>,
        affected: List<LocalDate>
    ): TimeOffPreview {
        val (earlier, tracked) = affected.partition { it < store.firstDate }
        val impact = store.balanceImpact(affected, entries = entries)
        return TimeOffPreview(
            store.revision, tracked.map { DayKey(store.tracker.id, it) },
            tracked.map { store.outcome(it, entries) }, impact.current.delta, impact.current.remainingDays,
            if (impact.shortfall != null) PlanRejection.INSUFFICIENT_BALANCE else null,
            reviewDate = store.today,
            balancePeriods = impact.changes,
            shortfallYear = impact.shortfall?.year,
            preTrackingDates = earlier,
            changes = tracked.map { date ->
                TimeOffDayChange(
                    DayKey(store.tracker.id, date),
                    store.outcome(date),
                    store.outcome(date, entries),
                    store.rows[date]?.evidence(),
                    store.plan(date).category,
                    store.plan(date).zoneId
                )
            }
        )
    }
}


private fun PlanningStore.nextBalanceRefresh(): Instant {
    val january = periodSettings.vacationYearStart(vacationYear + 1)
        .atStartOfDayIn(TimeZone.of(active.schedule().zoneId))
    val nextPlanActivation = versions.map { Instant.parse(it.effectiveAt) }
        .filter { it > now }.minOrNull()
    return if (nextPlanActivation == null) january else minOf(january, nextPlanActivation)
}
