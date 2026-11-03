@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.daymark.attendance

import dev.daymark.at
import dev.daymark.fixtureDate
import dev.daymark.fixtureFix
import dev.daymark.fixturePlan
import dev.daymark.location.LocationCapabilities
import dev.daymark.location.LocationGateway
import dev.daymark.location.LocationPermissionPort
import dev.daymark.location.LocationResult
import dev.daymark.plan.PlanReader
import dev.daymark.plan.PlannedDay
import dev.daymark.plan.ScheduleContext
import dev.daymark.plan.TimeOffKind
import dev.daymark.ui.TodayViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

class TodayViewModelTest {
    private class Repository(private val clock: Clock, private val baselineOnly: Boolean = false) :
        AttendanceRepository {
        val evidence = mutableMapOf<LocalDate, AttendanceEvidence>()
        private val changes = MutableStateFlow(0)
        var reconciliations = 0
        var off: TimeOffKind? = null
            set(value) {
                field = value; changes.value++
            }

        private fun day(date: LocalDate) =
            StoredDay(fixturePlan(date = date), evidence[date], off, AutomaticState.UNCLAIMED, 1)

        override suspend fun loadCurrentDay(now: Instant) =
            day(now.toLocalDateTime(TimeZone.UTC).date)

        override suspend fun loadDay(key: DayKey) =
            if (baselineOnly && evidence[key.date] == null) null else day(key.date)

        override suspend fun reconcile(now: Instant) {
            reconciliations++
        }

        override fun observeDay(key: DayKey): Flow<DayOutcome?> = changes.flatMapLatest {
            flow {
                while (true) {
                    val stored = day(key.date)
                    val outcome = evaluateDay(stored.plan, stored.evidence, stored.off, clock.now())
                    emit(outcome)
                    val next = when (outcome.status) {
                        AttendanceStatus.SCHEDULED -> stored.plan.arrival
                        AttendanceStatus.AWAITING_CHECK -> stored.plan.deadline + 1.nanoseconds
                        else -> break
                    }
                    delay((next - clock.now()).coerceAtLeast(1.milliseconds))
                    reconcile(clock.now())
                }
            }
        }

        override fun observeMonth(year: Int, month: Int): Flow<HistoryMonth> = error("Not used")
        override suspend fun claimAutomatic(
            key: DayKey,
            attemptId: String,
            now: Instant
        ): ClaimResult = error("Manual controller never claims automation")

        override suspend fun correct(command: Correction): DayOutcome = error("Not used")
        override suspend fun completeCheck(request: CheckCommit): CheckOutcome =
            when (val verdict = request.verdict) {
                is FixVerdict.Rejected -> CheckOutcome.Rejected(
                    evaluateDay(
                        day(request.key.date).plan,
                        null,
                        null,
                        clock.now()
                    ), verdict.reason
                )

                is FixVerdict.Accepted -> {
                    evidence[request.key.date] = AttendanceEvidence(
                        verdict.status,
                        AttendanceSource.MANUAL_GPS,
                        verdict.confirmedAt,
                        null,
                        request.updatedAt
                    )
                    changes.value++
                    CheckOutcome.Recorded(
                        evaluateDay(
                            day(request.key.date).plan,
                            evidence[request.key.date],
                            null,
                            clock.now()
                        )
                    )
                }
            }
    }

    private val permissions = object : LocationPermissionPort {
        override fun current() = LocationCapabilities(true, false, true, true)
        override suspend fun requestForeground() = current()
        override suspend fun requestBackground(): LocationCapabilities =
            error("Manual checks do not request background access")

        override fun openSystemSettings() = Unit
    }
    private val plans = object : PlanReader {
        override suspend fun context() =
            ScheduleContext(fixturePlan().key.trackerId, "UTC", at("08:00:00"), true)

        override suspend fun day(date: LocalDate) =
            PlannedDay(fixturePlan(date = date), null, false, false)
    }

    @Test
    fun duplicate_taps_share_one_check() = runTest {
        val clock = TestClock();
        val repo = Repository(clock)
        var calls = 0;
        val reply = CompletableDeferred<LocationResult>()
        val gateway = object : LocationGateway {
            override suspend fun requestFix(budget: Duration): LocationResult {
                calls++; return reply.await()
            }
        }
        val model = TodayViewModel(
            repo,
            AttendanceService(repo, gateway, clock),
            plans,
            permissions,
            clock,
            backgroundScope
        )
        model.uiState.first { it.canCheck }
        model.checkAttendance(); model.checkAttendance()
        model.requestQuickCheck(fixturePlan().key.trackerId); model.requestQuickCheck(fixturePlan().key.trackerId)
        runCurrent()
        assertTrue(model.uiState.value.busy)
        assertEquals(1, calls)
        reply.complete(fixtureFix())
        val after =
            model.uiState.first { !it.busy && it.outcome?.status == AttendanceStatus.PRESENT }
        assertFalse(after.canCheck)
    }

    @Test
    fun deadline_reconciles_without_polling_gps_and_stops_while_inactive() = runTest {
        val clock = TestClock(at("08:59:00"));
        val repo = Repository(clock)
        var calls = 0
        val gateway = object : LocationGateway {
            override suspend fun requestFix(budget: Duration): LocationResult {
                calls++; return fixtureFix()
            }
        }
        val model = TodayViewModel(
            repo,
            AttendanceService(repo, gateway, clock),
            plans,
            permissions,
            clock,
            backgroundScope
        )
        model.uiState.first { it.outcome?.status == AttendanceStatus.SCHEDULED }
        assertFalse(model.uiState.value.canCheck)
        clock.value = at("09:02:00"); advanceTimeBy(180_000); runCurrent()
        model.uiState.first { it.outcome?.status == AttendanceStatus.AWAITING_CHECK }
        val beforeDeadline = repo.reconciliations
        clock.value = at("09:06:00"); advanceTimeBy(240_001); runCurrent()
        model.uiState.first { it.outcome?.status == AttendanceStatus.ABSENT }
        assertEquals(beforeDeadline + 1, repo.reconciliations)
        assertEquals(0, calls)
        model.setActive(false); runCurrent()
        val inactive = repo.reconciliations
        clock.value = Instant.parse("2026-10-28T09:06:00Z"); advanceTimeBy(86_400_000); runCurrent()
        assertEquals(inactive, repo.reconciliations)
        model.setActive(true)
        assertEquals(
            LocalDate(2026, 10, 28),
            model.uiState.first { it.date == LocalDate(2026, 10, 28) }.date
        )
        assertEquals(0, calls)
    }

    @Test
    fun late_and_rejected_fixes_are_reflected_without_a_false_success() = runTest {
        val clock = TestClock(at("09:18:00"));
        val repo = Repository(clock)
        var outside = true
        val gateway = object : LocationGateway {
            override suspend fun requestFix(budget: Duration) =
                fixtureFix(capturedAt = clock.now(), latitude = if (outside) 1.0 else 0.0)
        }
        val model = TodayViewModel(
            repo,
            AttendanceService(repo, gateway, clock),
            plans,
            permissions,
            clock,
            backgroundScope
        )
        model.uiState.first { it.canCheck }
        model.checkAttendance()
        val rejected = model.uiState.first { !it.busy && it.error == CheckRejection.OUTSIDE_AREA }
        assertEquals(AttendanceStatus.ABSENT, rejected.outcome?.status)
        assertTrue(repo.evidence.isEmpty())
        outside = false; model.checkAttendance()
        assertEquals(
            AttendanceSource.MANUAL_GPS,
            model.uiState.first { it.outcome?.status == AttendanceStatus.LATE }.outcome?.source
        )
    }

    @Test
    fun untracked_projection_is_not_shown_as_an_absence_but_allows_manual_confirmation() = runTest {
        val clock = TestClock(at("09:18:00"));
        val repo = Repository(clock, baselineOnly = true)
        val futureStart = object : PlanReader {
            override suspend fun context() = ScheduleContext(
                fixturePlan().key.trackerId,
                "UTC",
                Instant.parse("2026-10-28T09:00:00Z"),
                true
            )

            override suspend fun day(date: LocalDate) = plans.day(date)
        }
        val location = object : LocationGateway {
            override suspend fun requestFix(budget: Duration) = fixtureFix(capturedAt = clock.now())
        }
        val model = TodayViewModel(
            repo,
            AttendanceService(repo, location, clock),
            futureStart,
            permissions,
            clock,
            backgroundScope
        )
        val initial = model.uiState.first { it.ready }
        assertNull(initial.outcome)
        assertTrue(initial.canCheck)
        model.checkAttendance()
        assertEquals(
            AttendanceStatus.LATE,
            model.uiState.first { !it.busy && it.outcome != null }.outcome?.status
        )
    }

    @Test
    fun leaving_the_screen_cancels_in_flight_location_without_recording_success() = runTest {
        val clock = TestClock();
        val repo = Repository(clock)
        var cancelled = false
        val location = object : LocationGateway {
            override suspend fun requestFix(budget: Duration): LocationResult {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
        }
        val model = TodayViewModel(
            repo,
            AttendanceService(repo, location, clock),
            plans,
            permissions,
            clock,
            backgroundScope
        )
        model.uiState.first { it.canCheck }
        model.checkAttendance(); runCurrent()
        model.setActive(false); runCurrent()
        assertTrue(cancelled)
        assertFalse(model.uiState.value.busy)
        assertFalse(model.uiState.value.canCheck)
        assertTrue(repo.evidence.isEmpty())
    }

    @Test
    fun active_midnight_refresh_changes_only_the_current_attendance_date_without_gps() = runTest {
        val clock = TestClock(at("23:59:00"));
        val repo = Repository(clock)
        var calls = 0
        val location = object : LocationGateway {
            override suspend fun requestFix(budget: Duration): LocationResult {
                calls++; return fixtureFix()
            }
        }
        val model = TodayViewModel(
            repo,
            AttendanceService(repo, location, clock),
            plans,
            permissions,
            clock,
            backgroundScope
        )
        model.uiState.first { it.date == fixtureDate }
        clock.value = Instant.parse("2026-10-28T00:00:00Z")
        advanceTimeBy(60_001); runCurrent()
        val after = model.uiState.first { it.date == LocalDate(2026, 10, 28) }
        assertEquals(AttendanceStatus.SCHEDULED, after.outcome?.status)
        assertFalse(after.canCheck)
        assertEquals(0, calls)
        assertTrue(repo.evidence.isEmpty())
    }

    @Test
    fun removing_time_off_restores_the_current_day_check_without_reopening_today() = runTest {
        val clock = TestClock(at("09:18:00"));
        val repo = Repository(clock)
        repo.off = TimeOffKind.VACATION
        val location = object : LocationGateway {
            override suspend fun requestFix(budget: Duration) = fixtureFix(capturedAt = clock.now())
        }
        val model = TodayViewModel(
            repo,
            AttendanceService(repo, location, clock),
            plans,
            permissions,
            clock,
            backgroundScope
        )
        assertFalse(model.uiState.first { it.outcome?.status == AttendanceStatus.VACATION }.canCheck)
        repo.off = null
        assertTrue(model.uiState.first { it.outcome?.status == AttendanceStatus.ABSENT }.canCheck)
    }

    @Test
    fun widget_routes_resolve_current_day_and_do_not_trust_a_deleted_tracker() = runTest {
        val clock = TestClock(Instant.parse("2026-10-28T09:18:00Z"));
        val repo = Repository(clock)
        var calls = 0
        val location = object : LocationGateway {
            override suspend fun requestFix(budget: Duration): LocationResult {
                calls++; return fixtureFix(capturedAt = clock.now())
            }
        }
        val model = TodayViewModel(
            repo,
            AttendanceService(repo, location, clock),
            plans,
            permissions,
            clock,
            backgroundScope
        )
        model.uiState.first { it.canCheck }
        model.requestQuickCheck("6afba670-5b46-48bc-abff-d57bc453ef29")
        runCurrent(); assertEquals(0, calls)
        model.requestQuickCheck(fixturePlan().key.trackerId)
        model.uiState.first { !it.busy && it.outcome?.status == AttendanceStatus.LATE }
        assertEquals(setOf(LocalDate(2026, 10, 28)), repo.evidence.keys)
        assertEquals(1, calls)
    }

    @Test
    fun a_widget_tap_before_arrival_is_consumed_without_a_future_queued_sensor_request() = runTest {
        val clock = TestClock(at("08:59:00"));
        val repo = Repository(clock)
        var calls = 0
        val location = object : LocationGateway {
            override suspend fun requestFix(budget: Duration): LocationResult {
                calls++; return fixtureFix(capturedAt = clock.now())
            }
        }
        val model = TodayViewModel(
            repo,
            AttendanceService(repo, location, clock),
            plans,
            permissions,
            clock,
            backgroundScope
        )
        model.uiState.first { it.ready }
        model.requestQuickCheck(fixturePlan().key.trackerId)
        assertEquals(CheckRejection.BEFORE_WINDOW, model.uiState.value.error)
        clock.value = at("09:00:00"); advanceTimeBy(60_001); runCurrent()
        assertTrue(model.uiState.first { it.canCheck }.canCheck)
        assertEquals(0, calls)
        assertTrue(repo.evidence.isEmpty())
    }

    @Test
    fun inactive_host_does_not_load_or_check_until_started() = runTest {
        val clock = TestClock();
        val repo = Repository(clock)
        var calls = 0
        val location = object : LocationGateway {
            override suspend fun requestFix(budget: Duration): LocationResult {
                calls++; return fixtureFix()
            }
        }
        val model = TodayViewModel(
            repo,
            AttendanceService(repo, location, clock),
            plans,
            permissions,
            clock,
            backgroundScope,
            initiallyActive = false
        )
        model.requestQuickCheck(fixturePlan().key.trackerId)
        runCurrent(); assertEquals(0, repo.reconciliations); assertEquals(0, calls)
        model.setActive(true)
        model.uiState.first { it.ready && it.active }
        model.requestQuickCheck(fixturePlan().key.trackerId)
        model.uiState.first { !it.busy && it.outcome?.status == AttendanceStatus.PRESENT }
        assertEquals(1, calls)
    }

    @Test
    fun an_explicit_off_day_before_the_first_arrival_keeps_its_reason_without_gps() = runTest {
        val clock = TestClock(at("09:18:00"));
        val repo = Repository(clock, baselineOnly = true).apply { off = TimeOffKind.HOLIDAY }
        val futureStart = object : PlanReader {
            override suspend fun context() = ScheduleContext(
                fixturePlan().key.trackerId,
                "UTC",
                Instant.parse("2026-10-28T09:00:00Z"),
                false
            )

            override suspend fun day(date: LocalDate) = plans.day(date)
        }
        val location = object : LocationGateway {
            override suspend fun requestFix(budget: Duration): LocationResult =
                error("An off day must not obtain GPS")
        }
        val model = TodayViewModel(
            repo,
            AttendanceService(repo, location, clock),
            futureStart,
            permissions,
            clock,
            backgroundScope
        )
        val state = model.uiState.first { it.ready }
        assertEquals(AttendanceStatus.HOLIDAY, state.outcome?.status)
        assertFalse(state.canCheck)
    }

}
