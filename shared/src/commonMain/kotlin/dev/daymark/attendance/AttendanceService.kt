package dev.daymark.attendance

import dev.daymark.logging.AppLogger
import dev.daymark.location.LocationFailure
import dev.daymark.location.LocationGateway
import dev.daymark.location.LocationResult
import dev.daymark.models.attendance.AttendanceSource
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.models.attendance.CheckCommit
import dev.daymark.models.attendance.CheckMode
import dev.daymark.models.attendance.CheckOutcome
import dev.daymark.models.attendance.CheckRejection
import dev.daymark.models.attendance.ClaimResult
import dev.daymark.models.attendance.DayKey
import dev.daymark.models.attendance.FixVerdict
import dev.daymark.models.attendance.StoredDay
import dev.daymark.repository.AttendanceChecks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Clock
import kotlin.uuid.Uuid

class AttendanceService(
    private val repository: AttendanceRepository,
    private val location: LocationGateway,
    private val clock: Clock,
    private val onCheckCompleted: suspend (DayKey, CheckMode, CheckOutcome) -> Unit = { _, _, _ -> }
) : AttendanceChecks {
    private val manual = Mutex()
    override suspend fun manualCheck(): CheckOutcome = checkManual(null)

    suspend fun widgetCheck(expectedTrackerId: String): CheckOutcome =
        checkManual(expectedTrackerId)

    private suspend fun checkManual(expectedTrackerId: String?): CheckOutcome {
        if (!manual.tryLock()) return CheckOutcome.Busy(null)
        return try {
            val day = repository.loadCurrentDay(clock.now()) ?: return CheckOutcome.Rejected(
                currentDay = null,
                reason = CheckRejection.NO_TRACKER
            )
            if (expectedTrackerId != null && day.plan.key.trackerId != expectedTrackerId)
                return CheckOutcome.StateChanged(null)
            val now = clock.now()
            val current = day.outcome(now)
            when {
                now.toLocalDateTime(TimeZone.of(day.plan.zoneId)).date != day.plan.key.date -> CheckOutcome.StateChanged(
                    current
                )

                !day.plan.isScheduled || day.off != null -> CheckOutcome.Rejected(
                    current,
                    CheckRejection.DAY_OFF
                )

                day.evidence?.source == AttendanceSource.MANUAL_CORRECTION || day.evidence?.status in listOf(
                    AttendanceStatus.PRESENT,
                    AttendanceStatus.LATE
                ) -> CheckOutcome.AlreadyRecorded(current)

                now < day.plan.arrival -> CheckOutcome.Rejected(
                    current,
                    CheckRejection.BEFORE_WINDOW
                )

                day.plan.departure?.let { now > it } == true -> {
                    AppLogger.i("AttendanceService", "Manual check rejected: AFTER_DEPARTURE")
                    CheckOutcome.Rejected(current, CheckRejection.AFTER_DEPARTURE)
                }

                else -> acquire(day, CheckMode.USER_INITIATED, null)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            CheckOutcome.Rejected(null, CheckRejection.LOCAL_SAVE_FAILED)
        } finally {
            manual.unlock()
        }
    }

    suspend fun runAutomatic(key: DayKey, ordinal: Int = 1): CheckOutcome {
        val attemptId = Uuid.random().toString()
        return try {
            repository.reconcile(now = clock.now())
            when (val claim = repository.claimAutomatic(
                key = key, attemptId = attemptId, now = clock.now(), ordinal = ordinal
            )) {
                is ClaimResult.Claimed -> runClaimedAutomatic(claim = claim, ordinal = ordinal)
                ClaimResult.AlreadyConsumed -> CheckOutcome.Busy(repository.loadDay(key)?.outcome(clock.now()))
                ClaimResult.Ineligible -> CheckOutcome.StateChanged(repository.loadDay(key)?.outcome(clock.now()))
            }
        } catch (cancelled: CancellationException) {
            abandonAutomatic(key = key, attemptId = attemptId)
            throw cancelled
        } catch (failure: Exception) {
            AppLogger.e(tag = "AttendanceService", message = "Automatic check failed", throwable = failure)
            CheckOutcome.Rejected(currentDay = null, reason = CheckRejection.LOCAL_SAVE_FAILED)
        }
    }

    private suspend fun abandonAutomatic(key: DayKey, attemptId: String) = withContext(NonCancellable) {
        repository.abandonAutomatic(key = key, attemptId = attemptId)
    }

    private suspend fun runClaimedAutomatic(claim: ClaimResult.Claimed, ordinal: Int): CheckOutcome {
        val day = claim.day
        val now = clock.now()
        val invalid = automaticWindowRejection(day = day, ordinal = ordinal, now = now)
        if (invalid == null) {
            return acquire(day = day, mode = CheckMode.AUTOMATIC, attemptId = claim.attemptId, budgetStart = now)
        }
        return repository.completeCheck(
            request = commit(day = day, verdict = FixVerdict.Rejected(reason = invalid), mode = CheckMode.AUTOMATIC, attemptId = claim.attemptId)
        )
    }

    private fun automaticWindowRejection(day: StoredDay, ordinal: Int, now: kotlin.time.Instant): CheckRejection? =
        when {
            now.toLocalDateTime(TimeZone.of(day.plan.zoneId)).date != day.plan.key.date -> CheckRejection.STATE_CHANGED
            now < day.plan.arrival -> CheckRejection.BEFORE_WINDOW
            now > day.plan.deadline || (ordinal > 1 && day.plan.deadline - now < automaticSessionLimit) ->
                CheckRejection.AFTER_AUTOMATIC_WINDOW
            else -> null
        }

    private suspend fun acquire(
        day: StoredDay,
        mode: CheckMode,
        attemptId: String?,
        budgetStart: kotlin.time.Instant = clock.now()
    ): CheckOutcome {
        val budget = if (mode == CheckMode.AUTOMATIC)
            (day.plan.deadline - budgetStart).coerceAtMost(automaticSessionLimit) else automaticSessionLimit
        if (budget <= Duration.ZERO) return repository.completeCheck(
            commit(day, FixVerdict.Rejected(CheckRejection.AFTER_AUTOMATIC_WINDOW), mode, attemptId)
        )
        val result = try {
            location.requestFix(budget)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            LocationResult.Failure(LocationFailure.UNAVAILABLE)
        }
        val verdict = when (result) {
            is LocationResult.Fix -> classifyFix(day.plan, result, mode, clock.now())
            is LocationResult.Failure -> FixVerdict.Rejected(
                when (result.reason) {
                    LocationFailure.PERMISSION_DENIED -> CheckRejection.LOCATION_DENIED
                    LocationFailure.SERVICES_DISABLED -> CheckRejection.LOCATION_DISABLED
                    LocationFailure.TIMEOUT -> CheckRejection.LOCATION_TIMEOUT
                    LocationFailure.CANCELLED -> CheckRejection.CANCELLED
                    LocationFailure.UNAVAILABLE -> CheckRejection.UNAVAILABLE
                }
            )
        }
        val outcome = try {
            repository.completeCheck(commit(day, verdict, mode, attemptId))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            AppLogger.e("AttendanceService", "Check persistence failed", failure)
            if (mode == CheckMode.AUTOMATIC) recordAutomaticSaveFailure(day = day, attemptId = attemptId)
            else CheckOutcome.Rejected(currentDay = null, reason = CheckRejection.LOCAL_SAVE_FAILED)
        }
        try {
            onCheckCompleted(day.plan.key, mode, outcome)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            AppLogger.e("AttendanceService", "Check completion notification failed", failure)
        }
        return outcome
    }

    private suspend fun recordAutomaticSaveFailure(day: StoredDay, attemptId: String?): CheckOutcome =
        repository.completeCheck(
            request = commit(
                day = day, verdict = FixVerdict.Rejected(reason = CheckRejection.LOCAL_SAVE_FAILED),
                mode = CheckMode.AUTOMATIC, attemptId = attemptId
            )
        )

    private fun commit(day: StoredDay, verdict: FixVerdict, mode: CheckMode, attemptId: String?) =
        CheckCommit(
            key = day.plan.key,
            attemptId = attemptId,
            expectedScheduleVersion = day.plan.scheduleVersion,
            expectedPlaceVersion = day.plan.place.version,
            verdict = verdict,
            mode = mode,
            updatedAt = clock.now(),
            expectedUserEditRevision = day.userEditRevision
        )
}
