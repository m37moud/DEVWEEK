package dev.daymark.attendance

import dev.daymark.at
import dev.daymark.fixtureFix
import dev.daymark.fixturePlan
import dev.daymark.location.LocationGateway
import dev.daymark.location.LocationResult
import dev.daymark.models.attendance.AttendanceEvidence
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
import dev.daymark.models.attendance.FixVerdict
import dev.daymark.models.attendance.HistoryMonth
import dev.daymark.models.attendance.StoredDay
import dev.daymark.plan.TimeOffKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class TestClock(var value: Instant = at("09:02:00")) : Clock {
    override fun now() = value
}

class AttendanceServiceTest {
    private class Repository(private val clock: Clock) : AttendanceRepository {
        var day: StoredDay? = StoredDay(fixturePlan(), null, null, AutomaticState.UNCLAIMED, 1)
        var lastCommit: CheckCommit? = null
        var onClaim: () -> Unit = { }
        var failSave = false
        private fun outcome() = day?.let { evaluateDay(it.plan, it.evidence, it.off, clock.now()) }
        override fun observeDay(key: DayKey): Flow<DayOutcome?> = flowOf(outcome())
        override fun observeMonth(year: Int, month: Int): Flow<HistoryMonth> =
            flowOf(HistoryMonth(emptyList(), 0, 0, 0))

        override suspend fun loadCurrentDay(now: Instant) = day
        override suspend fun loadDay(key: DayKey) = day?.takeIf { it.plan.key == key }
        override suspend fun reconcile(now: Instant) {}
        override suspend fun correct(command: Correction): DayOutcome =
            error("Not used by this orchestration fake")

        override suspend fun claimAutomatic(
            key: DayKey,
            attemptId: String,
            now: Instant
        ): ClaimResult {
            val current = day ?: return ClaimResult.Ineligible
            if (current.plan.key != key || !current.plan.isScheduled || current.off != null || now < current.plan.arrival || now > current.plan.deadline)
                return ClaimResult.Ineligible
            if (current.automaticState != AutomaticState.UNCLAIMED) return ClaimResult.AlreadyConsumed
            day = current.copy(automaticState = AutomaticState.CLAIMED)
            onClaim()
            return ClaimResult.Claimed(requireNotNull(day), attemptId)
        }

        override suspend fun completeCheck(request: CheckCommit): CheckOutcome {
            if (failSave) error("test-only save failure")
            lastCommit = request
            val current = requireNotNull(day)
            if (current.userEditRevision != request.expectedUserEditRevision || current.off != null ||
                current.evidence?.source == AttendanceSource.MANUAL_CORRECTION
            ) return CheckOutcome.StateChanged(outcome())
            return when (val verdict = request.verdict) {
                is FixVerdict.Rejected -> CheckOutcome.Rejected(outcome(), verdict.reason)
                is FixVerdict.Accepted -> {
                    val source =
                        if (request.mode == CheckMode.AUTOMATIC) AttendanceSource.AUTOMATIC_GPS else AttendanceSource.MANUAL_GPS
                    day = current.copy(
                        evidence = AttendanceEvidence(
                            verdict.status,
                            source,
                            verdict.confirmedAt,
                            null,
                            request.updatedAt
                        )
                    )
                    CheckOutcome.Recorded(requireNotNull(outcome()))
                }
            }
        }
    }

    private class Gateway : LocationGateway {
        var calls = 0
        var answer: suspend () -> LocationResult = { fixtureFix() }
        override suspend fun requestFix(budget: Duration): LocationResult {
            calls++; return answer()
        }
    }

    @Test
    fun correction_wins_during_location_acquisition() = runTest {
        val clock = TestClock();
        val repo = Repository(clock);
        val gateway = Gateway()
        val started = CompletableDeferred<Unit>();
        val response = CompletableDeferred<LocationResult>()
        gateway.answer = { started.complete(Unit); response.await() }
        val pending = async { AttendanceService(repo, gateway, clock).manualCheck() }
        started.await()
        repo.day = repo.day!!.copy(
            userEditRevision = 1, evidence = AttendanceEvidence(
                AttendanceStatus.ABSENT,
                AttendanceSource.MANUAL_CORRECTION,
                null,
                "manual",
                clock.now()
            )
        )
        response.complete(fixtureFix())
        val result = assertIs<CheckOutcome.StateChanged>(pending.await())
        assertEquals(AttendanceSource.MANUAL_CORRECTION, result.currentDay?.source)
        assertEquals(AttendanceStatus.ABSENT, result.currentDay?.status)
    }

    @Test
    fun holiday_added_then_removed_during_acquisition_still_invalidates_old_request() = runTest {
        val clock = TestClock();
        val repo = Repository(clock);
        val gateway = Gateway()
        val started = CompletableDeferred<Unit>();
        val response = CompletableDeferred<LocationResult>()
        gateway.answer = { started.complete(Unit); response.await() }
        val pending = async { AttendanceService(repo, gateway, clock).manualCheck() }
        started.await()
        repo.day = repo.day!!.copy(off = TimeOffKind.HOLIDAY, userEditRevision = 1)
        repo.day = repo.day!!.copy(off = null, userEditRevision = 2)
        response.complete(fixtureFix())
        assertIs<CheckOutcome.StateChanged>(pending.await())
        assertNull(repo.day!!.evidence)
        assertEquals(0L, repo.lastCommit?.expectedUserEditRevision)
    }

    @Test
    fun stale_claimed_and_off_automatic_days_make_no_location_request() = runTest {
        val clock = TestClock();
        val repo = Repository(clock);
        val gateway = Gateway()
        val service = AttendanceService(repo, gateway, clock)
        service.runAutomatic(
            repo.day!!.plan.key.copy(
                date = kotlinx.datetime.LocalDate(
                    2026,
                    10,
                    26
                )
            )
        )
        repo.day = repo.day!!.copy(automaticState = AutomaticState.CLAIMED)
        service.runAutomatic(repo.day!!.plan.key)
        repo.day =
            repo.day!!.copy(automaticState = AutomaticState.UNCLAIMED, off = TimeOffKind.HOLIDAY)
        service.runAutomatic(repo.day!!.plan.key)
        assertEquals(0, gateway.calls)
    }

    @Test
    fun deadline_is_rechecked_after_claim_before_location_dispatch() = runTest {
        val clock = TestClock();
        val repo = Repository(clock);
        val gateway = Gateway()
        repo.onClaim = { clock.value = at("09:06:00") }
        val result = AttendanceService(repo, gateway, clock).runAutomatic(repo.day!!.plan.key)
        assertEquals(0, gateway.calls)
        assertIs<CheckOutcome.Rejected>(result)
        assertEquals(CheckRejection.AFTER_AUTOMATIC_WINDOW, result.reason)
        assertNotEquals(AutomaticState.UNCLAIMED, repo.day!!.automaticState)
    }

    @Test
    fun duplicate_manual_taps_share_no_second_acquisition() = runTest {
        val clock = TestClock();
        val repo = Repository(clock);
        val gateway = Gateway()
        val started = CompletableDeferred<Unit>();
        val response = CompletableDeferred<LocationResult>()
        gateway.answer = { started.complete(Unit); response.await() }
        val service = AttendanceService(repo, gateway, clock)
        val first = async { service.manualCheck() }; started.await()
        assertIs<CheckOutcome.Busy>(service.manualCheck())
        response.complete(fixtureFix()); assertIs<CheckOutcome.Recorded>(first.await())
        assertEquals(1, gateway.calls)
    }

    @Test
    fun automatic_cancellation_never_releases_claim_or_retries_gps() = runTest {
        val clock = TestClock();
        val repo = Repository(clock);
        val gateway = Gateway()
        val started = CompletableDeferred<Unit>()
        gateway.answer = { started.complete(Unit); awaitCancellation() }
        val service = AttendanceService(repo, gateway, clock)
        val first = async { service.runAutomatic(repo.day!!.plan.key) }; started.await()
        first.cancelAndJoin()
        assertNotEquals(AutomaticState.UNCLAIMED, repo.day!!.automaticState)
        service.runAutomatic(repo.day!!.plan.key)
        assertEquals(1, gateway.calls)
        assertNull(repo.day!!.evidence)
    }

    @Test
    fun local_save_failure_cannot_be_reported_as_recorded() = runTest {
        val clock = TestClock();
        val repo = Repository(clock);
        val gateway = Gateway()
        repo.failSave = true
        val result =
            assertIs<CheckOutcome.Rejected>(AttendanceService(repo, gateway, clock).manualCheck())
        assertEquals(CheckRejection.LOCAL_SAVE_FAILED, result.reason)
        assertNull(repo.day!!.evidence)
    }

    @Test
    fun manual_before_arrival_or_on_time_off_does_not_acquire_location() = runTest {
        val clock = TestClock(at("08:59:00"));
        val repo = Repository(clock);
        val gateway = Gateway()
        val service = AttendanceService(repo, gateway, clock)
        assertEquals(
            CheckRejection.BEFORE_WINDOW,
            assertIs<CheckOutcome.Rejected>(service.manualCheck()).reason
        )
        clock.value = at("09:02:00")
        repo.day = repo.day!!.copy(off = TimeOffKind.HOLIDAY)
        assertEquals(
            CheckRejection.DAY_OFF,
            assertIs<CheckOutcome.Rejected>(service.manualCheck()).reason
        )
        assertEquals(0, gateway.calls)
    }
}
