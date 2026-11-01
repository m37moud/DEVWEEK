package dev.daymark.reporting

import androidx.room3.withWriteTransaction
import dev.daymark.*
import dev.daymark.attendance.TestClock
import dev.daymark.data.*
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.*

class ReportJournalTest {
    @Test fun dispatched_batch_survives_reopen_and_obsolete_ack_cannot_clean_new_generation() = runBlocking {
        val folder = Files.createTempDirectory("daymark-journal-")
        val file = folder.resolve("test.db").toString()
        var db = openJvmDatabase(file)
        try {
            seedDatabase(db)
            var store = RoomReportStore(db,TestClock())
            val tracker = db.dao().tracker()!!.id
            val generation = store.beginConnection()!!
            val a = ReportBinding(tracker,generation,"account-a","report-a",annualTabIds = mapOf(2026 to 3),managedTabIds = mapOf("Overview" to 1,"Time off" to 2))
            store.bind(a)
            val batch = store.buildBatch(a,100)!!
            store.journalBeforeDispatch(batch); store.markDispatched(batch)
            db.close(); db = openJvmDatabase(file); store = RoomReportStore(db,TestClock())
            assertEquals("DISPATCHED",db.dao().batches(tracker).single().status)
            assertNotNull(store.buildBatch(a,100))
            val b = a.copy(connectionGeneration = store.beginConnection()!!,accountReference = "account-b",spreadsheetId = "report-b")
            store.bind(b)
            store.acknowledge(batch)
            assertEquals(b,store.activeBinding())
            assertNotNull(store.buildBatch(b,100))
            store.invalidateBinding()
            store.bind(b) // Stale interactive callback must not restore a disconnected connection.
            assertNull(store.activeBinding())
        } finally { db.close(); folder.toFile().deleteRecursively() }
    }
    @Test fun correction_during_upload_remains_dirty_and_large_projection_splits_complete_rows() = runBlocking {
        val folder = Files.createTempDirectory("daymark-journal-revision-"); val db = openJvmDatabase(folder.resolve("test.db").toString())
        try {
            seedDatabase(db)
            val store = RoomReportStore(db,TestClock())
            val a = ReportBinding(db.dao().tracker()!!.id,store.beginConnection()!!,"a","r",annualTabIds=mapOf(2026 to 3))
            store.bind(a)
            val first = store.buildBatch(a,1)!!
            assertEquals(1,first.projections.sumOf { p -> p.ranges.sumOf { it.cells.size } })
            store.journalBeforeDispatch(first); store.markDispatched(first)
            db.withWriteTransaction { first.projections.forEach { db.dao().markDirty(a.trackerId,it.key.storageKey()) } }
            store.acknowledge(first)
            assertTrue(db.dao().dirtyProjections(a.trackerId).any { it.revision > it.acknowledgedRevision })
            var count = 0
            while (true) {
                val next = store.buildBatch(a,1) ?: break
                assertEquals(1,next.projections.sumOf { p -> p.ranges.sumOf { it.cells.size } })
                store.journalBeforeDispatch(next); store.markDispatched(next); store.acknowledge(next)
                check(++count < 100)
            }
            assertTrue(count > 2)
        } finally { db.close(); folder.toFile().deleteRecursively() }
    }
    @Test fun removed_time_off_clears_capacity_reserved_before_an_ambiguous_dispatch(): Unit = runBlocking {
        val folder=Files.createTempDirectory("daymark-report-clear-"); val db=openJvmDatabase(folder.resolve("test.db").toString())
        try {
            seedDatabase(db); val store=RoomReportStore(db,TestClock())
            val binding=ReportBinding(db.dao().tracker()!!.id,store.beginConnection()!!,"a","r",annualTabIds=emptyMap())
            db.withWriteTransaction { repeat(3) { index ->
                db.dao().putTimeOff(TimeOffEntity("off-$index",binding.trackerId,"HOLIDAY","2026-10-27","2026-10-27",null,at("09:00:00").toString()))
            } }
            store.bind(binding)
            val batch=store.buildBatch(binding,100)!!
            store.journalBeforeDispatch(batch); store.markDispatched(batch); store.markAmbiguous(batch)
            db.withWriteTransaction { repeat(3) { db.dao().deleteTimeOff("off-$it") }; db.dao().markDirty(binding.trackerId,"TIME_OFF") }
            val cleared=store.localProjections(binding).single { it.key == ProjectionKey.TimeOff }.ranges.single()
            assertEquals(3,cleared.cells.size)
            assertTrue(cleared.cells.flatten().all { it.content.isEmpty() })
        } finally { db.close(); folder.toFile().deleteRecursively() }
    }

    @Test fun oversized_complete_row_remains_local_and_dirty(): Unit = runBlocking {
        val folder=Files.createTempDirectory("daymark-report-cap-"); val db=openJvmDatabase(folder.resolve("test.db").toString())
        try {
            seedDatabase(db); val clock=TestClock(); val repo=dev.daymark.attendance.RoomAttendanceRepository(db,clock)
            val note="x".repeat(2_000_000)
            repo.correct(dev.daymark.attendance.Correction(fixturePlan().key,dev.daymark.attendance.AttendanceStatus.ABSENT,null,note))
            val store=RoomReportStore(db,clock)
            val binding=ReportBinding(db.dao().tracker()!!.id,store.beginConnection()!!,"a","r",annualTabIds=mapOf(2026 to 3))
            store.bind(binding)
            assertEquals("projection_too_large",assertFailsWith<ReportAccessException> { store.buildBatch(binding,100) }.errorKey)
            assertEquals(note,repo.loadDay(fixturePlan().key)!!.evidence!!.note)
            assertTrue(db.dao().dirtyProjections(binding.trackerId).any { it.projectionKey.startsWith("DAY:") && it.revision>it.acknowledgedRevision })
        } finally { db.close(); folder.toFile().deleteRecursively() }
    }

    @Test fun a_year_of_history_drains_in_bounded_complete_row_batches(): Unit = runBlocking {
        val folder=Files.createTempDirectory("daymark-report-history-"); val db=openJvmDatabase(folder.resolve("test.db").toString())
        try {
            val start=kotlinx.datetime.LocalDate(2025,10,27)
            val begins=kotlin.time.Instant.parse("2025-10-27T09:00:00Z")
            seedDatabase(db,createdAt=begins,trackingStartsAt=begins)
            val tracker=db.dao().tracker()!!.id
            db.dao().putVersion(PlanVersionEntity.fromSnapshot(tracker,1,start,begins,dev.daymark.attendance.TrackerCategory.WORK,fixtureSchedule(),fixturePlace(),"Place"))
            val clock=TestClock(at("09:06:00")); val repo=dev.daymark.attendance.RoomAttendanceRepository(db,clock)
            repo.reconcile(clock.now())
            val store=RoomReportStore(db,clock)
            val binding=ReportBinding(tracker,store.beginConnection()!!,"a","r",annualTabIds=mapOf(2025 to 3,2026 to 4))
            store.bind(binding)
            var batches=0; var publishedRows=0
            while (true) {
                val batch=store.buildBatch(binding,37) ?: break
                val rows=batch.projections.sumOf { p -> p.ranges.sumOf { it.cells.size } }
                assertTrue(rows in 1..37)
                assertTrue(kotlinx.serialization.json.Json.encodeToString(ReportBatch.serializer(),batch).encodeToByteArray().size < 2_000_000)
                publishedRows+=rows
                store.journalBeforeDispatch(batch); store.markDispatched(batch); store.acknowledge(batch)
                check(++batches<30)
            }
            assertTrue(batches>=10)
            assertTrue(publishedRows>=365)
            assertTrue(db.dao().dirtyProjections(tracker).all { it.acknowledgedRevision==it.revision })
        } finally { db.close(); folder.toFile().deleteRecursively() }
    }

}
