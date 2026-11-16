package dev.daymark.history

import dev.daymark.at
import dev.daymark.attendance.TestClock
import dev.daymark.data.local.DaymarkDatabase
import dev.daymark.data.local.RoomDatabaseTransactions
import dev.daymark.data.local.TimeOffEntity
import dev.daymark.data.local.attendance.RoomAttendanceRepository
import dev.daymark.data.local.dashboard.TodayDashboardReader
import dev.daymark.data.local.encodeSchedule
import dev.daymark.data.local.openJvmDatabase
import dev.daymark.data.local.plan.PlanningSnapshotReader
import dev.daymark.data.seedDatabase
import dev.daymark.fixtureDate
import dev.daymark.fixturePlan
import dev.daymark.fixtureSchedule
import dev.daymark.models.TodayDashboard
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.models.attendance.Correction
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class TodayKpiParityTest {
    @Test
    fun dashboard_matches_history_including_late_minutes_and_excluding_future_leave_without_writes() =
        runBlocking {
            val folder = Files.createTempDirectory("today-kpi-parity-")
            val db = openJvmDatabase(folder.resolve("test.db").toString())
            try {
                seedDatabase(db)
                val clock = TestClock(at("12:00:00"))
                val repository = attendance(db, clock)
                repository.correct(
                    Correction(
                        fixturePlan().key,
                        AttendanceStatus.LATE,
                        LocalTime(9, 10),
                        null
                    )
                )
                clock.value = Instant.parse("2026-10-29T12:00:00Z")
                for ((id, kind, date) in listOf(
                    Triple("elapsed-leave", "VACATION", "2026-10-28"),
                    Triple("holiday", "HOLIDAY", "2026-10-29"),
                    Triple("future-leave", "VACATION", "2026-10-30")
                )) {
                    db.planningDao().putTimeOff(
                        TimeOffEntity(
                            id, fixturePlan().key.trackerId,
                            kind, date, date, null, clock.now().toString()
                        )
                    )
                }
                val rows = db.attendanceDao().days(fixturePlan().key.trackerId)
                val dashboard = reader(db, clock).observe().first()
                val range =
                    repository.observeRange(LocalDate(2026, 10, 1), LocalDate(2026, 10, 31)).first()
                assertEquals(historyMetrics(range.records, range.today), dashboard.metrics)
                assertEquals(1, dashboard.metrics.attendance)
                assertEquals(1, dashboard.metrics.late)
                assertEquals(10L, dashboard.metrics.lateDuration.inWholeMinutes)
                assertEquals(1, dashboard.metrics.leaveRest)
                assertEquals(1, dashboard.metrics.holidays)
                assertEquals(rows, db.attendanceDao().days(fixturePlan().key.trackerId))
            } finally {
                db.close(); folder.toFile().deleteRecursively()
            }
        }

    @Test
    fun open_dashboard_refreshes_at_grace_deadline_without_database_mutation() = runBlocking {
        val folder = Files.createTempDirectory("today-kpi-deadline-")
        val db = openJvmDatabase(folder.resolve("test.db").toString())
        val clock = TestClock(at("09:04:59.900"))
        var job: kotlinx.coroutines.Job? = null
        try {
            seedDatabase(db)
            val events = Channel<TodayDashboard>(Channel.UNLIMITED)
            job = launch(Dispatchers.Default) {
                reader(db, clock).observe().collect { events.send(it) }
            }
            assertEquals(0, withTimeout(5_000) { events.receive() }.metrics.absent)
            clock.value = at("09:05:00.001")
            assertEquals(1, withTimeout(5_000) { events.receive() }.metrics.absent)
            assertTrue(db.attendanceDao().days(fixturePlan().key.trackerId).isEmpty())
        } finally {
            job?.cancelAndJoin(); db.close(); folder.toFile().deleteRecursively()
        }
    }

    @Test
    fun open_rest_day_dashboard_rolls_month_at_midnight_without_database_mutation() = runBlocking {
        val folder = Files.createTempDirectory("today-kpi-midnight-")
        val db = openJvmDatabase(folder.resolve("test.db").toString())
        val clock = TestClock(Instant.parse("2026-10-31T23:59:59.900Z"))
        var job: kotlinx.coroutines.Job? = null
        try {
            seedDatabase(db)
            val version = db.planningDao().versions(fixturePlan().key.trackerId).single()
            db.planningDao().putVersion(
                version.copy(
                    scheduleJson = encodeSchedule(
                        fixtureSchedule(
                            days = setOf(
                                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
                            )
                        )
                    )
                )
            )
            val events = Channel<TodayDashboard>(Channel.UNLIMITED)
            job = launch(Dispatchers.Default) {
                reader(db, clock).observe().collect { events.send(it) }
            }
            val october = withTimeout(5_000) { events.receive() }
            assertEquals(LocalDate(2026, 10, 31), october.date)
            assertEquals(0, october.metrics.leaveRest)
            clock.value = Instant.parse("2026-11-01T00:00:00Z")
            val november = withTimeout(5_000) { events.receive() }
            assertEquals(LocalDate(2026, 11, 1), november.date)
            assertEquals(0, november.metrics.leaveRest)
            assertEquals(0, november.metrics.absent)
            assertTrue(db.attendanceDao().days(fixturePlan().key.trackerId).isEmpty())
        } finally {
            job?.cancelAndJoin(); db.close(); folder.toFile().deleteRecursively()
        }
    }

    private fun reader(db: DaymarkDatabase, clock: TestClock) = TodayDashboardReader(
        db.profileDao(),
        PlanningSnapshotReader(db.profileDao(), db.attendanceDao(), db.planningDao()),
        RoomDatabaseTransactions(db),
        clock
    )

    private fun attendance(db: DaymarkDatabase, clock: TestClock) = RoomAttendanceRepository(
        db.profileDao(),
        db.attendanceDao(),
        db.planningDao(),
        db.dirtyProjectionDao(),
        RoomDatabaseTransactions(db),
        clock
    )
}
