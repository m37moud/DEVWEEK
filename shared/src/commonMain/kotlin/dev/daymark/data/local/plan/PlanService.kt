@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.daymark.data.local.plan

import dev.daymark.attendance.AttendanceRepository
import dev.daymark.data.local.DatabaseTransactions
import dev.daymark.data.local.PlanVersionEntity
import dev.daymark.data.local.dao.DirtyProjectionDao
import dev.daymark.data.local.dao.PlanningDao
import dev.daymark.data.local.dao.ProfileDao
import dev.daymark.data.local.markAttendanceSummariesDirty
import dev.daymark.data.local.markDirty
import dev.daymark.models.attendance.DayKey
import dev.daymark.models.plan.PlanChange
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.models.plan.PlanRejection
import dev.daymark.models.plan.PlanReviewToken
import dev.daymark.models.plan.PlanState
import dev.daymark.models.plan.TimeOffPreview
import dev.daymark.plan.dates
import dev.daymark.plan.resolveLocalArrival
import dev.daymark.repository.PlanDataSource
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

// clean-code exception: constructor arity keeps cross-DAO transaction ownership explicit; revisit when responsibilities can split without changing atomicity.
class PlanService(
    private val profileDao: ProfileDao,
    private val planningDao: PlanningDao,
    private val dirtyProjectionDao: DirtyProjectionDao,
    private val snapshots: PlanningSnapshotReader,
    private val transactions: DatabaseTransactions,
    private val clock: Clock,
    private val attendance: AttendanceRepository
) : PlanDataSource {
    override fun observePlan(): Flow<PlanState?> = profileDao.observeTracker().map {
        transactions.withReadTransaction {
            val store = snapshots.load(clock.now()) ?: return@withReadTransaction null
            val pending = store.versions.filter { Instant.parse(it.effectiveAt) > store.now }
                .maxByOrNull { it.version }
            PlanState(
                store.active.change(),
                pending?.change(),
                pending?.let { LocalDate.parse(it.effectiveDate) },
                (pending ?: store.active).placeName
            )
        }
    }

    override suspend fun previewChange(command: PlanChange): TimeOffPreview =
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

    override suspend fun applyChange(
        command: PlanChange,
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
                planningDao.putVersion(proposal.version)
                proposal.preview.affectedDays.forEach {
                    dirtyProjectionDao.markDirty(
                        it.trackerId,
                        "DAY:${it.date}"
                    )
                }
                dirtyProjectionDao.markDirty(store.tracker.id, "TIME_OFF")
                dirtyProjectionDao.markAttendanceSummariesDirty(store.tracker.id)
                profileDao.advanceRevision(store.tracker.id)
                PlanMutationResult.Applied
            }
        }

    private data class Proposal(val version: PlanVersionEntity, val preview: TimeOffPreview)

    private fun proposal(store: PlanningStore, command: PlanChange): Proposal {
        val effective = store.today.plus(1, DateTimeUnit.DAY)
        val latest = store.versions.maxBy { it.version }
        val samePlace = latest.place()
            .let { it.latitude == command.place.latitude && it.longitude == command.place.longitude && it.radiusMetres == command.place.radiusMetres }
        val place =
            command.place.copy(version = if (samePlace) latest.placeVersion else store.versions.maxOf { it.placeVersion } + 1)
        val version = PlanVersionEntity.fromSnapshot(
            store.tracker.id,
            latest.version + 1,
            effective,
            resolveLocalArrival(effective, LocalTime(0, 0), store.active.schedule().zoneId),
            command.category,
            command.schedule,
            place,
            latest.placeName
        )
        val versions = store.versions + version
        val affected = (store.entries.asSequence().flatMap { dates(it.from, it.through) }
            .filter { it >= effective } +
                dates(effective, effective.plus(6, DateTimeUnit.DAY)))
            .filter { it >= store.firstDate }.distinct().sorted().toList()
        // Resolve every weekday before accepting a schedule, including its same-day grace constraint.
        val valid = try {
            affected.forEach { store.plan(it, versions) }; true
        } catch (_: IllegalArgumentException) {
            false
        }
        if (!valid) return Proposal(
            version,
            TimeOffPreview(
                store.revision,
                emptyList(),
                emptyList(),
                0,
                store.balance(),
                PlanRejection.INVALID_VALUE,
                effective,
                reviewDate = store.today
            )
        )
        val impact = store.balanceImpact(affected, expectations = versions)
        return Proposal(
            version, TimeOffPreview(
                store.revision, affected.map { DayKey(store.tracker.id, it) },
                affected.map { store.outcome(it, expectations = versions) },
                impact.current.delta, impact.current.remainingDays,
                if (impact.shortfall != null) PlanRejection.INSUFFICIENT_BALANCE else null,
                effective,
                balancePeriods = impact.changes,
                shortfallYear = impact.shortfall?.year,
                reviewDate = store.today
            )
        )
    }
}
