@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.daymark.plan

import androidx.room3.*
import dev.daymark.attendance.*
import dev.daymark.data.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.uuid.Uuid

class TimeOffService(private val db: DaymarkDatabase, private val clock: Clock) {
    private val dao get() = db.dao()
    private val attendance = RoomAttendanceRepository(db, clock)
    fun observeEntries(): Flow<List<TimeOffEntry>> = dao.observeTracker().flatMapLatest { tracker ->
        if (tracker == null) flowOf(emptyList()) else dao.observeTimeOff(tracker.id).map { entries -> entries.map { it.entry() } }
    }
    fun observeAvailableBalance(): Flow<Int?> = dao.observeTracker().flatMapLatest { tracker ->
        if (tracker == null) flowOf(null) else dao.observeDirtyProjections(tracker.id).map {
            db.withReadTransaction { dao.planning(clock.now())?.balance() }
        }
    }
    suspend fun preview(command: TimeOffCommand): TimeOffPreview = storageResult(invalidPreview(PlanRejection.LOCAL_SAVE_FAILED)) {
        attendance.reconcile(clock.now())
        db.withReadTransaction {
            val store = dao.planning(clock.now()) ?: return@withReadTransaction invalidPreview(PlanRejection.INVALID_VALUE)
            proposal(store, command).preview
        }
    }
    suspend fun apply(command: TimeOffCommand, expectedRevision: Long): PlanMutationResult = storageResult<PlanMutationResult>(PlanMutationResult.LocalSaveFailed) {
        db.withWriteTransaction {
            attendance.reconcile(clock.now())
            val store = dao.planning(clock.now()) ?: return@withWriteTransaction PlanMutationResult.Invalid(PlanRejection.INVALID_VALUE)
            val proposal = proposal(store, command)
            if (expectedRevision != store.revision) return@withWriteTransaction PlanMutationResult.StalePreview
            proposal.preview.failure()?.let { return@withWriteTransaction it }
            if (proposal.entries == store.entries) return@withWriteTransaction PlanMutationResult.Applied
            val removed = store.entries.filter { old -> proposal.entries.none { it.id == old.id } }
            removed.forEach { dao.deleteTimeOff(it.id) }
            proposal.entries.filter { it !in store.entries }.forEach { dao.putTimeOff(it.entity(store.tracker.id)) }
            for (key in proposal.preview.affectedDays) {
                // Future reservations are projections only: do not freeze future attendance expectations.
                if (key.date <= store.today) {
                    val before = store.rows[key.date]
                    val row = before ?: AttendanceEntity.fromPlan(store.plan(key.date), store.now)
                    val outcome = store.outcome(key.date, proposal.entries)
                    dao.putDay(row.copy(revision = (before?.revision ?: 0) + 1, userEditRevision = row.userEditRevision + 1,
                        cachedStatus = outcome.status.name, cachedSource = outcome.source.name,
                        cachedConfirmedAt = outcome.confirmedAt?.toString(), updatedAt = store.now.toString()))
                }
                dao.markDirty(key.trackerId, "DAY:${key.date}")
            }
            dao.markDirty(store.tracker.id, "TIME_OFF")
            dao.markDirty(store.tracker.id, "OVERVIEW")
            dao.advanceRevision(store.tracker.id)
            PlanMutationResult.Applied
        }
    }
    suspend fun setAvailableBalance(targetDays: Int, note: String?): PlanMutationResult {
        if (targetDays < 0) return PlanMutationResult.Invalid(PlanRejection.INVALID_VALUE)
        return storageResult<PlanMutationResult>(PlanMutationResult.LocalSaveFailed) {
            db.withWriteTransaction {
                val store = dao.planning(clock.now()) ?: return@withWriteTransaction PlanMutationResult.Invalid(PlanRejection.INVALID_VALUE)
                val difference = targetDays.toLong() - store.balance()
                if (difference !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) return@withWriteTransaction PlanMutationResult.Invalid(PlanRejection.INVALID_VALUE)
                if (difference != 0L) {
                    dao.insertBalanceAdjustment(BalanceAdjustmentEntity(Uuid.random().toString(), store.tracker.id, difference.toInt(), store.now.toString(), note))
                    dao.markDirty(store.tracker.id, "OVERVIEW")
                    dao.advanceRevision(store.tracker.id)
                }
                PlanMutationResult.Applied
            }
        }
    }
    private data class Proposal(val entries: List<TimeOffEntry>, val preview: TimeOffPreview)
    private fun proposal(store: PlanningStore, command: TimeOffCommand): Proposal {
        val before = store.entries
        val old = when (command) {
            is TimeOffCommand.Replace -> before.find { it.id == command.entryId }
            is TimeOffCommand.Remove -> before.find { it.id == command.entryId }
            else -> null
        }
        fun invalid(reason: PlanRejection) = Proposal(before, TimeOffPreview(store.revision, emptyList(), emptyList(), 0, store.balance(), reason))
        if (command !is TimeOffCommand.Add && old == null) return invalid(PlanRejection.INVALID_VALUE)
        val from = when (command) { is TimeOffCommand.Add -> command.from; is TimeOffCommand.Replace -> command.from; is TimeOffCommand.Remove -> old!!.from }
        val through = when (command) { is TimeOffCommand.Add -> command.through; is TimeOffCommand.Replace -> command.through; is TimeOffCommand.Remove -> old!!.through }
        if (from > through) return invalid(PlanRejection.INVALID_RANGE)
        if (from < store.firstDate) return invalid(PlanRejection.OUTSIDE_TRACKED_PERIOD)
        val next = when (command) {
            is TimeOffCommand.Remove -> before.filterNot { it.id == old!!.id }
            else -> {
                val kind = when (command) { is TimeOffCommand.Add -> command.kind; is TimeOffCommand.Replace -> command.kind }
                if (command is TimeOffCommand.Add && before.any { it.kind == kind && it.from == from && it.through == through }) before
                else if (old?.let { it.kind == kind && it.from == from && it.through == through } == true) before
                else before.filterNot { it.id == old?.id } + TimeOffEntry(old?.id ?: Uuid.random().toString(), kind, from, through, store.now)
            }
        }
        val affected = (dates(from, through) + (old?.let { dates(it.from, it.through) } ?: emptySequence())).distinct().sorted().toList()
        val available = store.balance(next)
        val delta = available - store.balance()
        return Proposal(next, TimeOffPreview(store.revision, affected.map { DayKey(store.tracker.id, it) },
            affected.map { store.outcome(it, next) }, delta, available,
            if (delta < 0 && available < 0) PlanRejection.INSUFFICIENT_BALANCE else null,
            changes=affected.map { date -> TimeOffDayChange(DayKey(store.tracker.id,date),store.outcome(date),
                store.outcome(date,next),store.rows[date]?.evidence(),store.plan(date).category,store.plan(date).zoneId) }))
    }
}
