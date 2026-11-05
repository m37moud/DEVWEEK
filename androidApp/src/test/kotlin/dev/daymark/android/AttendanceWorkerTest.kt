package dev.daymark.android

import dev.daymark.attendance.*
import dev.daymark.attendance.AttendanceRepository
import dev.daymark.attendance.AttendanceService
import dev.daymark.attendance.evaluateDay
import dev.daymark.location.*
import dev.daymark.location.LocationFailure
import dev.daymark.location.LocationGateway
import dev.daymark.location.LocationResult
import dev.daymark.models.attendance.AttendanceSource
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.models.attendance.AutomaticState
import dev.daymark.models.attendance.CheckCommit
import dev.daymark.models.attendance.CheckOutcome
import dev.daymark.models.attendance.ClaimResult
import dev.daymark.models.attendance.Correction
import dev.daymark.models.attendance.DayKey
import dev.daymark.models.attendance.DayOutcome
import dev.daymark.models.attendance.DayPlan
import dev.daymark.models.attendance.FixVerdict
import dev.daymark.models.attendance.HistoryMonth
import dev.daymark.models.attendance.RegisteredPlace
import dev.daymark.models.attendance.StoredDay
import dev.daymark.models.attendance.TrackerCategory
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.*
import org.junit.Test

class AttendanceWorkerTest {
    private val key = DayKey("f8428c3a-1347-40dc-9667-af38e69c6801", LocalDate(2026, 10, 27))
    private val arrival = Instant.parse("2026-10-27T09:00:00Z")
    private class MutableClock(var value: Instant) : Clock { override fun now() = value }
    private inner class Repository(private val clock: Clock) : AttendanceRepository {
        var consumed = false
        val plan = DayPlan(key, TrackerCategory.WORK, arrival, arrival + Duration.parse("5m"), "UTC", 1, RegisteredPlace(0.0, 0.0, 100, 1), true)
        private fun day() = StoredDay(plan, null, null, if (consumed) AutomaticState.FINISHED else AutomaticState.UNCLAIMED, 1)
        override suspend fun reconcile(now: Instant) = Unit
        override suspend fun loadCurrentDay(now: Instant) = day()
        override suspend fun loadDay(key: DayKey) = day()
        override fun observeDay(key: DayKey) = flowOf(evaluateDay(plan, null, null, clock.now()))
        override fun observeMonth(year: Int, month: Int): Flow<HistoryMonth> = error("Unused")
        override suspend fun correct(command: Correction): DayOutcome = error("Unused")
        override suspend fun claimAutomatic(key: DayKey, attemptId: String, now: Instant): ClaimResult {
            if (consumed) return ClaimResult.AlreadyConsumed
            if (now !in plan.arrival..plan.deadline) return ClaimResult.Ineligible
            consumed = true
            return ClaimResult.Claimed(day(), attemptId)
        }
        override suspend fun completeCheck(request: CheckCommit): CheckOutcome = when(val verdict = request.verdict) {
            is FixVerdict.Accepted -> CheckOutcome.Recorded(DayOutcome(verdict.status, AttendanceSource.AUTOMATIC_GPS, verdict.confirmedAt, null))
            is FixVerdict.Rejected -> CheckOutcome.Rejected(evaluateDay(plan, null, null, clock.now()), verdict.reason)
        }
    }
    @Test fun late_dispatch_never_requests_location_and_recovers_next_work() = runTest {
        val clock = MutableClock(Instant.parse("2026-10-27T09:06:00Z")); val repo = Repository(clock)
        var calls = 0; var recoveries = 0
        val gateway = object : LocationGateway { override suspend fun requestFix(budget: Duration): LocationResult { calls++; error("Late GPS") } }
        val job = AndroidAttendanceJob(AttendanceService(repo, gateway, clock), { true }, {}, { recoveries++ })
        assertFalse(job.run(key))
        assertEquals(0, calls)
        assertEquals(AttendanceStatus.ABSENT, repo.observeDay(key).first().status)
        assertEquals(1, recoveries)
    }
    @Test fun timeout_and_recovery_never_sample_a_consumed_date_again() = runTest {
        val clock = MutableClock(arrival); val repo = Repository(clock)
        var calls = 0; var recoveries = 0
        val gateway = object : LocationGateway { override suspend fun requestFix(budget: Duration): LocationResult { calls++; return LocationResult.Failure(LocationFailure.TIMEOUT) } }
        val job = AndroidAttendanceJob(AttendanceService(repo, gateway, clock), { true }, {}, { recoveries++ })
        assertFalse(job.run(key)); assertFalse(job.run(key))
        assertEquals(1, calls); assertEquals(2, recoveries)
    }
    @Test fun permission_revocation_disables_automation_without_requesting_a_fix() = runTest {
        val clock = MutableClock(arrival); val repo = Repository(clock)
        var calls = 0; var disabled = 0; var recoveries = 0
        val gateway = object : LocationGateway { override suspend fun requestFix(budget: Duration): LocationResult { calls++; error("Denied GPS") } }
        val job = AndroidAttendanceJob(AttendanceService(repo, gateway, clock), { false }, { disabled++ }, { recoveries++ })
        assertFalse(job.run(key))
        assertEquals(0, calls); assertEquals(1, disabled); assertEquals(1, recoveries)
        assertNotNull(repo.loadDay(key)); assertFalse(repo.consumed)
    }
    @Test fun native_denial_race_disables_automation_after_one_sample_and_schedules_recovery() = runTest {
        val clock = MutableClock(arrival); val repo = Repository(clock)
        var calls = 0; var disabled = 0; var recoveries = 0
        val gateway = object : LocationGateway { override suspend fun requestFix(budget: Duration): LocationResult { calls++; return LocationResult.Failure(LocationFailure.PERMISSION_DENIED) } }
        val job = AndroidAttendanceJob(AttendanceService(repo, gateway, clock), { true }, { disabled++ }, { recoveries++ })
        assertFalse(job.run(key))
        assertEquals(1, calls); assertEquals(1, disabled); assertEquals(1, recoveries)
    }
    @Test fun a_denied_stale_worker_identifies_its_original_tracker_when_disabling() = runTest {
        val clock = MutableClock(arrival); val repo = Repository(clock)
        val gateway = object : LocationGateway { override suspend fun requestFix(budget: Duration): LocationResult = error("Denied GPS") }
        var disabledTracker: String? = null
        val job = AndroidAttendanceJob(AttendanceService(repo, gateway, clock), { false }, { trackerId -> disabledTracker = trackerId }, {})
        job.run(key)
        assertEquals(key.trackerId, disabledTracker)
    }

}
