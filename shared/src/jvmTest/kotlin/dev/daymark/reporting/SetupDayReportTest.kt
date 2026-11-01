package dev.daymark.reporting

import dev.daymark.*
import dev.daymark.attendance.*
import dev.daymark.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.*
import dev.daymark.plan.*
import java.nio.file.Files
import kotlin.test.*

class SetupDayReportTest {
    @Test fun explicit_setup_day_corrections_match_history_annual_and_overview_totals(): Unit = runBlocking {
        val folder=Files.createTempDirectory("daymark-setup-report-"); val db=openJvmDatabase(folder.resolve("test.db").toString())
        try {
            seedDatabase(db,createdAt=at("09:20:00"),trackingStartsAt=kotlin.time.Instant.parse("2026-10-28T09:00:00Z"))
            val clock=TestClock(at("09:20:00")); val repo=RoomAttendanceRepository(db,clock); val store=RoomReportStore(db,clock)
            val tracker=db.dao().tracker()!!.id
            val binding=ReportBinding(tracker,store.beginConnection()!!,"a","r",annualTabIds=mapOf(2026 to 3))
            store.bind(binding)
            for (status in listOf(AttendanceStatus.PRESENT,AttendanceStatus.LATE,AttendanceStatus.ABSENT)) {
                repo.correct(Correction(fixturePlan().key,status,if (status==AttendanceStatus.ABSENT) null else LocalTime(9,10),null))
                val history=repo.observeMonth(2026,10).first()
                assertEquals(status,history.records.single().outcome.status)
                val projections=store.localProjections(binding)
                assertEquals(status.name,projections.single { it.key is ProjectionKey.Day }.ranges.single().cells.single()[5].content)
                val overview=projections.single { it.key==ProjectionKey.Overview }.ranges.single().cells.associate { it[0].content to it[2].content }
                assertEquals(history.present,overview.getValue("present").toInt())
                assertEquals(history.late,overview.getValue("late").toInt())
                assertEquals(history.absent,overview.getValue("absent").toInt())
            }
        } finally { db.close(); folder.toFile().deleteRecursively() }
    }
    @Test fun westward_zone_cutover_does_not_hide_previously_recorded_dates_from_totals_or_week(): Unit = runBlocking {
        val folder=Files.createTempDirectory("daymark-zone-report-"); val db=openJvmDatabase(folder.resolve("test.db").toString())
        try {
            val start=LocalDate(2026,10,20)
            val begins=start.atTime(9,0).toInstant(TimeZone.of("Pacific/Kiritimati"))
            seedDatabase(db,createdAt=begins-kotlin.time.Duration.parse("1h"),trackingStartsAt=begins)
            val tracker=db.dao().tracker()!!.id
            val schedule=fixtureSchedule(zoneId="Pacific/Kiritimati")
            db.dao().putVersion(PlanVersionEntity.fromSnapshot(tracker,1,start,begins,TrackerCategory.WORK,schedule,fixturePlace(),"Place"))
            val clock=TestClock(at("09:00:00")); val repo=RoomAttendanceRepository(db,clock)
            repo.reconcile(clock.now())
            repo.correct(Correction(fixturePlan().key,AttendanceStatus.PRESENT,LocalTime(9,0),null))
            val plans=PlanService(db,clock)
            val change=PlanChange(schedule.copy(zoneId="Etc/GMT+12"),TrackerCategory.WORK,fixturePlace())
            val preview=plans.previewChange(change)
            assertEquals(PlanMutationResult.Applied,plans.applyChange(change,preview.expectedRevision))
            clock.value=at("10:00:01"); repo.reconcile(clock.now())
            val store=RoomReportStore(db,clock)
            val binding=ReportBinding(tracker,store.beginConnection()!!,"a","r",annualTabIds=mapOf(2026 to 3)); store.bind(binding)
            val history=repo.observeMonth(2026,10).first()
            assertEquals(1,history.present)
            val overview=store.localProjections(binding).single { it.key==ProjectionKey.Overview }.ranges.single().cells.associate { it[0].content to it[2].content }
            assertEquals(1,overview.getValue("present").toInt())
            val dashboard=dev.daymark.ui.TodayDashboardReader(db,clock).observe().first { it.ready }
            assertEquals(1,dashboard.present)
            assertFalse(dashboard.week.single { it.date==fixtureDate }.planned)
        } finally { db.close(); folder.toFile().deleteRecursively() }
    }

}
