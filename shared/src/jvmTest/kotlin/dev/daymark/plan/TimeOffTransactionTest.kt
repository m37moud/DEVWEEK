package dev.daymark.plan

import dev.daymark.data.local.RoomDatabaseTransactions

import androidx.room3.executeSQL
import androidx.room3.useWriterConnection
import dev.daymark.at
import dev.daymark.attendance.AttendanceService
import dev.daymark.attendance.TestClock
import dev.daymark.data.local.DaymarkDatabase
import dev.daymark.data.local.PlanVersionEntity
import dev.daymark.data.local.attendance.RoomAttendanceRepository
import dev.daymark.data.local.openJvmDatabase
import dev.daymark.data.local.dashboard.TodayDashboardReader
import dev.daymark.data.local.plan.PlanService
import dev.daymark.data.local.plan.PlanningSnapshotReader
import dev.daymark.data.local.reporting.RoomReportStore
import dev.daymark.data.local.plan.TimeOffService
import dev.daymark.data.local.plan.change
import dev.daymark.data.local.plan.entry
import dev.daymark.data.seedDatabase
import dev.daymark.fixtureDate
import dev.daymark.fixtureFix
import dev.daymark.fixturePlace
import dev.daymark.fixturePlan
import dev.daymark.fixtureSchedule
import dev.daymark.history.AttendancePeriodSettings
import dev.daymark.history.HistoryMetric
import dev.daymark.history.historyMetrics
import dev.daymark.history.VacationYearBasis
import dev.daymark.models.plan.AttendancePeriodBalanceChange
import dev.daymark.location.LocationGateway
import dev.daymark.location.LocationResult
import dev.daymark.models.attendance.AttendanceSource
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.models.attendance.CheckOutcome
import dev.daymark.models.attendance.ClaimResult
import dev.daymark.models.attendance.Correction
import dev.daymark.models.attendance.DayKey
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.plan.AnnualVacationPolicy
import dev.daymark.models.plan.PlanChange
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.models.plan.PlanRejection
import dev.daymark.models.plan.TimeOffCommand
import dev.daymark.models.plan.VacationBalancePeriodChange
import dev.daymark.models.plan.VacationBalanceSummary
import dev.daymark.models.reporting.ProjectionKey
import dev.daymark.models.reporting.ReportBinding
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class TimeOffTransactionTest {
    private val weekdays = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY
    )

    private fun date(value: String) = LocalDate.parse(value)
    private suspend fun withStore(
        initialNow: Instant = Instant.parse("2026-11-01T12:00:00Z"),
        setupDate: LocalDate = fixtureDate,
        scheduledDays: Set<DayOfWeek> = weekdays,
        block: suspend (DaymarkDatabase, TestClock, TimeOffService) -> Unit
    ) {
        val folder = Files.createTempDirectory("daymark-time-off-")
        val db = openJvmDatabase(folder.resolve("attendance.db").toString())
        val clock = TestClock(initialNow)
        try {
            val setupAt = Instant.parse("${setupDate}T08:00:00Z")
            seedDatabase(db, setupAt, Instant.parse("${setupDate}T09:00:00Z"))
            val tracker = requireNotNull(db.profileDao().tracker())
            db.profileDao().putTracker(tracker.copy(openingBalance = 12))
            db.planningDao().putVersion(
                PlanVersionEntity.fromSnapshot(
                    tracker.id, 1, setupDate, setupAt,
                    TrackerCategory.WORK, fixtureSchedule(days = scheduledDays), fixturePlace(), "Work"
                )
            )
            RoomAttendanceRepository(
                db.profileDao(), db.attendanceDao(), db.planningDao(), db.dirtyProjectionDao(),
                RoomDatabaseTransactions(db),
                clock
            ).reconcile(clock.now())
            block(
                db,
                clock,
                TimeOffService(
                    db.profileDao(),
                    db.attendanceDao(),
                    db.planningDao(),
                    db.dirtyProjectionDao(),
                    dev.daymark.data.local.plan.PlanningSnapshotReader(
                        db.profileDao(),
                        db.attendanceDao(),
                        db.planningDao()
                    ),
                    RoomDatabaseTransactions(db),
                    clock,
                    attendance = dev.daymark.data.local.attendance.RoomAttendanceRepository(
                        db.profileDao(),
                        db.attendanceDao(),
                        db.planningDao(),
                        db.dirtyProjectionDao(),
                        RoomDatabaseTransactions(db),
                        clock
                    )
                )
            )
        } finally {
            db.close(); folder.toFile().deleteRecursively()
        }
    }

    private suspend fun save(service: TimeOffService, command: TimeOffCommand) {
        val preview = service.preview(command)
        assertNull(preview.rejection)
        assertIs<PlanMutationResult.Applied>(service.apply(command, preview.reviewToken))
    }

    @Test
    fun work_mission_updates_the_closing_period_and_removal_restores_absence() = runTest {
        withStore(
            Instant.parse("2026-11-21T12:00:00Z"),
            setupDate = date("2026-10-21"),
            scheduledDays = weekdays + DayOfWeek.SUNDAY
        ) { db, clock, service ->
            val missionDate = date("2026-10-25")
            val trackerId = requireNotNull(db.profileDao().tracker()).id
            val key = DayKey(trackerId, missionDate)
            val period = AttendancePeriodSettings(20).period(date("2026-11-01"))
            assertEquals(date("2026-10-21"), period.from)
            assertEquals(date("2026-11-20"), period.through)
            val repository = RoomAttendanceRepository(
                db.profileDao(), db.attendanceDao(), db.planningDao(), db.dirtyProjectionDao(),
                RoomDatabaseTransactions(db), clock
            )
            val before = repository.observeRange(period.from, period.through).first()
            assertEquals(AttendanceStatus.ABSENT, before.records.single { it.plan.key == key }.outcome.status)
            val beforeMetrics = historyMetrics(before.records, before.today)

            val command = TimeOffCommand.AddDates(TimeOffKind.WORK_MISSION, setOf(missionDate), "=1+2")
            save(service, command)
            save(service, command)
            val after = repository.observeRange(period.from, period.through).first()
            val metrics = historyMetrics(after.records, after.today)
            assertEquals(beforeMetrics.attendance + 1, metrics.attendance)
            assertEquals(beforeMetrics.absent - 1, metrics.absent)
            assertEquals(1, metrics.workMissions)
            assertEquals(listOf(missionDate), after.records.filter {
                HistoryMetric.WORK_MISSION.includes(it)
            }.map { it.plan.key.date })
            assertEquals(12, service.observeAvailableBalance().first())

            val secondDate = date("2026-11-07")
            save(service, TimeOffCommand.AddDates(TimeOffKind.WORK_MISSION, setOf(missionDate, secondDate)))
            val weekend = repository.observeRange(secondDate, secondDate).first().records.single()
            assertFalse(weekend.plan.isScheduled)
            assertEquals(AttendanceStatus.WORK_MISSION, weekend.outcome.status)
            val missions = service.observeEntries().first()
            assertEquals(setOf(missionDate, secondDate), missions.map { it.from }.toSet())
            val mission = missions.single { it.from == missionDate }
            assertEquals("=1+2", mission.note)
            save(service, TimeOffCommand.Replace(mission.id, mission.kind, missionDate, missionDate, "زيارة عميل"))
            save(service, TimeOffCommand.Remove(mission.id))
            val restored = repository.observeRange(period.from, period.through).first()
            assertEquals(AttendanceStatus.ABSENT, restored.records.single { it.plan.key == key }.outcome.status)
        }
    }

    @Test
    fun separate_days_are_atomic_and_overlapping_missions_preserve_confirmed_attendance() = runTest {
        withStore { db, clock, service ->
            val present = fixtureDate
            val vacation = date("2026-10-28")
            val otherVacation = date("2026-10-30")
            val holiday = date("2026-10-29")
            val laterHoliday = date("2026-11-03")
            val repository = RoomAttendanceRepository(
                db.profileDao(), db.attendanceDao(), db.planningDao(), db.dirtyProjectionDao(),
                RoomDatabaseTransactions(db), clock
            )
            val key = fixturePlan().key
            repository.correct(Correction(key, AttendanceStatus.PRESENT, LocalTime(9, 0), "Original"))
            save(service, TimeOffCommand.AddDates(TimeOffKind.VACATION, setOf(vacation, otherVacation)))
            save(service, TimeOffCommand.AddDates(TimeOffKind.HOLIDAY, setOf(holiday, laterHoliday)))
            val blocked = TimeOffCommand.AddDates(TimeOffKind.WORK_MISSION, setOf(present, vacation, laterHoliday))
            val review = service.preview(blocked)
            assertEquals(PlanRejection.WORK_MISSION_CONFLICT, review.rejection)
            assertEquals(
                PlanMutationResult.Invalid(PlanRejection.WORK_MISSION_CONFLICT),
                service.apply(blocked, review.reviewToken)
            )
            assertTrue(service.observeEntries().first().none { it.kind == TimeOffKind.WORK_MISSION })
            assertEquals(10, service.observeAvailableBalance().first())

            save(service, TimeOffCommand.AddDates(TimeOffKind.WORK_MISSION, setOf(present)))
            assertEquals(AttendanceStatus.WORK_MISSION, repository.observeDay(key).first()?.status)
            assertNull(repository.observeDay(key).first()?.confirmedAt)
            assertEquals(AttendanceStatus.PRESENT, repository.loadDay(key)?.evidence?.status)
            val conflictingHoliday = TimeOffCommand.Add(TimeOffKind.HOLIDAY, present, present)
            assertEquals(PlanRejection.WORK_MISSION_CONFLICT, service.preview(conflictingHoliday).rejection)
            save(
                service,
                TimeOffCommand.Remove(
                    service.observeEntries().first().single { it.kind == TimeOffKind.WORK_MISSION }.id
                )
            )
            assertEquals(AttendanceStatus.PRESENT, repository.observeDay(key).first()?.status)
            assertEquals("Original", repository.loadDay(key)?.evidence?.note)
            assertEquals(10, service.observeAvailableBalance().first())
        }
    }

    @Test
    fun vacation_on_november_19_can_be_saved_after_setup_on_november_21() = runTest {
        withStore(Instant.parse("2026-11-21T12:00:00Z"), setupDate = date("2026-11-21")) { db, _, service ->
            val earlier = date("2026-11-19")
            val command = TimeOffCommand.Add(TimeOffKind.VACATION, earlier, earlier)
            val preview = service.preview(command)
            assertNull(preview.rejection)
            assertEquals(-1, preview.balanceDelta)
            assertEquals(listOf(earlier), preview.preTrackingDates)
            assertTrue(preview.affectedDays.isEmpty())
            assertEquals(PlanMutationResult.Applied, service.apply(command, preview.reviewToken))
            val entry = service.observeEntries().first().single()
            assertEquals(earlier, entry.from)
            assertEquals(earlier, entry.through)
            assertEquals(11, service.observeAvailableBalance().first())
            val trackerId = requireNotNull(db.profileDao().tracker()).id
            assertTrue(db.attendanceDao().days(trackerId).none { it.date < "2026-11-21" })
            assertTrue(
                db.dirtyProjectionDao().dirtyProjections(trackerId).none { it.projectionKey == "DAY:2026-11-19" })

            save(service, TimeOffCommand.Remove(entry.id))
            assertEquals(12, service.observeAvailableBalance().first())

            val acrossStart = TimeOffCommand.Add(TimeOffKind.VACATION, date("2026-11-20"), date("2026-11-23"))
            val rangeReview = service.preview(acrossStart)
            assertEquals(listOf(date("2026-11-20")), rangeReview.preTrackingDates)
            assertEquals(
                listOf(date("2026-11-21"), date("2026-11-22"), date("2026-11-23")),
                rangeReview.affectedDays.map { it.date })
            assertEquals(-2, rangeReview.balanceDelta)
        }
    }

    @Test
    fun annual_renewal_allocates_cross_year_bookings_and_fences_a_stale_review() = runTest {
        withStore { db, clock, service ->
            assertEquals(PlanMutationResult.Applied, service.updateAnnualVacationPolicy(4, false, 2027))
            assertEquals(AnnualVacationPolicy(2027, 4, false), service.observeAnnualVacationSettings().first()?.pending)
            val booking = TimeOffCommand.Add(TimeOffKind.VACATION, date("2026-12-31"), date("2027-01-01"))
            val preview = service.preview(booking)
            assertEquals(
                listOf(VacationBalancePeriodChange(null, -1, 11), VacationBalancePeriodChange(2027, -1, 3)),
                preview.balancePeriods
            )
            assertEquals(PlanMutationResult.Applied, service.apply(booking, preview.reviewToken))
            assertEquals(VacationBalanceSummary(12, 1, 11), service.observeVacationBalance().first())

            val holiday = TimeOffCommand.Add(TimeOffKind.HOLIDAY, date("2027-01-01"), date("2027-01-01"))
            val lateReview = service.preview(holiday)
            clock.value = Instant.parse("2027-01-01T00:00:00Z")
            assertEquals(PlanMutationResult.StalePreview, service.apply(holiday, lateReview.reviewToken))
            assertEquals(VacationBalanceSummary(4, 1, 3, 2027), service.observeVacationBalance().first())
            assertEquals(PlanMutationResult.StalePreview, service.setAvailableBalance(6, null))
            assertEquals(PlanMutationResult.Applied, service.setAvailableBalance(6, null, 2027))
            assertEquals(
                2027,
                db.planningDao().balanceAdjustments(requireNotNull(db.profileDao().tracker()).id).single().balanceYear
            )
            save(service, holiday)
            assertEquals(VacationBalanceSummary(4, 0, 7, 2027), service.observeVacationBalance().first())

            assertEquals(PlanMutationResult.Applied, service.updateAnnualVacationPolicy(8, false, 2028))
            clock.value = Instant.parse("2029-01-01T00:00:00Z")
            assertEquals(VacationBalanceSummary(8, 0, 8, 2029), service.observeVacationBalance().first())
        }
    }

    @Test
    fun attendance_period_renewal_reallocates_existing_bookings_and_keeps_adjustment_years() = runTest {
        withStore { db, clock, service ->
            assertEquals(PlanMutationResult.Applied, service.updateAnnualVacationPolicy(4, true, 2027))
            assertEquals(PlanMutationResult.Applied, service.setAvailableBalance(10, null))
            val december = date("2026-12-21")
            save(service, TimeOffCommand.Add(TimeOffKind.VACATION, december, december))
            assertEquals(VacationBalanceSummary(12, 1, 9), service.observeVacationBalance().first())

            val settings = AttendancePeriodSettings(20, VacationYearBasis.ATTENDANCE_PERIOD)
            val preview = assertNotNull(service.previewAttendancePeriod(settings))
            assertEquals(
                listOf(AttendancePeriodBalanceChange(null, -1, 10), AttendancePeriodBalanceChange(2027, 1, 13)),
                preview.balances
            )
            val stale = service.preview(TimeOffCommand.Add(TimeOffKind.HOLIDAY, december, december))
            assertEquals(PlanMutationResult.Applied, service.saveAttendancePeriod(preview))
            assertEquals(
                PlanMutationResult.StalePreview,
                service.apply(TimeOffCommand.Add(TimeOffKind.HOLIDAY, december, december), stale.reviewToken)
            )
            assertEquals(december, service.observeAnnualVacationSettings().first()?.nextEffectiveDate)
            clock.value = Instant.parse("2026-12-21T00:00:00Z")
            assertEquals(VacationBalanceSummary(14, 1, 13, 2027), service.observeVacationBalance().first())
            val trackerId = assertNotNull(db.profileDao().tracker()).id
            assertNull(db.planningDao().balanceAdjustments(trackerId).single().balanceYear)
        }
    }

    @Test
    fun cutoff_regroups_dashboard_and_reports_and_clears_the_previous_summary_row() = runTest {
        withStore { db, clock, service ->
            val profile = db.profileDao()
            val attendanceDao = db.attendanceDao()
            val planning = db.planningDao()
            val dirty = db.dirtyProjectionDao()
            val transactions = RoomDatabaseTransactions(db)
            val snapshots = PlanningSnapshotReader(profile, attendanceDao, planning)
            val repo = RoomAttendanceRepository(profile, attendanceDao, planning, dirty, transactions, clock)
            val target = DayKey(assertNotNull(profile.tracker()).id, date("2026-10-28"))
            repo.correct(Correction(target, AttendanceStatus.PRESENT, LocalTime(9, 0), null))
            val dashboard = TodayDashboardReader(profile, snapshots, transactions, clock)
            assertEquals(0, dashboard.observe().first().metrics.attendance)

            val store = RoomReportStore(profile, attendanceDao, dirty, db.reportDao(), snapshots, transactions, clock)
            val binding = ReportBinding(target.trackerId, assertNotNull(store.beginConnection()), "test", "report", annualTabIds = emptyMap())
            store.bind(binding)
            val oldBatch = assertNotNull(store.buildBatch(binding, 100))
            store.journalBeforeDispatch(oldBatch)
            store.markDispatched(oldBatch)

            val review = assertNotNull(service.previewAttendancePeriod(AttendancePeriodSettings(20)))
            assertEquals(PlanMutationResult.Applied, service.saveAttendancePeriod(review))
            store.acknowledge(oldBatch)

            val current = dashboard.observe().first()
            assertEquals(review.period, current.period)
            assertEquals(1, current.metrics.attendance)
            assertEquals(1, repo.observeMonth(2026, 11).first().present)
            assertEquals(0, repo.observeMonth(2026, 10).first().present)
            val periods = store.localProjections(binding).single { it.key == ProjectionKey.AttendancePeriods }
            val rows = periods.ranges.single().cells
            assertEquals(listOf("2026-11", "2026-10-21", "2026-11-20", "1"), rows[0].take(4).map { it.content })
            assertTrue(rows[1].all { it.content.isEmpty() })
            assertTrue(assertNotNull(store.buildBatch(binding, 100)).projections.any { it.key == periods.key })
        }
    }

    @Test
    fun attendance_period_settings_reject_a_new_annual_shortfall_atomically() = runTest {
        withStore { db, _, service ->
            assertEquals(PlanMutationResult.Applied, service.updateAnnualVacationPolicy(0, false, 2027))
            val december = date("2026-12-21")
            save(service, TimeOffCommand.Add(TimeOffKind.VACATION, december, december))
            val review = assertNotNull(
                service.previewAttendancePeriod(AttendancePeriodSettings(20, VacationYearBasis.ATTENDANCE_PERIOD))
            )
            assertEquals(PlanMutationResult.InsufficientYear(2027), review.rejection)
            assertEquals(review.rejection, service.saveAttendancePeriod(review))
            assertNull(db.profileDao().setting("attendance-period"))
            assertEquals(VacationBalanceSummary(12, 1, 11), service.observeVacationBalance().first())
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun active_balance_observation_renews_without_a_database_write() = runTest {
        withStore(Instant.parse("2026-12-31T23:59:59Z")) { _, clock, service ->
            assertEquals(PlanMutationResult.Applied, service.updateAnnualVacationPolicy(4, false, 2027))
            val summaries = service.observeVacationBalance().produceIn(backgroundScope)
            try {
                assertEquals(VacationBalanceSummary(12, 0, 12), summaries.receive())
                runCurrent()
                clock.value = Instant.parse("2027-01-01T00:00:00Z")
                advanceTimeBy(1_001)
                assertEquals(VacationBalanceSummary(4, 0, 4, 2027), summaries.receive())
            } finally {
                summaries.cancel()
            }
        }
    }

    @Test
    fun historical_deduction_cannot_spend_carryover_already_reserved_in_a_later_year() = runTest {
        withStore { _, clock, service ->
            assertEquals(PlanMutationResult.Applied, service.updateAnnualVacationPolicy(2, true, 2027))
            val booking = TimeOffCommand.Add(TimeOffKind.VACATION, date("2028-01-03"), date("2028-01-24"))
            save(service, booking)
            clock.value = Instant.parse("2027-01-05T12:00:00Z")
            val earlier = TimeOffCommand.Add(TimeOffKind.VACATION, date("2027-01-04"), date("2027-01-04"))
            val preview = service.preview(earlier)
            assertEquals(2028, preview.shortfallYear)
            assertEquals(PlanMutationResult.InsufficientYear(2028), service.apply(earlier, preview.reviewToken))
            assertEquals(PlanMutationResult.InsufficientYear(2028), service.updateAnnualVacationPolicy(2, false, 2028))
            assertEquals(1, service.observeEntries().first().size)

            clock.value = Instant.parse("2028-01-01T00:00:00Z")
            assertEquals(VacationBalanceSummary(16, 16, 0, 2028), service.observeVacationBalance().first())
        }
    }

    @Test
    fun renewal_uses_attendance_zone_and_a_failed_first_policy_keeps_the_legacy_balance() = runTest {
        withStore { db, clock, service ->
            val tracker = requireNotNull(db.profileDao().tracker())
            val cairo = fixtureSchedule(days = weekdays).copy(zoneId = "Africa/Cairo")
            db.planningDao().putVersion(
                PlanVersionEntity.fromSnapshot(
                    tracker.id,
                    1,
                    fixtureDate,
                    at("08:00:00"),
                    TrackerCategory.WORK,
                    cairo,
                    fixturePlace(),
                    "Work"
                )
            )
            val booking = TimeOffCommand.Add(TimeOffKind.VACATION, date("2027-01-04"), date("2027-01-05"))
            save(service, booking)
            assertEquals(PlanMutationResult.InsufficientYear(2027), service.updateAnnualVacationPolicy(1, false, 2027))
            assertNull(service.observeAnnualVacationSettings().first()?.pending)
            assertEquals(10, service.observeAvailableBalance().first())
            assertEquals(PlanMutationResult.Applied, service.updateAnnualVacationPolicy(5, false, 2027))
            clock.value = Instant.parse("2026-12-31T21:59:59Z")
            assertNull(service.observeVacationBalance().first()?.year)
            clock.value = Instant.parse("2026-12-31T22:00:00Z")
            assertEquals(VacationBalanceSummary(5, 2, 3, 2027), service.observeVacationBalance().first())
        }
    }

    @Test
    fun holiday_refund_and_repeat_save_are_idempotent() = runTest {
        withStore { _, _, service ->
            val vacation =
                TimeOffCommand.Add(TimeOffKind.VACATION, date("2026-11-02"), date("2026-11-03"))
            save(service, vacation)
            assertEquals(10, service.observeAvailableBalance().first())
            save(
                service,
                TimeOffCommand.Add(TimeOffKind.HOLIDAY, date("2026-11-03"), date("2026-11-03"))
            )
            assertEquals(11, service.observeAvailableBalance().first())
            val holiday = service.observeEntries().first().single { it.kind == TimeOffKind.HOLIDAY }
            save(service, TimeOffCommand.Remove(holiday.id))
            assertEquals(10, service.observeAvailableBalance().first())
            save(service, vacation)
            assertEquals(10, service.observeAvailableBalance().first())
            assertEquals(1, service.observeEntries().first().size)
        }
    }

    @Test
    fun vacation_summary_counts_booked_days_once_and_preserves_the_original_balance() = runTest {
        withStore { db, _, service ->
            val tracker = requireNotNull(db.profileDao().tracker())
            db.profileDao().putTracker(tracker.copy(openingBalance = 20))
            save(service, TimeOffCommand.Add(TimeOffKind.VACATION, date("2026-10-28"), date("2026-11-06")))
            save(service, TimeOffCommand.Add(TimeOffKind.VACATION, date("2026-11-02"), date("2026-11-03")))
            save(service, TimeOffCommand.Add(TimeOffKind.HOLIDAY, date("2026-11-04"), date("2026-11-04")))
            assertEquals(VacationBalanceSummary(20, 7, 13), service.observeVacationBalance().first())
            assertIs<PlanMutationResult.Applied>(service.setAvailableBalance(15, null))
            assertEquals(VacationBalanceSummary(20, 7, 15), service.observeVacationBalance().first())
            assertEquals(15, service.observeAvailableBalance().first())
        }
    }

    @Test
    fun removing_retrospective_leave_restores_absence_and_underlying_correction() = runTest {
        withStore { db, clock, service ->
            val repo = RoomAttendanceRepository(
                db.profileDao(),
                db.attendanceDao(),
                db.planningDao(),
                db.dirtyProjectionDao(),
                RoomDatabaseTransactions(db),
                clock
            )
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

    @Test
    fun overlapping_entries_and_replacement_apply_only_the_deduction_difference() = runTest {
        withStore { _, _, service ->
            save(
                service,
                TimeOffCommand.Add(TimeOffKind.VACATION, date("2026-11-02"), date("2026-11-04"))
            )
            save(
                service,
                TimeOffCommand.Add(TimeOffKind.VACATION, date("2026-11-03"), date("2026-11-06"))
            )
            assertEquals(7, service.observeAvailableBalance().first())
            val first = service.observeEntries().first().first { it.from == date("2026-11-02") }
            save(
                service,
                TimeOffCommand.Replace(
                    first.id,
                    TimeOffKind.VACATION,
                    date("2026-11-07"),
                    date("2026-11-08")
                )
            )
            assertEquals(8, service.observeAvailableBalance().first())
        }
    }

    @Test
    fun invalid_or_insufficient_changes_do_not_partially_mutate_records() = runTest {
        withStore { db, _, service ->
            val before = db.dirtyProjectionDao().dirtyProjections(fixturePlan().key.trackerId)
            val invalid =
                TimeOffCommand.Add(TimeOffKind.VACATION, date("2026-11-04"), date("2026-11-02"))
            assertEquals(PlanRejection.INVALID_RANGE, service.preview(invalid).rejection)
            assertIs<PlanMutationResult.Invalid>(service.setAvailableBalance(-1, null))
            val tooMuch =
                TimeOffCommand.Add(TimeOffKind.VACATION, date("2026-11-02"), date("2026-12-02"))
            val preview = service.preview(tooMuch)
            assertEquals(PlanRejection.INSUFFICIENT_BALANCE, preview.rejection)
            assertIs<PlanMutationResult.InsufficientBalance>(
                service.apply(
                    tooMuch,
                    preview.reviewToken
                )
            )
            assertTrue(service.observeEntries().first().isEmpty())
            assertEquals(
                before,
                db.dirtyProjectionDao().dirtyProjections(fixturePlan().key.trackerId)
            )
        }
    }

    @Test
    fun balance_adjustment_target_is_idempotent_and_retains_verbatim_note() = runTest {
        withStore { db, _, service ->
            assertIs<PlanMutationResult.Applied>(service.setAvailableBalance(5, "  تعديل =1+2  "))
            assertIs<PlanMutationResult.Applied>(service.setAvailableBalance(5, "unused duplicate"))
            assertEquals(5, service.observeAvailableBalance().first())
            val adjustments = db.planningDao().balanceAdjustments(fixturePlan().key.trackerId)
            assertEquals(1, adjustments.size)
            assertEquals("  تعديل =1+2  ", adjustments.single().note)
        }
    }

    @Test
    fun preview_becomes_stale_after_a_record_changes() = runTest {
        withStore(at("09:02:00")) { db, clock, service ->
            val command = TimeOffCommand.Add(TimeOffKind.HOLIDAY, fixtureDate, fixtureDate)
            val preview = service.preview(command)
            val repo = RoomAttendanceRepository(
                db.profileDao(),
                db.attendanceDao(),
                db.planningDao(),
                db.dirtyProjectionDao(),
                RoomDatabaseTransactions(db),
                clock
            )
            repo.correct(
                Correction(
                    fixturePlan().key,
                    AttendanceStatus.PRESENT,
                    LocalTime(9, 0),
                    null
                )
            )
            assertIs<PlanMutationResult.StalePreview>(
                service.apply(
                    command,
                    preview.reviewToken
                )
            )
            assertTrue(service.observeEntries().first().isEmpty())
        }
    }

    @Test
    fun preview_becomes_stale_when_a_deadline_passes_without_other_user_edits() = runTest {
        withStore(at("09:04:00")) { db, clock, service ->
            val command = TimeOffCommand.Add(TimeOffKind.HOLIDAY, fixtureDate, fixtureDate)
            val preview = service.preview(command)
            clock.value = at("09:06:00")
            assertIs<PlanMutationResult.StalePreview>(
                service.apply(
                    command,
                    preview.reviewToken
                )
            )
            assertTrue(service.observeEntries().first().isEmpty())
            assertEquals(
                AttendanceStatus.ABSENT,
                RoomAttendanceRepository(
                    db.profileDao(),
                    db.attendanceDao(),
                    db.planningDao(),
                    db.dirtyProjectionDao(),
                    RoomDatabaseTransactions(db),
                    clock
                ).observeDay(
                    fixturePlan().key
                ).first()?.status
            )
        }
    }

    @Test
    fun today_overlay_aba_invalidates_gps_and_never_releases_consumed_claim() = runTest {
        withStore(at("09:02:00")) { db, clock, service ->
            val repo = RoomAttendanceRepository(
                db.profileDao(),
                db.attendanceDao(),
                db.planningDao(),
                db.dirtyProjectionDao(),
                RoomDatabaseTransactions(db),
                clock
            )
            val key = fixturePlan().key
            assertIs<ClaimResult.Claimed>(repo.claimAutomatic(key, "one-auto", clock.now()))
            val started = CompletableDeferred<Unit>();
            val reply = CompletableDeferred<LocationResult>()
            val gateway = object : LocationGateway {
                override suspend fun requestFix(budget: Duration): LocationResult {
                    started.complete(Unit); return reply.await()
                }
            }
            val pending = async { AttendanceService(repo, gateway, clock).manualCheck() }
            started.await()
            save(service, TimeOffCommand.AddDates(TimeOffKind.WORK_MISSION, setOf(fixtureDate)))
            assertIs<CheckOutcome.Rejected>(AttendanceService(repo, gateway, clock).manualCheck())
            save(service, TimeOffCommand.Remove(service.observeEntries().first().single().id))
            reply.complete(fixtureFix())
            assertIs<CheckOutcome.StateChanged>(pending.await())
            assertIs<ClaimResult.AlreadyConsumed>(
                repo.claimAutomatic(
                    key,
                    "no-second-auto",
                    clock.now()
                )
            )
            assertNull(repo.loadDay(key)?.evidence)
        }
    }

    @Test
    fun future_plan_recomputes_reservations_without_rewriting_historical_deductions() = runTest {
        withStore { db, clock, service ->
            save(
                service,
                TimeOffCommand.Add(TimeOffKind.VACATION, date("2026-10-28"), date("2026-10-28"))
            )
            save(
                service,
                TimeOffCommand.Add(TimeOffKind.VACATION, date("2026-11-02"), date("2026-11-03"))
            )
            assertEquals(9, service.observeAvailableBalance().first())
            val plans = PlanService(
                db.profileDao(),
                db.planningDao(),
                db.dirtyProjectionDao(),
                dev.daymark.data.local.plan.PlanningSnapshotReader(
                    db.profileDao(),
                    db.attendanceDao(),
                    db.planningDao()
                ),
                RoomDatabaseTransactions(db),
                clock,
                attendance = dev.daymark.data.local.attendance.RoomAttendanceRepository(
                    db.profileDao(), db.attendanceDao(), db.planningDao(), db.dirtyProjectionDao(),
                    RoomDatabaseTransactions(db),
                    clock
                )
            )
            val change =
                PlanChange(fixtureSchedule(days = weekdays), TrackerCategory.SCHOOL, fixturePlace())
            val preview = plans.previewChange(change)
            assertEquals(date("2026-11-02"), preview.effectiveFrom)
            assertEquals(11, preview.availableAfter)
            assertIs<PlanMutationResult.Applied>(
                plans.applyChange(
                    change,
                    preview.reviewToken
                )
            )
            assertEquals(11, service.observeAvailableBalance().first())
            val history = RoomAttendanceRepository(
                db.profileDao(),
                db.attendanceDao(),
                db.planningDao(),
                db.dirtyProjectionDao(),
                RoomDatabaseTransactions(db),
                clock
            ).loadDay(
                DayKey(
                    fixturePlan().key.trackerId,
                    date("2026-10-28")
                )
            )
            assertEquals(TrackerCategory.WORK, history?.plan?.category)
            assertNotNull(plans.observePlan().first()?.pending)
            clock.value = Instant.parse("2026-11-04T12:00:00Z")
            val changedPlan = assertNotNull(plans.observePlan().first())
            assertEquals(TrackerCategory.SCHOOL, changedPlan.current.category)
            assertTrue(changedPlan.hasWorkDates)
            val schoolMission = TimeOffCommand.AddDates(TimeOffKind.WORK_MISSION, setOf(date("2026-11-04")))
            assertEquals(PlanRejection.WORK_MISSION_INELIGIBLE, service.preview(schoolMission).rejection)
        }
    }

    @Test
    fun future_plan_with_additional_unfunded_workdays_is_rejected_atomically() = runTest {
        withStore { db, clock, service ->
            save(
                service,
                TimeOffCommand.Add(TimeOffKind.VACATION, date("2026-11-07"), date("2026-11-08"))
            )
            service.setAvailableBalance(0, null)
            val plans = PlanService(
                db.profileDao(),
                db.planningDao(),
                db.dirtyProjectionDao(),
                dev.daymark.data.local.plan.PlanningSnapshotReader(
                    db.profileDao(),
                    db.attendanceDao(),
                    db.planningDao()
                ),
                RoomDatabaseTransactions(db),
                clock,
                attendance = dev.daymark.data.local.attendance.RoomAttendanceRepository(
                    db.profileDao(), db.attendanceDao(), db.planningDao(), db.dirtyProjectionDao(),
                    RoomDatabaseTransactions(db),
                    clock
                )
            )
            val change = PlanChange(fixtureSchedule(), TrackerCategory.WORK, fixturePlace())
            val preview = plans.previewChange(change)
            assertEquals(PlanRejection.INSUFFICIENT_BALANCE, preview.rejection)
            assertIs<PlanMutationResult.InsufficientBalance>(
                plans.applyChange(
                    change,
                    preview.reviewToken
                )
            )
            assertEquals(1, db.planningDao().versions(fixturePlan().key.trackerId).size)
            assertNull(plans.observePlan().first()?.pending)
        }
    }

    @Test
    fun failed_outbox_write_rolls_back_leave_balance_and_preview_revision() = runTest {
        withStore { db, _, service ->
            val command =
                TimeOffCommand.Add(TimeOffKind.VACATION, date("2026-11-02"), date("2026-11-03"))
            val preview = service.preview(command)
            val tracker = db.profileDao().tracker()
            val dirty = db.dirtyProjectionDao().dirtyProjections(fixturePlan().key.trackerId)
            db.useWriterConnection { it.executeSQL("CREATE TRIGGER abort_leave BEFORE UPDATE ON dirty_projections WHEN NEW.projectionKey = 'OVERVIEW' BEGIN SELECT RAISE(ABORT, 'test_abort'); END") }
            assertIs<PlanMutationResult.LocalSaveFailed>(
                service.apply(
                    command,
                    preview.reviewToken
                )
            )
            assertEquals(tracker, db.profileDao().tracker())
            assertEquals(
                dirty,
                db.dirtyProjectionDao().dirtyProjections(fixturePlan().key.trackerId)
            )
            assertTrue(service.observeEntries().first().isEmpty())
            assertEquals(12, service.observeAvailableBalance().first())
            db.useWriterConnection { it.executeSQL("DROP TRIGGER abort_leave") }
            assertIs<PlanMutationResult.Applied>(service.apply(command, preview.reviewToken))
        }
    }

    @Test
    fun a_zone_change_does_not_charge_or_mark_absent_a_window_before_its_activation() = runTest {
        withStore { db, clock, service ->
            val plans = PlanService(
                db.profileDao(),
                db.planningDao(),
                db.dirtyProjectionDao(),
                dev.daymark.data.local.plan.PlanningSnapshotReader(
                    db.profileDao(),
                    db.attendanceDao(),
                    db.planningDao()
                ),
                RoomDatabaseTransactions(db),
                clock,
                attendance = dev.daymark.data.local.attendance.RoomAttendanceRepository(
                    db.profileDao(), db.attendanceDao(), db.planningDao(), db.dirtyProjectionDao(),
                    RoomDatabaseTransactions(db),
                    clock
                )
            )
            val command = PlanChange(
                fixtureSchedule(days = weekdays).copy(zoneId = "Pacific/Kiritimati"),
                TrackerCategory.WORK,
                fixturePlace()
            )
            val preview = plans.previewChange(command)
            assertIs<PlanMutationResult.Applied>(
                plans.applyChange(
                    command,
                    preview.reviewToken
                )
            )
            save(
                service,
                TimeOffCommand.Add(TimeOffKind.VACATION, date("2026-11-02"), date("2026-11-02"))
            )
            // New-zone November 2 at 09:00 occurred before old-zone midnight activated this change.
            assertEquals(12, service.observeAvailableBalance().first())
            clock.value = Instant.parse("2026-11-02T00:01:00Z")
            val repo = RoomAttendanceRepository(
                db.profileDao(),
                db.attendanceDao(),
                db.planningDao(),
                db.dirtyProjectionDao(),
                RoomDatabaseTransactions(db),
                clock
            )
            repo.reconcile(clock.now())
            val day = requireNotNull(
                repo.loadDay(
                    DayKey(
                        fixturePlan().key.trackerId,
                        date("2026-11-02")
                    )
                )
            )
            assertFalse(day.plan.isScheduled)
            assertEquals(AttendanceStatus.DAY_OFF, repo.observeDay(day.plan.key).first()?.status)
        }
    }

    @Test
    fun crossing_a_day_boundary_invalidates_a_plan_preview_even_before_tracking_starts() = runTest {
        val folder = Files.createTempDirectory("daymark-pending-preview-")
        val db = openJvmDatabase(folder.resolve("attendance.db").toString())
        val clock = TestClock(at("12:00:00"))
        try {
            seedDatabase(db, trackingStartsAt = Instant.parse("2026-11-10T09:00:00Z"))
            val plans = PlanService(
                db.profileDao(),
                db.planningDao(),
                db.dirtyProjectionDao(),
                dev.daymark.data.local.plan.PlanningSnapshotReader(
                    db.profileDao(),
                    db.attendanceDao(),
                    db.planningDao()
                ),
                RoomDatabaseTransactions(db),
                clock,
                attendance = dev.daymark.data.local.attendance.RoomAttendanceRepository(
                    db.profileDao(), db.attendanceDao(), db.planningDao(), db.dirtyProjectionDao(),
                    RoomDatabaseTransactions(db),
                    clock
                )
            )
            val command = PlanChange(fixtureSchedule(), TrackerCategory.COURSE, fixturePlace())
            val preview = plans.previewChange(command)
            clock.value = Instant.parse("2026-10-28T00:01:00Z")
            assertIs<PlanMutationResult.StalePreview>(
                plans.applyChange(
                    command,
                    preview.reviewToken
                )
            )
            assertEquals(1, db.planningDao().versions(fixturePlan().key.trackerId).size)
        } finally {
            db.close(); folder.toFile().deleteRecursively()
        }
    }

    @Test
    fun retrospective_preview_exposes_before_after_and_preserved_gps_evidence(): Unit =
        kotlinx.coroutines.runBlocking {
            val folder = java.nio.file.Files.createTempDirectory("daymark-preview-evidence-");
            val db = dev.daymark.data.local.openJvmDatabase(folder.resolve("test.db").toString())
            try {
                dev.daymark.data.seedDatabase(db)
                val clock = dev.daymark.attendance.TestClock()
                val repo = dev.daymark.data.local.attendance.RoomAttendanceRepository(
                    db.profileDao(), db.attendanceDao(), db.planningDao(), db.dirtyProjectionDao(),
                    RoomDatabaseTransactions(db),
                    clock
                )
                repo.completeCheck(dev.daymark.data.testCommit())
                val service = TimeOffService(
                    db.profileDao(),
                    db.attendanceDao(),
                    db.planningDao(),
                    db.dirtyProjectionDao(),
                    dev.daymark.data.local.plan.PlanningSnapshotReader(
                        db.profileDao(),
                        db.attendanceDao(),
                        db.planningDao()
                    ),
                    RoomDatabaseTransactions(db),
                    clock,
                    attendance = dev.daymark.data.local.attendance.RoomAttendanceRepository(
                        db.profileDao(),
                        db.attendanceDao(),
                        db.planningDao(),
                        db.dirtyProjectionDao(),
                        RoomDatabaseTransactions(db),
                        clock
                    )
                )
                val command = TimeOffCommand.Add(
                    TimeOffKind.HOLIDAY,
                    dev.daymark.fixtureDate,
                    dev.daymark.fixtureDate
                )
                val preview = service.preview(command)
                val change = preview.changes.single()
                assertEquals(
                    dev.daymark.models.attendance.AttendanceStatus.PRESENT,
                    change.before.status
                )
                assertEquals(
                    dev.daymark.models.attendance.AttendanceStatus.HOLIDAY,
                    change.after.status
                )
                assertEquals(
                    dev.daymark.models.attendance.AttendanceSource.MANUAL_GPS,
                    change.evidence?.source
                )
                assertEquals(
                    PlanMutationResult.Applied,
                    service.apply(command, preview.reviewToken)
                )
                val entry = service.observeEntries().first().single()
                val removal = service.preview(TimeOffCommand.Remove(entry.id)).changes.single()
                assertEquals(
                    dev.daymark.models.attendance.AttendanceStatus.HOLIDAY,
                    removal.before.status
                )
                assertEquals(
                    dev.daymark.models.attendance.AttendanceStatus.PRESENT,
                    removal.after.status
                )
                assertEquals(
                    dev.daymark.models.attendance.AttendanceSource.MANUAL_GPS,
                    removal.evidence?.source
                )
            } finally {
                db.close(); folder.toFile().deleteRecursively()
            }
        }

    @Test
    fun cancelling_a_preview_preserves_an_inflight_automatic_confirmation(): Unit =
        kotlinx.coroutines.runBlocking {
            val folder = java.nio.file.Files.createTempDirectory("daymark-preview-claim-");
            val db = dev.daymark.data.local.openJvmDatabase(folder.resolve("test.db").toString())
            try {
                dev.daymark.data.seedDatabase(db)
                val clock = dev.daymark.attendance.TestClock()
                val repo = dev.daymark.data.local.attendance.RoomAttendanceRepository(
                    db.profileDao(), db.attendanceDao(), db.planningDao(), db.dirtyProjectionDao(),
                    RoomDatabaseTransactions(db),
                    clock
                )
                val attempt = kotlin.uuid.Uuid.random().toString()
                assertTrue(
                    repo.claimAutomatic(
                        dev.daymark.fixturePlan().key,
                        attempt,
                        clock.now()
                    ) is dev.daymark.models.attendance.ClaimResult.Claimed
                )
                TimeOffService(
                    db.profileDao(),
                    db.attendanceDao(),
                    db.planningDao(),
                    db.dirtyProjectionDao(),
                    dev.daymark.data.local.plan.PlanningSnapshotReader(
                        db.profileDao(),
                        db.attendanceDao(),
                        db.planningDao()
                    ),
                    RoomDatabaseTransactions(db),
                    clock,
                    attendance = dev.daymark.data.local.attendance.RoomAttendanceRepository(
                        db.profileDao(),
                        db.attendanceDao(),
                        db.planningDao(),
                        db.dirtyProjectionDao(),
                        RoomDatabaseTransactions(db),
                        clock
                    )
                ).preview(
                    TimeOffCommand.Add(
                        TimeOffKind.HOLIDAY,
                        dev.daymark.fixtureDate,
                        dev.daymark.fixtureDate
                    )
                )
                // No apply: the user dismisses the preview. The already-claimed valid sensor result still commits.
                val request = dev.daymark.data.testCommit()
                    .copy(
                        attemptId = attempt,
                        mode = dev.daymark.models.attendance.CheckMode.AUTOMATIC
                    )
                assertTrue(repo.completeCheck(request) is dev.daymark.models.attendance.CheckOutcome.Recorded)
            } finally {
                db.close(); folder.toFile().deleteRecursively()
            }
        }

}
