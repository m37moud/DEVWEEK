package dev.daymark.plan

import dev.daymark.attendance.TestClock
import dev.daymark.data.local.attendance.RoomAttendanceRepository
import dev.daymark.data.local.openJvmDatabase
import dev.daymark.data.local.plan.PlanService
import dev.daymark.data.local.plan.RoomPlanReader
import dev.daymark.data.local.plan.change
import dev.daymark.data.seedDatabase
import dev.daymark.fixtureDate
import dev.daymark.fixturePlace
import dev.daymark.fixturePlan
import dev.daymark.fixtureSchedule
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.plan.PlanChange
import dev.daymark.models.plan.PlanMutationResult
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class RoomPlanReaderTest {
    @Test
    fun future_projection_does_not_freeze_snapshots_and_later_planning_reads_the_new_version() =
        runTest {
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
                val change = PlanChange(
                    fixtureSchedule(arrival = LocalTime(10, 0)),
                    TrackerCategory.COURSE,
                    fixturePlace()
                )
                val preview = plans.previewChange(change)
                assertIs<PlanMutationResult.Applied>(
                    plans.applyChange(
                        change,
                        preview.expectedRevision
                    )
                )
                reader.context()
                assertEquals(2L, reader.day(tomorrow).plan.scheduleVersion)
                assertEquals(
                    LocalTime(10, 0),
                    reader.day(tomorrow).plan.arrival.toLocalDateTime(TimeZone.UTC).time
                )
                assertEquals(1L, reader.day(fixtureDate).plan.scheduleVersion)
                assertEquals(0, db.dao().countDay(fixturePlan().key.trackerId, tomorrow.toString()))
            } finally {
                db.close(); folder.toFile().deleteRecursively()
            }
        }
}
