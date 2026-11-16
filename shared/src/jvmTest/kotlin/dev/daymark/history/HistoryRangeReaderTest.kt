package dev.daymark.history

import dev.daymark.at
import dev.daymark.attendance.TestClock
import dev.daymark.data.local.RoomDatabaseTransactions
import dev.daymark.data.local.TimeOffEntity
import dev.daymark.data.local.attendance.RoomAttendanceRepository
import dev.daymark.data.local.encodeSchedule
import dev.daymark.data.local.openJvmDatabase
import dev.daymark.data.seedDatabase
import dev.daymark.fixtureDate
import dev.daymark.fixturePlan
import dev.daymark.fixtureSchedule
import dev.daymark.models.attendance.AttendanceStatus
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

class HistoryRangeReaderTest {
    @Test
    fun projects_calendar_gaps_and_future_leave_without_persisting_attendance() = runBlocking {
        val directory = Files.createTempDirectory("daymark-range-")
        val db = openJvmDatabase(directory.resolve("attendance.db").toString())
        try {
            seedDatabase(db)
            val trackerId = fixturePlan().key.trackerId
            val version = db.planningDao().versions(trackerId).single()
            db.planningDao().putVersion(
                version.copy(
                    scheduleJson = encodeSchedule(
                        fixtureSchedule(days = setOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY))
                    )
                )
            )
            db.planningDao().putTimeOff(
                TimeOffEntity(
                    "planned", trackerId, "VACATION",
                    "2026-10-28", "2026-10-28", null, at("10:00:00").toString()
                )
            )
            val repo = RoomAttendanceRepository(
                db.profileDao(), db.attendanceDao(), db.planningDao(),
                db.dirtyProjectionDao(), RoomDatabaseTransactions(db), TestClock(at("12:00:00"))
            )
            val range = repo.observeRange(LocalDate(2026, 10, 1), LocalDate(2026, 10, 29)).first()
            assertEquals(fixtureDate, range.firstDate)
            assertEquals(
                listOf(fixtureDate, LocalDate(2026, 10, 28), LocalDate(2026, 10, 29)),
                range.records.map { it.plan.key.date })
            assertEquals(
                listOf(
                    AttendanceStatus.ABSENT,
                    AttendanceStatus.VACATION,
                    AttendanceStatus.DAY_OFF
                ),
                range.records.map { it.outcome.status })
            assertEquals(0, historyMetrics(range.records, range.today).leaveRest)
            assertEquals(0, historyMetrics(range.records, range.today).holidays)
            assertTrue(db.attendanceDao().days(trackerId).isEmpty())
            assertEquals(1, repo.observeRange(fixtureDate, fixtureDate).first().records.size)
            assertTrue(
                repo.observeRange(LocalDate(2026, 10, 1), LocalDate(2026, 10, 26))
                    .first().records.isEmpty()
            )
        } finally {
            db.close()
            directory.toFile().deleteRecursively()
        }
    }
}
