@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.daymark.data.local.attendance

import androidx.sqlite.SQLiteException
import dev.daymark.attendance.AttendanceRepository
import dev.daymark.attendance.automaticRetryAt
import dev.daymark.attendance.automaticSessionLimit
import dev.daymark.data.local.settings.UI_SETTINGS_KEY
import dev.daymark.data.local.settings.automaticGeneration
import dev.daymark.data.local.settings.decodeAppSettings
import dev.daymark.models.attendance.AutomaticDelivery
import dev.daymark.plan.ScheduledCheck
import dev.daymark.data.local.settings.ATTENDANCE_PERIOD_KEY
import dev.daymark.data.local.settings.attendancePeriodSettings
import dev.daymark.attendance.outcome
import dev.daymark.data.local.AttendanceEntity
import dev.daymark.data.local.DatabaseTransactions
import dev.daymark.data.local.PlanVersionEntity
import dev.daymark.data.local.SettingEntity
import dev.daymark.data.local.TimeOffEntity
import dev.daymark.data.local.TrackerEntity
import dev.daymark.data.local.dao.AttendanceDao
import dev.daymark.data.local.dao.DirtyProjectionDao
import dev.daymark.data.local.dao.PlanningDao
import dev.daymark.data.local.dao.ProfileDao
import dev.daymark.data.local.markAttendanceSummariesDirty
import dev.daymark.data.local.markDirty
import dev.daymark.data.local.plan.PlanningSnapshotReader
import dev.daymark.data.local.plan.projectedPlan
import dev.daymark.logging.AppLogger
import dev.daymark.models.attendance.AttendanceSource
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.models.attendance.AutomaticState
import dev.daymark.models.attendance.CheckCommit
import dev.daymark.models.attendance.CheckMode
import dev.daymark.models.attendance.CheckOutcome
import dev.daymark.models.attendance.CheckRejection
import dev.daymark.models.attendance.ClaimResult
import dev.daymark.models.attendance.Correction
import dev.daymark.models.attendance.DayKey
import dev.daymark.models.attendance.DayOutcome
import dev.daymark.models.attendance.DayPlan
import dev.daymark.models.attendance.FixVerdict
import dev.daymark.models.attendance.HistoryMonth
import dev.daymark.models.attendance.HistoryRecord
import dev.daymark.models.attendance.StoredDay
import dev.daymark.plan.dayEntry
import dev.daymark.data.local.plan.entry
import dev.daymark.plan.resolveLocalArrival
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

// clean-code exception: constructor arity keeps cross-DAO transaction ownership explicit; revisit when responsibilities can split without changing atomicity.
class RoomAttendanceRepository(
    private val profileDao: ProfileDao,
    private val attendanceDao: AttendanceDao,
    private val planningDao: PlanningDao,
    private val dirtyProjectionDao: DirtyProjectionDao,
    private val transactions: DatabaseTransactions,
    private val clock: Clock
) :
    AttendanceRepository {
    private val sessionId = Uuid.random().toString()

    override fun observeDay(key: DayKey): Flow<DayOutcome?> =
        attendanceDao.observeDay(key.trackerId, key.date.toString()).flatMapLatest {
            flow {
                while (true) {
                    val now = clock.now()
                    val day = loadDay(key)
                    val outcome = day?.outcome(now)
                    emit(outcome)
                    val next =
                        if (day != null && outcome != null) nextChange(day.plan, outcome) else null
                    if (next == null) break
                    delay((next - now).coerceAtLeast(1.milliseconds))
                    reconcile(clock.now())
                }
            }
        }

    override fun observeRange(from: LocalDate, through: LocalDate) =
        HistoryRangeReader(
            snapshots = PlanningSnapshotReader(
                profileDao = profileDao,
                attendanceDao = attendanceDao,
                planningDao = planningDao
            ),
            transactions = transactions,
            clock = clock
        ).observe(from = from, through = through)

    override fun observeMonth(year: Int, month: Int): Flow<HistoryMonth> {
        val label = LocalDate(year, month, 1)
        return profileDao.observeTracker().flatMapLatest { tracker ->
            if (tracker == null) flowOf(HistoryMonth(emptyList(), 0, 0, 0))
            else transactions.observeChanges("attendance", "time_off", "settings").flatMapLatest {
                flow {
                    while (true) {
                        val now = clock.now()
                        val history = transactions.withReadTransaction {
                            val entries = planningDao.timeOff(tracker.id)
                            val period = attendancePeriodSettings(profileDao.setting(ATTENDANCE_PERIOD_KEY)?.value)
                                .period(label)
                            val records = attendanceDao.daysBetween(
                                trackerId = tracker.id,
                                from = period.from.toString(),
                                through = period.through.toString()
                            ).map { row ->
                                val day = stored(row, entries)
                                HistoryRecord(day.plan, day.evidence, day.outcome(now))
                            }
                            HistoryMonth(
                                records,
                                records.count { it.outcome.status == AttendanceStatus.PRESENT },
                                records.count { it.outcome.status == AttendanceStatus.LATE },
                                records.count { it.outcome.status == AttendanceStatus.ABSENT })
                        }
                        emit(history)
                        val next = history.records.mapNotNull { nextChange(it.plan, it.outcome) }
                            .minOrNull() ?: break
                        delay((next - now).coerceAtLeast(1.milliseconds))
                        reconcile(clock.now())
                    }
                }
            }
        }
    }

    override suspend fun loadCurrentDay(now: Instant): StoredDay? =
        transactions.withReadTransaction {
            val context = context(now) ?: return@withReadTransaction null
            val key = DayKey(
                context.tracker.id,
                now.toLocalDateTime(TimeZone.of(context.active.schedule().zoneId)).date
            )
            val row = attendanceDao.day(key.trackerId, key.date.toString()) ?: project(
                context,
                key,
                now
            )?.copy(revision = 0)
            row?.let { stored(it, planningDao.timeOff(key.trackerId)) }
        }

    override suspend fun loadDay(key: DayKey): StoredDay? = transactions.withReadTransaction {
        attendanceDao.day(key.trackerId, key.date.toString())
            ?.let { stored(it, planningDao.timeOff(key.trackerId)) }
    }

    override suspend fun reconcile(now: Instant) = transactions.withWriteTransaction {
        val context = context(now = now) ?: return@withWriteTransaction
        val trackerId = context.tracker.id
        val today = now.toLocalDateTime(TimeZone.of(context.active.schedule().zoneId)).date
        val cursorKey = "attendance-reconciled:$trackerId"
        val cursor = profileDao.setting(key = cursorKey)?.value?.let(::readReconciliationCursor)
        markPlanningBoundary(
            trackerId = trackerId,
            today = today,
            planVersion = context.active.version
        )
        val start = Instant.parse(input = context.tracker.trackingStartsAt)
        val firstDate =
            start.toLocalDateTime(TimeZone.of(context.versions.first().schedule().zoneId)).date
        // A clock rollback can make a formerly overdue row eligible again, including outside today's zone.
        val forward = cursor?.takeIf { now >= it.evaluatedAt && today >= it.date }
        if (cursor != null && forward == null) AppLogger.d(
            tag = "RoomAttendanceRepository",
            message = "Rechecking attendance after clock rollback"
        )
        val dates = maxOf(firstDate, forward?.date ?: firstDate)..today
        val rows =
            if (forward == null) attendanceDao.days(trackerId = trackerId).associateBy { it.date }
            else reconciliationRows(trackerId = trackerId, dates = dates)
        val entries = planningDao.timeOff(trackerId = trackerId)
        refreshSavedDays(rows = rows.values, entries = entries, now = now)
        fillUnmaterializedDays(
            context = context,
            window = ReconciliationWindow(dates = dates, trackingStartsAt = start, now = now),
            savedDates = rows.keys,
            entries = entries
        )
        val next = ReconciliationCursor(date = today, evaluatedAt = now)
        if (next != cursor) profileDao.putSetting(
            setting = SettingEntity(
                key = cursorKey,
                value = "$today|$now"
            )
        )
    }

    private suspend fun markPlanningBoundary(
        trackerId: String,
        today: LocalDate,
        planVersion: Long
    ) {
        val key = "planning-clock:$trackerId"
        val revision = "$today:$planVersion"
        if (profileDao.setting(key = key)?.value == revision) return
        profileDao.putSetting(setting = SettingEntity(key = key, value = revision))
        profileDao.advanceRevision(trackerId = trackerId)
        dirtyProjectionDao.markAttendanceSummariesDirty(trackerId = trackerId)
    }

    private suspend fun reconciliationRows(
        trackerId: String,
        dates: ClosedRange<LocalDate>
    ): Map<String, AttendanceEntity> {
        val recent = attendanceDao.daysBetween(
            trackerId = trackerId,
            from = dates.start.toString(),
            through = dates.endInclusive.toString()
        )
        return (recent + attendanceDao.awaitingOutcome(trackerId = trackerId) +
                attendanceDao.claimedDays(trackerId = trackerId)).associateBy { it.date }
    }

    private suspend fun refreshSavedDays(
        rows: Collection<AttendanceEntity>,
        entries: List<TimeOffEntity>,
        now: Instant
    ) {
        for (row in rows) {
            val interrupted = row.automaticState == AutomaticState.CLAIMED.name &&
                    row.automaticOwnerSession != sessionId
            val refreshed =
                if (interrupted) row.copy(automaticState = AutomaticState.INTERRUPTED.name) else row
            persist(before = row, after = refreshed, entries = entries, now = now)
        }
    }

    private suspend fun fillUnmaterializedDays(
        context: Context,
        window: ReconciliationWindow,
        savedDates: Set<String>,
        entries: List<TimeOffEntity>
    ) {
        var date = window.dates.start
        while (date <= window.dates.endInclusive) {
            if (date.toString() !in savedDates) {
                val key = DayKey(trackerId = context.tracker.id, date = date)
                val row = project(context = context, key = key, now = window.now)
                if (row != null && Instant.parse(input = row.arrival) >= window.trackingStartsAt) {
                    persist(before = null, after = row, entries = entries, now = window.now)
                }
            }
            date = date.plus(1, DateTimeUnit.DAY)
        }
    }

    private data class ReconciliationWindow(
        val dates: ClosedRange<LocalDate>,
        val trackingStartsAt: Instant,
        val now: Instant
    )

    private data class ReconciliationCursor(val date: LocalDate, val evaluatedAt: Instant)

    private fun readReconciliationCursor(stored: String) = ReconciliationCursor(
        date = LocalDate.parse(input = stored.substringBefore('|')),
        evaluatedAt = Instant.parse(input = stored.substringAfter('|'))
    )

    override suspend fun claimAutomatic(key: DayKey, attemptId: String, now: Instant, ordinal: Int): ClaimResult =
        transactions.withWriteTransaction {
            require(attemptId.isNotBlank())
            val actualNow = clock.now()
            val context = context(actualNow) ?: return@withWriteTransaction ClaimResult.Ineligible
            if (context.tracker.id != key.trackerId || !context.tracker.automaticEnabled) return@withWriteTransaction ClaimResult.Ineligible
            val before = attendanceDao.day(key.trackerId, key.date.toString())
            val row = before ?: project(context, key, actualNow)
            ?: return@withWriteTransaction ClaimResult.Ineligible
            val entries = planningDao.timeOff(key.trackerId)
            val day = stored(row, entries)
            val zone = TimeZone.of(day.plan.zoneId)
            if (actualNow.toLocalDateTime(zone).date != key.date || now.toLocalDateTime(zone).date != key.date)
                return@withWriteTransaction ClaimResult.Ineligible
            val generation = profileDao.automaticGeneration()
            if (!canClaimOrdinal(row = row, ordinal = ordinal, generation = generation, now = actualNow))
                return@withWriteTransaction ClaimResult.AlreadyConsumed
            if (!day.plan.isScheduled || day.off != null || day.evidence != null || day.plan.arrival < Instant.parse(
                    context.tracker.trackingStartsAt
                ) ||
                now < day.plan.arrival || now > day.plan.deadline || actualNow < day.plan.arrival || actualNow > day.plan.deadline
            )
                return@withWriteTransaction ClaimResult.Ineligible
            val limit = if (ordinal == 1) {
                decodeAppSettings(profileDao.setting(key = UI_SETTINGS_KEY)?.value).automaticAttemptLimit
            } else row.automaticAttemptLimit
            val claimed = persist(
                before, row.copy(
                    automaticAttempts = ordinal,
                    automaticAttemptLimit = limit,
                    automaticGeneration = generation,
                    automaticSeriesEditRevision = row.userEditRevision,
                    automaticRetryAt = null,
                    automaticState = AutomaticState.CLAIMED.name,
                    automaticAttemptId = attemptId,
                    automaticOwnerSession = sessionId,
                    automaticClaimedAt = actualNow.toString()
                ), entries, actualNow
            )
            ClaimResult.Claimed(stored(claimed, entries), attemptId)
        }

    private fun canClaimOrdinal(row: AttendanceEntity, ordinal: Int, generation: Long, now: Instant): Boolean {
        if (ordinal == 1) return row.automaticState == AutomaticState.UNCLAIMED.name
        val retryAt = row.automaticRetryAt?.let(Instant::parse) ?: return false
        return row.automaticState == AutomaticState.WAITING_RETRY.name &&
            ordinal == row.automaticAttempts + 1 && ordinal <= row.automaticAttemptLimit &&
            row.automaticGeneration == generation && row.automaticSeriesEditRevision == row.userEditRevision &&
            now >= retryAt && Instant.parse(row.deadline) - now >= automaticSessionLimit
    }

    override suspend fun abandonAutomatic(key: DayKey, attemptId: String) {
        try {
            transactions.withWriteTransaction {
                val row = attendanceDao.day(trackerId = key.trackerId, date = key.date.toString())
                    ?: return@withWriteTransaction
                if (row.automaticState == AutomaticState.CLAIMED.name && ownsAutomaticClaim(row, attemptId)) {
                    attendanceDao.putDay(day = row.copy(automaticState = AutomaticState.INTERRUPTED.name))
                }
            }
        } catch (failure: SQLiteException) {
            AppLogger.e(tag = "RoomAttendanceRepository", message = "Could not persist interrupted claim", throwable = failure)
        }
    }

    // A second reader can mark the claim interrupted while the original owner still has its result.
    private fun ownsAutomaticClaim(row: AttendanceEntity, attemptId: String?): Boolean =
        row.automaticAttemptId == attemptId && row.automaticOwnerSession == sessionId &&
            row.automaticState in setOf(AutomaticState.CLAIMED.name, AutomaticState.INTERRUPTED.name)

    private fun finishAutomatic(row: AttendanceEntity, reason: CheckRejection?, now: Instant): AttendanceEntity {
        val retryAt = reason?.takeIf { row.automaticAttempts < row.automaticAttemptLimit }?.let {
            automaticRetryAt(
                reason = it,
                finishedAt = now, deadline = Instant.parse(row.deadline)
            )
        }
        val finalReason = if (reason == CheckRejection.AFTER_AUTOMATIC_WINDOW && row.automaticAttempts > 1) {
            row.automaticRejection?.let(CheckRejection::valueOf) ?: reason
        } else reason
        return row.copy(
            automaticState = if (retryAt == null) AutomaticState.FINISHED.name else AutomaticState.WAITING_RETRY.name,
            automaticRetryAt = retryAt?.toString(),
            automaticRejection = finalReason?.name,
            automaticResultPending = retryAt == null && finalReason !in setOf(
                CheckRejection.STATE_CHANGED, CheckRejection.CANCELLED, CheckRejection.AFTER_AUTOMATIC_WINDOW
            )
        )
    }

    data class AutomaticRecovery(val next: ScheduledCheck? = null, val delivery: AutomaticDelivery? = null)

    suspend fun recoverAutomatic(now: Instant, locationEnabled: Boolean): AutomaticRecovery =
        transactions.withWriteTransaction {
            val context = context(now = now) ?: return@withWriteTransaction AutomaticRecovery()
            val tracker = context.tracker
            val generation = profileDao.automaticGeneration()
            val entries = planningDao.timeOff(trackerId = tracker.id)
            var recovery = AutomaticRecovery()
            for (row in attendanceDao.automaticRecoveryRows(trackerId = tracker.id)) {
                val day = stored(row = row, entries = entries)
                val plan = day.plan
                val current = plan.key.date == now.toLocalDateTime(TimeZone.of(plan.zoneId)).date &&
                    tracker.automaticEnabled && row.automaticGeneration == generation &&
                    row.automaticSeriesEditRevision == row.userEditRevision && day.off == null && plan.isScheduled
                if (row.automaticState == AutomaticState.WAITING_RETRY.name) {
                    val retryAt = requireNotNull(row.automaticRetryAt).let(Instant::parse)
                    val eligible = current && day.evidence == null
                    if (eligible && locationEnabled && plan.deadline - maxOf(now, retryAt) >= automaticSessionLimit) {
                        recovery = recovery.copy(next = ScheduledCheck(
                            key = plan.key, arrival = plan.arrival, deadline = plan.deadline,
                            attemptOrdinal = row.automaticAttempts + 1, eligibleAt = retryAt
                        ))
                        continue
                    }
                    val terminal = row.cancelWaitingAutomatic().copy(
                        automaticRejection = if (eligible && !locationEnabled) CheckRejection.LOCATION_DISABLED.name
                            else row.automaticRejection
                    )
                    attendanceDao.putDay(day = terminal)
                    if (eligible) recovery = recovery.copy(delivery = terminal.delivery(day.outcome(now)))
                } else {
                    attendanceDao.putDay(day = row.copy(automaticResultPending = false))
                    if (current) recovery = recovery.copy(delivery = row.delivery(day.outcome(now)))
                }
            }
            recovery
        }

    private fun AttendanceEntity.cancelWaitingAutomatic(): AttendanceEntity =
        if (automaticState == AutomaticState.WAITING_RETRY.name) copy(
            automaticState = AutomaticState.FINISHED.name,
            automaticRetryAt = null,
            automaticResultPending = false
        ) else this

    private fun AttendanceEntity.delivery(outcome: DayOutcome): AutomaticDelivery? {
        val rejection = automaticRejection?.let(CheckRejection::valueOf)
        val result = if (rejection != null) CheckOutcome.Rejected(currentDay = outcome, reason = rejection)
        else if (outcome.source == AttendanceSource.AUTOMATIC_GPS) CheckOutcome.Recorded(currentDay = outcome)
        else return null
        if (rejection != null && (outcome.source != AttendanceSource.NONE ||
                outcome.status !in setOf(AttendanceStatus.AWAITING_CHECK, AttendanceStatus.ABSENT))) return null
        return AutomaticDelivery(key = DayKey(trackerId, LocalDate.parse(date)), outcome = result)
    }

    override suspend fun completeCheck(request: CheckCommit): CheckOutcome =
        transactions.withWriteTransaction {
            val now = clock.now()
            val context =
                context(now) ?: return@withWriteTransaction CheckOutcome.StateChanged(null)
            if (context.tracker.id != request.key.trackerId) return@withWriteTransaction CheckOutcome.StateChanged(
                null
            )
            val before = attendanceDao.day(request.key.trackerId, request.key.date.toString())
            val row = before ?: if (request.mode == CheckMode.USER_INITIATED) project(
                context,
                request.key,
                now
            )
            else null
            if (row == null) return@withWriteTransaction CheckOutcome.StateChanged(null)
            val entries = planningDao.timeOff(request.key.trackerId)
            val day = stored(row, entries)
            val current = day.outcome(now)
            val automatic = request.mode == CheckMode.AUTOMATIC
            if (automatic && !ownsAutomaticClaim(row = row, attemptId = request.attemptId)) {
                return@withWriteTransaction CheckOutcome.StateChanged(current)
            }
            fun finished(reason: CheckRejection?) = if (automatic) {
                finishAutomatic(row = row, reason = reason, now = now)
            } else row
            if (request.key.date != now.toLocalDateTime(TimeZone.of(day.plan.zoneId)).date ||
                day.plan.scheduleVersion != request.expectedScheduleVersion || day.plan.place.version != request.expectedPlaceVersion ||
                day.userEditRevision != request.expectedUserEditRevision || day.off != null || !day.plan.isScheduled ||
                (automatic && (!context.tracker.automaticEnabled || row.automaticGeneration != profileDao.automaticGeneration())) ||
                day.evidence?.source == AttendanceSource.MANUAL_CORRECTION
            ) {
                if (before != null) persist(
                    before,
                    finished(CheckRejection.STATE_CHANGED),
                    entries,
                    now
                )
                return@withWriteTransaction CheckOutcome.StateChanged(current)
            }
            val verdict = request.verdict
            if (verdict is FixVerdict.Rejected) {
                if (before != null || request.mode == CheckMode.AUTOMATIC) persist(
                    before,
                    finished(verdict.reason),
                    entries,
                    now
                )
                return@withWriteTransaction CheckOutcome.Rejected(current, verdict.reason)
            }
            verdict as FixVerdict.Accepted
            val expectedStatus =
                if (verdict.confirmedAt <= day.plan.deadline) AttendanceStatus.PRESENT else AttendanceStatus.LATE
            val invalid = when {
                verdict.confirmedAt < day.plan.arrival || verdict.confirmedAt > now || verdict.status != expectedStatus -> CheckRejection.INVALID_FIX
                request.mode == CheckMode.USER_INITIATED && day.plan.departure?.let { verdict.confirmedAt > it } == true -> CheckRejection.AFTER_DEPARTURE
                request.mode == CheckMode.AUTOMATIC && expectedStatus == AttendanceStatus.LATE -> CheckRejection.AFTER_AUTOMATIC_WINDOW
                else -> null
            }
            if (invalid != null) {
                if (before != null) persist(before, finished(invalid), entries, now)
                return@withWriteTransaction CheckOutcome.Rejected(current, invalid)
            }
            if (day.evidence?.confirmedAt?.let { it <= verdict.confirmedAt } == true) {
                persist(before, finished(CheckRejection.STATE_CHANGED), entries, now)
                return@withWriteTransaction CheckOutcome.AlreadyRecorded(current)
            }
            val source =
                if (request.mode == CheckMode.AUTOMATIC) AttendanceSource.AUTOMATIC_GPS else AttendanceSource.MANUAL_GPS
            val saved = persist(
                before, (if (automatic) finished(null) else row.cancelWaitingAutomatic()).copy(
                    evidenceStatus = verdict.status.name,
                    evidenceSource = source.name,
                    confirmedAt = verdict.confirmedAt.toString(),
                    note = null,
                    evidenceUpdatedAt = now.toString()
                ), entries, now
            )
            CheckOutcome.Recorded(stored(saved, entries).outcome(now))
        }

    override suspend fun correct(command: Correction): DayOutcome =
        transactions.withWriteTransaction {
            require(
                command.status in listOf(
                    AttendanceStatus.PRESENT,
                    AttendanceStatus.LATE,
                    AttendanceStatus.ABSENT
                )
            )
            val now = clock.now()
            val context = requireNotNull(context(now)) { "Tracker is unavailable" }
            require(command.key.trackerId == context.tracker.id)
            val before = attendanceDao.day(command.key.trackerId, command.key.date.toString())
            val row = requireNotNull(
                before ?: project(
                    context,
                    command.key,
                    now
                )
            ) { "Date is outside the tracked period" }
            val entries = planningDao.timeOff(command.key.trackerId)
            val day = stored(row, entries)
            require(command.key.date <= now.toLocalDateTime(TimeZone.of(day.plan.zoneId)).date)
            require(day.plan.isScheduled && day.off == null) { "Edit time off before correcting attendance" }
            val confirmed = if (command.status == AttendanceStatus.ABSENT) null
            else resolveLocalArrival(
                command.key.date,
                requireNotNull(command.arrivalTime),
                day.plan.zoneId
            )
            val saved = persist(
                before, row.cancelWaitingAutomatic().copy(
                    evidenceStatus = command.status.name,
                    evidenceSource = AttendanceSource.MANUAL_CORRECTION.name,
                    confirmedAt = confirmed?.toString(),
                    note = command.note,
                    evidenceUpdatedAt = now.toString(),
                    userEditRevision = row.userEditRevision + 1
                ), entries, now
            )
            stored(saved, entries).outcome(now)
        }

    private data class Context(
        val tracker: TrackerEntity,
        val versions: List<PlanVersionEntity>,
        val active: PlanVersionEntity
    )

    private suspend fun context(now: Instant): Context? {
        val tracker = profileDao.tracker() ?: return null
        val versions = planningDao.versions(tracker.id)
        require(versions.isNotEmpty()) { "Tracker expectations are unavailable" }
        val active = versions.filter { Instant.parse(it.effectiveAt) <= now }
            .maxWithOrNull(compareBy<PlanVersionEntity> { Instant.parse(it.effectiveAt) }.thenBy { it.version })
            ?: versions.first()
        return Context(tracker, versions, active)
    }

    private fun project(context: Context, key: DayKey, now: Instant): AttendanceEntity? {
        if (key.trackerId != context.tracker.id) return null
        val plan = projectedPlan(context.versions, key) ?: return null
        return AttendanceEntity.fromPlan(plan, now)
    }

    private fun stored(row: AttendanceEntity, entries: List<TimeOffEntity>): StoredDay {
        val plan = row.plan()
        val matching = entries.filter { row.date in it.startDate..it.endDate }
        return StoredDay(
            plan,
            row.evidence(),
            dayEntry(matching.map { it.entry() }, plan.key.date, plan.category)?.kind,
            AutomaticState.valueOf(row.automaticState),
            row.revision,
            row.userEditRevision
        )
    }

    private suspend fun persist(
        before: AttendanceEntity?,
        after: AttendanceEntity,
        entries: List<TimeOffEntity>,
        now: Instant
    ): AttendanceEntity {
        val outcome = stored(after, entries).outcome(now)
        val cached = after.copy(
            cachedStatus = outcome.status.name,
            cachedSource = outcome.source.name,
            cachedConfirmedAt = outcome.confirmedAt?.toString()
        )
        if (before != null && cached == before) return before
        val saved = cached.copy(revision = (before?.revision ?: 0) + 1, updatedAt = now.toString())
        attendanceDao.putDay(saved)
        profileDao.advanceRevision(saved.trackerId)
        dirtyProjectionDao.markDirty(saved.trackerId, "DAY:${saved.date}")
        dirtyProjectionDao.markAttendanceSummariesDirty(saved.trackerId)
        return saved
    }

    private fun nextChange(plan: DayPlan, outcome: DayOutcome): Instant? = when (outcome.status) {
        AttendanceStatus.SCHEDULED -> plan.arrival
        AttendanceStatus.AWAITING_CHECK -> plan.deadline + 1.nanoseconds
        else -> null
    }
}
