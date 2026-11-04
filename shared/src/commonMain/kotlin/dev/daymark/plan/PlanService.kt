@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.daymark.plan

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import dev.daymark.attendance.DayKey
import dev.daymark.attendance.AttendanceRepository
import dev.daymark.attendance.RoomAttendanceRepository
import dev.daymark.data.DaymarkDatabase
import dev.daymark.data.PlanVersionEntity
import dev.daymark.data.markDirty
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlin.time.Clock
import kotlin.time.Instant

class PlanService(
    private val db: DaymarkDatabase, private val clock: Clock,
    private val attendance: AttendanceRepository = RoomAttendanceRepository(db, clock)
) {
    private val dao get() = db.dao()
    fun observePlan(): Flow<PlanState?> = dao.observeTracker().map {
        db.withReadTransaction {
            val store = dao.planning(clock.now()) ?: return@withReadTransaction null
            val pending = store.versions.filter { Instant.parse(it.effectiveAt) > store.now }
                .maxByOrNull { it.version }
            PlanState(
                store.active.change(),
                pending?.change(),
                pending?.let { LocalDate.parse(it.effectiveDate) })
        }
    }

    suspend fun previewChange(command: PlanChange): TimeOffPreview =
        storageResult(invalidPreview(PlanRejection.LOCAL_SAVE_FAILED)) {
            attendance.reconcile(clock.now())
            db.withReadTransaction {
                val store = dao.planning(clock.now()) ?: return@withReadTransaction invalidPreview(
                    PlanRejection.INVALID_VALUE
                )
                proposal(store, command).preview
            }
        }

    suspend fun applyChange(command: PlanChange, expectedRevision: Long): PlanMutationResult =
        storageResult<PlanMutationResult>(PlanMutationResult.LocalSaveFailed) {
            db.withWriteTransaction {
                attendance.reconcile(clock.now())
                val store = dao.planning(clock.now())
                    ?: return@withWriteTransaction PlanMutationResult.Invalid(PlanRejection.INVALID_VALUE)
                if (expectedRevision != store.revision) return@withWriteTransaction PlanMutationResult.StalePreview
                val proposal = proposal(store, command)
                proposal.preview.failure()?.let { return@withWriteTransaction it }
                dao.putVersion(proposal.version)
                proposal.preview.affectedDays.forEach {
                    dao.markDirty(
                        it.trackerId,
                        "DAY:${it.date}"
                    )
                }
                dao.markDirty(store.tracker.id, "TIME_OFF")
                dao.markDirty(store.tracker.id, "OVERVIEW")
                dao.advanceRevision(store.tracker.id)
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
                dates(effective, effective.plus(6, DateTimeUnit.DAY))).distinct().sorted().toList()
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
                effective
            )
        )
        val after = store.balance(expectations = versions)
        val delta = after - store.balance()
        return Proposal(
            version, TimeOffPreview(
                store.revision, affected.map { DayKey(store.tracker.id, it) },
                affected.map { store.outcome(it, expectations = versions) }, delta, after,
                if (delta < 0 && after < 0) PlanRejection.INSUFFICIENT_BALANCE else null, effective
            )
        )
    }
}
