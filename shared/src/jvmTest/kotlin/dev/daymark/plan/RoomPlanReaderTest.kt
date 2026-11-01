package dev.daymark.plan

import dev.daymark.*
import dev.daymark.attendance.*
import dev.daymark.data.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.*
import java.nio.file.Files
import kotlin.test.*

class RoomPlanReaderTest {
    @Test fun future_projection_does_not_freeze_snapshots_and_later_planning_reads_the_new_version() = runTest {
        val folder = Files.createTempDirectory("daymark-plan-reader-")
        val db = openJvmDatabase(folder.resolve("attendance.db").toString())
        val clock = TestClock()
        try {
            seedDatabase(db)
            val repo = RoomAttendanceRepository(db, clock)
            repo.reconcile(clock.now())
            val reader = RoomPlanReader(db, clock)
            reader.context()
            val tomorrow = fixtureDate.plus(1, DateTimeUnit.DAY)
            assertEquals(1L, reader.day(tomorrow).plan.scheduleVersion)
            assertEquals(0, db.dao().countDay(fixturePlan().key.trackerId, tomorrow.toString()))
            val plans = PlanService(db, clock)
            val change = PlanChange(fixtureSchedule(arrival = LocalTime(10, 0)), TrackerCategory.COURSE, fixturePlace())
            val preview = plans.previewChange(change)
            assertIs<PlanMutationResult.Applied>(plans.applyChange(change, preview.expectedRevision))
            reader.context()
            assertEquals(2L, reader.day(tomorrow).plan.scheduleVersion)
            assertEquals(LocalTime(10, 0), reader.day(tomorrow).plan.arrival.toLocalDateTime(TimeZone.UTC).time)
            assertEquals(1L, reader.day(fixtureDate).plan.scheduleVersion)
            assertEquals(0, db.dao().countDay(fixturePlan().key.trackerId, tomorrow.toString()))
        } finally { db.close(); folder.toFile().deleteRecursively() }
    }
}
