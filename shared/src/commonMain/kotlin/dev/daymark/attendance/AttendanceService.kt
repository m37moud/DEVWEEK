package dev.daymark.attendance

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
import kotlinx.coroutines.sync.Mutex
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class AttendanceService(
    private val repository: AttendanceRepository,
    private val location: LocationGateway,
    private val clock: Clock
) : AttendanceChecks {
    private val manual = Mutex()
    override suspend fun manualCheck(): CheckOutcome {
        if (!manual.tryLock()) return CheckOutcome.Busy(null)
        return try {
            val day = repository.loadCurrentDay(clock.now()) ?: return CheckOutcome.Rejected(
                null,
                CheckRejection.NO_TRACKER
            )
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

    suspend fun runAutomatic(key: DayKey): CheckOutcome = try {
        repository.reconcile(clock.now())
        when (val claim = repository.claimAutomatic(key, Uuid.random().toString(), clock.now())) {
            is ClaimResult.Claimed -> {
                val day = claim.day
                val now = clock.now()
                val invalid = when {
                    now.toLocalDateTime(TimeZone.of(day.plan.zoneId)).date != key.date -> CheckRejection.STATE_CHANGED
                    now < day.plan.arrival -> CheckRejection.BEFORE_WINDOW
                    now > day.plan.deadline -> CheckRejection.AFTER_AUTOMATIC_WINDOW
                    else -> null
                }
                if (invalid == null) acquire(day, CheckMode.AUTOMATIC, claim.attemptId)
                else repository.completeCheck(
                    commit(
                        day,
                        FixVerdict.Rejected(invalid),
                        CheckMode.AUTOMATIC,
                        claim.attemptId
                    )
                )
            }

            ClaimResult.AlreadyConsumed -> CheckOutcome.Busy(
                repository.loadDay(key)?.outcome(clock.now())
            )

            ClaimResult.Ineligible -> CheckOutcome.Rejected(
                repository.loadDay(key)?.outcome(clock.now()), CheckRejection.STATE_CHANGED
            )
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        CheckOutcome.Rejected(null, CheckRejection.LOCAL_SAVE_FAILED)
    }

    private suspend fun acquire(day: StoredDay, mode: CheckMode, attemptId: String?): CheckOutcome {
        val result = try {
            location.requestFix(30.seconds)
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
        return repository.completeCheck(commit(day, verdict, mode, attemptId))
    }

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
