@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.daymark.attendance

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import dev.daymark.data.*
import dev.daymark.plan.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.datetime.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

class RoomAttendanceRepository(private val db: DaymarkDatabase, private val clock: Clock) : AttendanceRepository {
    private val sessionId = Uuid.random().toString()
    private val dao get() = db.dao()

    override fun observeDay(key: DayKey): Flow<DayOutcome?> = dao.observeDay(key.trackerId, key.date.toString()).flatMapLatest {
        flow {
            while (true) {
                val now = clock.now()
                val day = loadDay(key)
                val outcome = day?.outcome(now)
                emit(outcome)
                val next = if (day != null && outcome != null) nextChange(day.plan, outcome) else null
                if (next == null) break
                delay((next - now).coerceAtLeast(1.milliseconds))
                reconcile(clock.now())
            }
        }
    }

    override fun observeMonth(year: Int, month: Int): Flow<HistoryMonth> {
        LocalDate(year, month, 1)
        return dao.observeTracker().flatMapLatest { tracker ->
            if (tracker == null) flowOf(HistoryMonth(emptyList(), 0, 0, 0))
            else dao.observeDays(tracker.id).flatMapLatest {
                flow {
                    while (true) {
                        val now = clock.now()
                        val history = db.withReadTransaction {
                            val entries = dao.timeOff(tracker.id)
                            val records = dao.days(tracker.id).filter {
                                val date = LocalDate.parse(it.date); date.year == year && date.month.number == month
                            }.map { row ->
                                val day = stored(row, entries)
                                HistoryRecord(day.plan, day.evidence, day.outcome(now))
                            }.sortedBy { it.plan.key.date }
                            HistoryMonth(records, records.count { it.outcome.status == AttendanceStatus.PRESENT },
                                records.count { it.outcome.status == AttendanceStatus.LATE }, records.count { it.outcome.status == AttendanceStatus.ABSENT })
                        }
                        emit(history)
                        val next = history.records.mapNotNull { nextChange(it.plan, it.outcome) }.minOrNull() ?: break
                        delay((next - now).coerceAtLeast(1.milliseconds))
                        reconcile(clock.now())
                    }
                }
            }
        }
    }

    override suspend fun loadCurrentDay(now: Instant): StoredDay? = db.withReadTransaction {
        val context = context(now) ?: return@withReadTransaction null
        val key = DayKey(context.tracker.id, now.toLocalDateTime(TimeZone.of(context.active.schedule().zoneId)).date)
        val row = dao.day(key.trackerId, key.date.toString()) ?: project(context, key, now)?.copy(revision = 0)
        row?.let { stored(it, dao.timeOff(key.trackerId)) }
    }
    override suspend fun loadDay(key: DayKey): StoredDay? = db.withReadTransaction {
        dao.day(key.trackerId, key.date.toString())?.let { stored(it, dao.timeOff(key.trackerId)) }
    }

    override suspend fun reconcile(now: Instant) = db.withWriteTransaction {
        val context = context(now) ?: return@withWriteTransaction
        val entries = dao.timeOff(context.tracker.id)
        val rows = dao.days(context.tracker.id).associateBy { it.date }.toMutableMap()
        val start = Instant.parse(context.tracker.trackingStartsAt)
        var date = start.toLocalDateTime(TimeZone.of(context.versions.first().schedule().zoneId)).date
        val today = now.toLocalDateTime(TimeZone.of(context.active.schedule().zoneId)).date
        val clockKey = "planning-clock:${context.tracker.id}"
        val clockValue = "$today:${context.active.version}"
        if (dao.setting(clockKey)?.value != clockValue) {
            dao.putSetting(SettingEntity(clockKey, clockValue))
            dao.advanceRevision(context.tracker.id)
            dao.markDirty(context.tracker.id, "OVERVIEW")
        }
        val inserted = mutableSetOf<String>()
        while (date <= today) {
            if (date.toString() !in rows) {
                val projected = project(context, DayKey(context.tracker.id, date), now)
                if (projected != null && Instant.parse(projected.arrival) >= start) {
                    rows[projected.date] = projected; inserted += projected.date
                }
            }
            date = date.plus(1, DateTimeUnit.DAY)
        }
        for (row in rows.values) {
            val interrupted = row.automaticState == AutomaticState.CLAIMED.name &&
                (row.automaticOwnerSession != sessionId || row.automaticClaimedAt?.let { now - Instant.parse(it) >= 30.seconds } == true)
            val changed = if (interrupted) row.copy(automaticState = AutomaticState.INTERRUPTED.name) else row
            persist(if (row.date in inserted) null else row, changed, entries, now)
        }
    }

    override suspend fun claimAutomatic(key: DayKey, attemptId: String, now: Instant): ClaimResult = db.withWriteTransaction {
        require(attemptId.isNotBlank())
        val actualNow = clock.now()
        val context = context(actualNow) ?: return@withWriteTransaction ClaimResult.Ineligible
        if (context.tracker.id != key.trackerId || !context.tracker.automaticEnabled) return@withWriteTransaction ClaimResult.Ineligible
        val before = dao.day(key.trackerId, key.date.toString())
        val row = before ?: project(context, key, actualNow) ?: return@withWriteTransaction ClaimResult.Ineligible
        val entries = dao.timeOff(key.trackerId)
        val day = stored(row, entries)
        val zone = TimeZone.of(day.plan.zoneId)
        if (actualNow.toLocalDateTime(zone).date != key.date || now.toLocalDateTime(zone).date != key.date)
            return@withWriteTransaction ClaimResult.Ineligible
        if (day.automaticState != AutomaticState.UNCLAIMED) return@withWriteTransaction ClaimResult.AlreadyConsumed
        if (!day.plan.isScheduled || day.off != null || day.evidence != null || day.plan.arrival < Instant.parse(context.tracker.trackingStartsAt) ||
            now < day.plan.arrival || now > day.plan.deadline || actualNow < day.plan.arrival || actualNow > day.plan.deadline)
            return@withWriteTransaction ClaimResult.Ineligible
        val claimed = persist(before, row.copy(automaticState = AutomaticState.CLAIMED.name,
            automaticAttemptId = attemptId, automaticOwnerSession = sessionId, automaticClaimedAt = actualNow.toString()), entries, actualNow)
        ClaimResult.Claimed(stored(claimed, entries), attemptId)
    }

    override suspend fun completeCheck(request: CheckCommit): CheckOutcome = db.withWriteTransaction {
        val now = clock.now()
        val context = context(now) ?: return@withWriteTransaction CheckOutcome.StateChanged(null)
        if (context.tracker.id != request.key.trackerId) return@withWriteTransaction CheckOutcome.StateChanged(null)
        val before = dao.day(request.key.trackerId, request.key.date.toString())
        val row = before ?: if (request.mode == CheckMode.USER_INITIATED) project(context, request.key, now)
            else null
        if (row == null) return@withWriteTransaction CheckOutcome.StateChanged(null)
        val entries = dao.timeOff(request.key.trackerId)
        val day = stored(row, entries)
        val current = day.outcome(now)
        val autoMatches = request.mode != CheckMode.AUTOMATIC || (row.automaticAttemptId == request.attemptId &&
            row.automaticState in listOf(AutomaticState.CLAIMED.name, AutomaticState.INTERRUPTED.name))
        if (!autoMatches) return@withWriteTransaction CheckOutcome.StateChanged(current)
        fun finished(reason: CheckRejection?) = if (request.mode == CheckMode.AUTOMATIC)
            row.copy(automaticState = AutomaticState.FINISHED.name, automaticRejection = reason?.name) else row
        if (request.key.date != now.toLocalDateTime(TimeZone.of(day.plan.zoneId)).date ||
            day.plan.scheduleVersion != request.expectedScheduleVersion || day.plan.place.version != request.expectedPlaceVersion ||
            day.userEditRevision != request.expectedUserEditRevision || day.off != null || !day.plan.isScheduled ||
            day.evidence?.source == AttendanceSource.MANUAL_CORRECTION) {
            if (before != null) persist(before, finished(CheckRejection.STATE_CHANGED), entries, now)
            return@withWriteTransaction CheckOutcome.StateChanged(current)
        }
        val verdict = request.verdict
        if (verdict is FixVerdict.Rejected) {
            if (before != null || request.mode == CheckMode.AUTOMATIC) persist(before, finished(verdict.reason), entries, now)
            return@withWriteTransaction CheckOutcome.Rejected(current, verdict.reason)
        }
        verdict as FixVerdict.Accepted
        val expectedStatus = if (verdict.confirmedAt <= day.plan.deadline) AttendanceStatus.PRESENT else AttendanceStatus.LATE
        val invalid = when {
            verdict.confirmedAt < day.plan.arrival || verdict.confirmedAt > now || verdict.status != expectedStatus -> CheckRejection.INVALID_FIX
            request.mode == CheckMode.AUTOMATIC && expectedStatus == AttendanceStatus.LATE -> CheckRejection.AFTER_AUTOMATIC_WINDOW
            else -> null
        }
        if (invalid != null) {
            if (before != null) persist(before, finished(invalid), entries, now)
            return@withWriteTransaction CheckOutcome.Rejected(current, invalid)
        }
        if (day.evidence?.confirmedAt?.let { it <= verdict.confirmedAt } == true) {
            persist(before, finished(null), entries, now)
            return@withWriteTransaction CheckOutcome.AlreadyRecorded(current)
        }
        val source = if (request.mode == CheckMode.AUTOMATIC) AttendanceSource.AUTOMATIC_GPS else AttendanceSource.MANUAL_GPS
        val saved = persist(before, finished(null).copy(evidenceStatus = verdict.status.name, evidenceSource = source.name,
            confirmedAt = verdict.confirmedAt.toString(), note = null, evidenceUpdatedAt = now.toString()), entries, now)
        CheckOutcome.Recorded(stored(saved, entries).outcome(now))
    }

    override suspend fun correct(command: Correction): DayOutcome = db.withWriteTransaction {
        require(command.status in listOf(AttendanceStatus.PRESENT, AttendanceStatus.LATE, AttendanceStatus.ABSENT))
        val now = clock.now()
        val context = requireNotNull(context(now)) { "Tracker is unavailable" }
        require(command.key.trackerId == context.tracker.id)
        val before = dao.day(command.key.trackerId, command.key.date.toString())
        val row = requireNotNull(before ?: project(context, command.key, now)) { "Date is outside the tracked period" }
        val entries = dao.timeOff(command.key.trackerId)
        val day = stored(row, entries)
        require(command.key.date <= now.toLocalDateTime(TimeZone.of(day.plan.zoneId)).date)
        require(day.plan.isScheduled && day.off == null) { "Edit time off before correcting attendance" }
        val confirmed = if (command.status == AttendanceStatus.ABSENT) null
            else resolveLocalArrival(command.key.date, requireNotNull(command.arrivalTime), day.plan.zoneId)
        val saved = persist(before, row.copy(evidenceStatus = command.status.name, evidenceSource = AttendanceSource.MANUAL_CORRECTION.name,
            confirmedAt = confirmed?.toString(), note = command.note, evidenceUpdatedAt = now.toString(),
            userEditRevision = row.userEditRevision + 1), entries, now)
        stored(saved, entries).outcome(now)
    }

    private data class Context(val tracker: TrackerEntity, val versions: List<PlanVersionEntity>, val active: PlanVersionEntity)
    private suspend fun context(now: Instant): Context? {
        val tracker = dao.tracker() ?: return null
        val versions = dao.versions(tracker.id)
        require(versions.isNotEmpty()) { "Tracker expectations are unavailable" }
        val active = versions.filter { Instant.parse(it.effectiveAt) <= now }
            .maxWithOrNull(compareBy<PlanVersionEntity> { Instant.parse(it.effectiveAt) }.thenBy { it.version }) ?: versions.first()
        return Context(tracker, versions, active)
    }
    private fun project(context: Context, key: DayKey, now: Instant): AttendanceEntity? {
        if (key.trackerId != context.tracker.id) return null
        val plan = projectedPlan(context.versions, key) ?: return null
        return AttendanceEntity.fromPlan(plan, now)
    }
    private fun stored(row: AttendanceEntity, entries: List<TimeOffEntity>): StoredDay {
        val date = LocalDate.parse(row.date)
        val matching = entries.filter { LocalDate.parse(it.startDate) <= date && LocalDate.parse(it.endDate) >= date }
        val off = when {
            matching.any { it.kind == TimeOffKind.HOLIDAY.name } -> TimeOffKind.HOLIDAY
            matching.any { it.kind == TimeOffKind.VACATION.name } -> TimeOffKind.VACATION
            else -> null
        }
        return StoredDay(row.plan(), row.evidence(), off, AutomaticState.valueOf(row.automaticState), row.revision, row.userEditRevision)
    }
    private suspend fun persist(before: AttendanceEntity?, after: AttendanceEntity, entries: List<TimeOffEntity>, now: Instant): AttendanceEntity {
        val outcome = stored(after, entries).outcome(now)
        val cached = after.copy(cachedStatus = outcome.status.name, cachedSource = outcome.source.name, cachedConfirmedAt = outcome.confirmedAt?.toString())
        if (before != null && cached == before) return before
        val saved = cached.copy(revision = (before?.revision ?: 0) + 1, updatedAt = now.toString())
        dao.putDay(saved)
        dao.advanceRevision(saved.trackerId)
        dao.markDirty(saved.trackerId, "DAY:${saved.date}")
        dao.markDirty(saved.trackerId, "OVERVIEW")
        return saved
    }
    private fun nextChange(plan: DayPlan, outcome: DayOutcome): Instant? = when (outcome.status) {
        AttendanceStatus.SCHEDULED -> plan.arrival
        AttendanceStatus.AWAITING_CHECK -> plan.deadline + 1.nanoseconds
        else -> null
    }
}
