package dev.daymark.plan

import androidx.room3.*
import dev.daymark.*
import dev.daymark.attendance.*
import dev.daymark.data.*
import dev.daymark.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.*
import java.nio.file.Files
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Instant

class TimeOffTransactionTest {
    private val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    private fun date(value: String) = LocalDate.parse(value)
    private suspend fun withStore(initialNow: Instant = Instant.parse("2026-11-01T12:00:00Z"), block: suspend (DaymarkDatabase, TestClock, TimeOffService) -> Unit) {
        val folder = Files.createTempDirectory("daymark-time-off-")
        val db = openJvmDatabase(folder.resolve("attendance.db").toString())
        val clock = TestClock(initialNow)
        try {
            seedDatabase(db)
            val tracker = requireNotNull(db.dao().tracker())
            db.dao().putTracker(tracker.copy(openingBalance = 12))
            db.dao().putVersion(PlanVersionEntity.fromSnapshot(tracker.id, 1, fixtureDate, at("08:00:00"),
                TrackerCategory.WORK, fixtureSchedule(days = weekdays), fixturePlace(), "Work"))
            RoomAttendanceRepository(db, clock).reconcile(clock.now())
            block(db, clock, TimeOffService(db, clock))
        } finally { db.close(); folder.toFile().deleteRecursively() }
    }
    private suspend fun save(service: TimeOffService, command: TimeOffCommand) {
        val preview = service.preview(command)
        assertNull(preview.rejection)
        assertIs<PlanMutationResult.Applied>(service.apply(command, preview.expectedRevision))
    }
    @Test fun holiday_refund_and_repeat_save_are_idempotent() = runTest {
        withStore { _, _, service ->
            val vacation = TimeOffCommand.Add(TimeOffKind.VACATION, date("2026-11-02"), date("2026-11-03"))
            save(service, vacation)
            assertEquals(10, service.observeAvailableBalance().first())
            save(service, TimeOffCommand.Add(TimeOffKind.HOLIDAY, date("2026-11-03"), date("2026-11-03")))
            assertEquals(11, service.observeAvailableBalance().first())
            val holiday = service.observeEntries().first().single { it.kind == TimeOffKind.HOLIDAY }
            save(service, TimeOffCommand.Remove(holiday.id))
            assertEquals(10, service.observeAvailableBalance().first())
            save(service, vacation)
            assertEquals(10, service.observeAvailableBalance().first())
            assertEquals(1, service.observeEntries().first().size)
        }
    }
    @Test fun removing_retrospective_leave_restores_absence_and_underlying_correction() = runTest {
        withStore { db, clock, service ->
            val repo = RoomAttendanceRepository(db, clock)
            val past = DayKey(fixturePlan().key.trackerId, date("2026-10-28"))
            save(service, TimeOffCommand.Add(TimeOffKind.VACATION, past.date, past.date))
            assertEquals(AttendanceStatus.VACATION, repo.observeDay(past).first()?.status)
            save(service, TimeOffCommand.Remove(service.observeEntries().first().single().id))
            assertEquals(AttendanceStatus.ABSENT, repo.observeDay(past).first()?.status)
            repo.correct(Correction(past, AttendanceStatus.PRESENT, LocalTime(8, 45), "تصحيح"))
            save(service, TimeOffCommand.Add(TimeOffKind.HOLIDAY, past.date, past.date))
            assertEquals(AttendanceStatus.HOLIDAY, repo.observeDay(past).first()?.status)
            save(service, TimeOffCommand.Remove(service.observeEntries().first().single().id))
            assertEquals(AttendanceStatus.PRESENT, repo.observeDay(past).first()?.status)
            assertEquals(AttendanceSource.MANUAL_CORRECTION, repo.observeDay(past).first()?.source)
            assertEquals("تصحيح", repo.loadDay(past)?.evidence?.note)
        }
    }
    @Test fun overlapping_entries_and_replacement_apply_only_the_deduction_difference() = runTest {
        withStore { _, _, service ->
            save(service, TimeOffCommand.Add(TimeOffKind.VACATION, date("2026-11-02"), date("2026-11-04")))
            save(service, TimeOffCommand.Add(TimeOffKind.VACATION, date("2026-11-03"), date("2026-11-06")))
            assertEquals(7, service.observeAvailableBalance().first())
            val first = service.observeEntries().first().first { it.from == date("2026-11-02") }
            save(service, TimeOffCommand.Replace(first.id, TimeOffKind.VACATION, date("2026-11-07"), date("2026-11-08")))
            assertEquals(8, service.observeAvailableBalance().first())
        }
    }
    @Test fun invalid_or_insufficient_changes_do_not_partially_mutate_records() = runTest {
        withStore { db, _, service ->
            val before = db.dao().dirtyProjections(fixturePlan().key.trackerId)
            val invalid = TimeOffCommand.Add(TimeOffKind.VACATION, date("2026-11-04"), date("2026-11-02"))
            assertEquals(PlanRejection.INVALID_RANGE, service.preview(invalid).rejection)
            val outside = TimeOffCommand.Add(TimeOffKind.HOLIDAY, date("2026-10-26"), date("2026-10-26"))
            assertEquals(PlanRejection.OUTSIDE_TRACKED_PERIOD, service.preview(outside).rejection)
            assertIs<PlanMutationResult.Invalid>(service.setAvailableBalance(-1, null))
            val tooMuch = TimeOffCommand.Add(TimeOffKind.VACATION, date("2026-11-02"), date("2026-12-02"))
            val preview = service.preview(tooMuch)
            assertEquals(PlanRejection.INSUFFICIENT_BALANCE, preview.rejection)
            assertIs<PlanMutationResult.InsufficientBalance>(service.apply(tooMuch, preview.expectedRevision))
            assertTrue(service.observeEntries().first().isEmpty())
            assertEquals(before, db.dao().dirtyProjections(fixturePlan().key.trackerId))
        }
    }
    @Test fun balance_adjustment_target_is_idempotent_and_retains_verbatim_note() = runTest {
        withStore { db, _, service ->
            assertIs<PlanMutationResult.Applied>(service.setAvailableBalance(5, "  تعديل =1+2  "))
            assertIs<PlanMutationResult.Applied>(service.setAvailableBalance(5, "unused duplicate"))
            assertEquals(5, service.observeAvailableBalance().first())
            val adjustments = db.dao().balanceAdjustments(fixturePlan().key.trackerId)
            assertEquals(1, adjustments.size)
            assertEquals("  تعديل =1+2  ", adjustments.single().note)
        }
    }
    @Test fun preview_becomes_stale_after_a_record_changes() = runTest {
        withStore(at("09:02:00")) { db, clock, service ->
            val command = TimeOffCommand.Add(TimeOffKind.HOLIDAY, fixtureDate, fixtureDate)
            val preview = service.preview(command)
            val repo = RoomAttendanceRepository(db, clock)
            repo.correct(Correction(fixturePlan().key, AttendanceStatus.PRESENT, LocalTime(9, 0), null))
            assertIs<PlanMutationResult.StalePreview>(service.apply(command, preview.expectedRevision))
            assertTrue(service.observeEntries().first().isEmpty())
        }
    }
    @Test fun preview_becomes_stale_when_a_deadline_passes_without_other_user_edits() = runTest {
        withStore(at("09:04:00")) { db, clock, service ->
            val command = TimeOffCommand.Add(TimeOffKind.HOLIDAY, fixtureDate, fixtureDate)
            val preview = service.preview(command)
            clock.value = at("09:06:00")
            assertIs<PlanMutationResult.StalePreview>(service.apply(command, preview.expectedRevision))
            assertTrue(service.observeEntries().first().isEmpty())
            assertEquals(AttendanceStatus.ABSENT, RoomAttendanceRepository(db, clock).observeDay(fixturePlan().key).first()?.status)
        }
    }
    @Test fun today_overlay_aba_invalidates_gps_and_never_releases_consumed_claim() = runTest {
        withStore(at("09:02:00")) { db, clock, service ->
            val repo = RoomAttendanceRepository(db, clock)
            val key = fixturePlan().key
            assertIs<ClaimResult.Claimed>(repo.claimAutomatic(key, "one-auto", clock.now()))
            val started = CompletableDeferred<Unit>(); val reply = CompletableDeferred<LocationResult>()
            val gateway = object : LocationGateway {
                override suspend fun requestFix(budget: Duration): LocationResult { started.complete(Unit); return reply.await() }
            }
            val pending = async { AttendanceService(repo, gateway, clock).manualCheck() }
            started.await()
            save(service, TimeOffCommand.Add(TimeOffKind.HOLIDAY, fixtureDate, fixtureDate))
            save(service, TimeOffCommand.Remove(service.observeEntries().first().single().id))
            reply.complete(fixtureFix())
            assertIs<CheckOutcome.StateChanged>(pending.await())
            assertIs<ClaimResult.AlreadyConsumed>(repo.claimAutomatic(key, "no-second-auto", clock.now()))
            assertNull(repo.loadDay(key)?.evidence)
        }
    }
    @Test fun future_plan_recomputes_reservations_without_rewriting_historical_deductions() = runTest {
        withStore { db, clock, service ->
            save(service, TimeOffCommand.Add(TimeOffKind.VACATION, date("2026-10-28"), date("2026-10-28")))
            save(service, TimeOffCommand.Add(TimeOffKind.VACATION, date("2026-11-02"), date("2026-11-03")))
            assertEquals(9, service.observeAvailableBalance().first())
            val plans = PlanService(db, clock)
            val change = PlanChange(fixtureSchedule(days = weekdays), TrackerCategory.SCHOOL, fixturePlace())
            val preview = plans.previewChange(change)
            assertEquals(date("2026-11-02"), preview.effectiveFrom)
            assertEquals(11, preview.availableAfter)
            assertIs<PlanMutationResult.Applied>(plans.applyChange(change, preview.expectedRevision))
            assertEquals(11, service.observeAvailableBalance().first())
            val history = RoomAttendanceRepository(db, clock).loadDay(DayKey(fixturePlan().key.trackerId, date("2026-10-28")))
            assertEquals(TrackerCategory.WORK, history?.plan?.category)
            assertNotNull(plans.observePlan().first()?.pending)
        }
    }
    @Test fun future_plan_with_additional_unfunded_workdays_is_rejected_atomically() = runTest {
        withStore { db, clock, service ->
            save(service, TimeOffCommand.Add(TimeOffKind.VACATION, date("2026-11-07"), date("2026-11-08")))
            service.setAvailableBalance(0, null)
            val plans = PlanService(db, clock)
            val change = PlanChange(fixtureSchedule(), TrackerCategory.WORK, fixturePlace())
            val preview = plans.previewChange(change)
            assertEquals(PlanRejection.INSUFFICIENT_BALANCE, preview.rejection)
            assertIs<PlanMutationResult.InsufficientBalance>(plans.applyChange(change, preview.expectedRevision))
            assertEquals(1, db.dao().versions(fixturePlan().key.trackerId).size)
            assertNull(plans.observePlan().first()?.pending)
        }
    }
    @Test fun failed_outbox_write_rolls_back_leave_balance_and_preview_revision() = runTest {
        withStore { db, _, service ->
            val command = TimeOffCommand.Add(TimeOffKind.VACATION, date("2026-11-02"), date("2026-11-03"))
            val preview = service.preview(command)
            val tracker = db.dao().tracker()
            val dirty = db.dao().dirtyProjections(fixturePlan().key.trackerId)
            db.useWriterConnection { it.executeSQL("CREATE TRIGGER abort_leave BEFORE UPDATE ON dirty_projections WHEN NEW.projectionKey = 'OVERVIEW' BEGIN SELECT RAISE(ABORT, 'test_abort'); END") }
            assertIs<PlanMutationResult.LocalSaveFailed>(service.apply(command, preview.expectedRevision))
            assertEquals(tracker, db.dao().tracker())
            assertEquals(dirty, db.dao().dirtyProjections(fixturePlan().key.trackerId))
            assertTrue(service.observeEntries().first().isEmpty())
            assertEquals(12, service.observeAvailableBalance().first())
            db.useWriterConnection { it.executeSQL("DROP TRIGGER abort_leave") }
            assertIs<PlanMutationResult.Applied>(service.apply(command, preview.expectedRevision))
        }
    }
    @Test fun a_zone_change_does_not_charge_or_mark_absent_a_window_before_its_activation() = runTest {
        withStore { db, clock, service ->
            val plans = PlanService(db, clock)
            val command = PlanChange(fixtureSchedule(days = weekdays).copy(zoneId = "Pacific/Kiritimati"), TrackerCategory.WORK, fixturePlace())
            val preview = plans.previewChange(command)
            assertIs<PlanMutationResult.Applied>(plans.applyChange(command, preview.expectedRevision))
            save(service, TimeOffCommand.Add(TimeOffKind.VACATION, date("2026-11-02"), date("2026-11-02")))
            // New-zone November 2 at 09:00 occurred before old-zone midnight activated this change.
            assertEquals(12, service.observeAvailableBalance().first())
            clock.value = Instant.parse("2026-11-02T00:01:00Z")
            val repo = RoomAttendanceRepository(db, clock)
            repo.reconcile(clock.now())
            val day = requireNotNull(repo.loadDay(DayKey(fixturePlan().key.trackerId, date("2026-11-02"))))
            assertFalse(day.plan.isScheduled)
            assertEquals(AttendanceStatus.DAY_OFF, repo.observeDay(day.plan.key).first()?.status)
        }
    }
    @Test fun crossing_a_day_boundary_invalidates_a_plan_preview_even_before_tracking_starts() = runTest {
        val folder = Files.createTempDirectory("daymark-pending-preview-")
        val db = openJvmDatabase(folder.resolve("attendance.db").toString())
        val clock = TestClock(at("12:00:00"))
        try {
            seedDatabase(db, trackingStartsAt = Instant.parse("2026-11-10T09:00:00Z"))
            val plans = PlanService(db, clock)
            val command = PlanChange(fixtureSchedule(), TrackerCategory.COURSE, fixturePlace())
            val preview = plans.previewChange(command)
            clock.value = Instant.parse("2026-10-28T00:01:00Z")
            assertIs<PlanMutationResult.StalePreview>(plans.applyChange(command, preview.expectedRevision))
            assertEquals(1, db.dao().versions(fixturePlan().key.trackerId).size)
        } finally { db.close(); folder.toFile().deleteRecursively() }
    }

    @Test fun retrospective_preview_exposes_before_after_and_preserved_gps_evidence(): Unit = kotlinx.coroutines.runBlocking {
        val folder=java.nio.file.Files.createTempDirectory("daymark-preview-evidence-"); val db=dev.daymark.data.openJvmDatabase(folder.resolve("test.db").toString())
        try {
            dev.daymark.data.seedDatabase(db)
            val clock=dev.daymark.attendance.TestClock()
            val repo=dev.daymark.attendance.RoomAttendanceRepository(db,clock)
            repo.completeCheck(dev.daymark.data.testCommit())
            val service=TimeOffService(db,clock)
            val command=TimeOffCommand.Add(TimeOffKind.HOLIDAY,dev.daymark.fixtureDate,dev.daymark.fixtureDate)
            val preview=service.preview(command)
            val change=preview.changes.single()
            assertEquals(dev.daymark.attendance.AttendanceStatus.PRESENT,change.before.status)
            assertEquals(dev.daymark.attendance.AttendanceStatus.HOLIDAY,change.after.status)
            assertEquals(dev.daymark.attendance.AttendanceSource.MANUAL_GPS,change.evidence?.source)
            assertEquals(PlanMutationResult.Applied,service.apply(command,preview.expectedRevision))
            val entry=service.observeEntries().first().single()
            val removal=service.preview(TimeOffCommand.Remove(entry.id)).changes.single()
            assertEquals(dev.daymark.attendance.AttendanceStatus.HOLIDAY,removal.before.status)
            assertEquals(dev.daymark.attendance.AttendanceStatus.PRESENT,removal.after.status)
            assertEquals(dev.daymark.attendance.AttendanceSource.MANUAL_GPS,removal.evidence?.source)
        } finally { db.close(); folder.toFile().deleteRecursively() }
    }

    @Test fun cancelling_a_preview_preserves_an_inflight_automatic_confirmation(): Unit = kotlinx.coroutines.runBlocking {
        val folder=java.nio.file.Files.createTempDirectory("daymark-preview-claim-"); val db=dev.daymark.data.openJvmDatabase(folder.resolve("test.db").toString())
        try {
            dev.daymark.data.seedDatabase(db)
            val clock=dev.daymark.attendance.TestClock()
            val repo=dev.daymark.attendance.RoomAttendanceRepository(db,clock)
            val attempt=kotlin.uuid.Uuid.random().toString()
            assertTrue(repo.claimAutomatic(dev.daymark.fixturePlan().key,attempt,clock.now()) is dev.daymark.attendance.ClaimResult.Claimed)
            TimeOffService(db,clock).preview(TimeOffCommand.Add(TimeOffKind.HOLIDAY,dev.daymark.fixtureDate,dev.daymark.fixtureDate))
            // No apply: the user dismisses the preview. The already-claimed valid sensor result still commits.
            val request=dev.daymark.data.testCommit().copy(attemptId=attempt,mode=dev.daymark.attendance.CheckMode.AUTOMATIC)
            assertTrue(repo.completeCheck(request) is dev.daymark.attendance.CheckOutcome.Recorded)
        } finally { db.close(); folder.toFile().deleteRecursively() }
    }

}
