package dev.daymark.app

import androidx.room3.useWriterConnection
import androidx.room3.executeSQL
import dev.daymark.attendance.*
import dev.daymark.data.*
import dev.daymark.reporting.*
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.*

class DataLifecycleTest {
    @Test fun deletion_fences_callbacks_cancels_external_work_and_clears_all_local_rows(): Unit = runBlocking {
        val folder=Files.createTempDirectory("daymark-delete-"); val db=openJvmDatabase(folder.resolve("test.db").toString())
        try {
            seedDatabase(db); val store=RoomReportStore(db,TestClock())
            val binding=ReportBinding(db.dao().tracker()!!.id,store.beginConnection()!!,"a","r",annualTabIds=emptyMap())
            store.bind(binding)
            val events=mutableListOf<String>()
            val service=DataLifecycleService(db,store,stopExternal={
                assertNull(store.activeBinding()); events+="stopped"
            },forgetCredentials={ events+="forgot" },clearWidget={ events+="widget"; true })
            assertEquals(DeletionResult.Deleted,service.deleteLocalData())
            assertEquals(setOf("stopped","forgot","widget"),events.toSet())
            assertNull(db.dao().tracker())
            assertTrue(db.dao().dirtyProjections(binding.trackerId).isEmpty())
            store.bind(binding)
            assertNull(store.activeBinding())
        } finally { db.close(); folder.toFile().deleteRecursively() }
    }
    @Test fun failed_external_cleanup_keeps_local_records_and_reports_failure(): Unit = runBlocking {
        val folder=Files.createTempDirectory("daymark-delete-failure-"); val db=openJvmDatabase(folder.resolve("test.db").toString())
        try {
            seedDatabase(db); val store=RoomReportStore(db,TestClock())
            val service=DataLifecycleService(db,store,stopExternal={},forgetCredentials={ error("native_failed") },clearWidget={ true })
            assertEquals(DeletionResult.Failed,service.deleteLocalData())
            assertNotNull(db.dao().tracker())
        } finally { db.close(); folder.toFile().deleteRecursively() }
    }
    @Test fun database_clear_failure_rolls_back_records_and_does_not_report_deleted(): Unit = runBlocking {
        val folder=Files.createTempDirectory("daymark-delete-transaction-"); val db=openJvmDatabase(folder.resolve("test.db").toString())
        try {
            seedDatabase(db); val clock=TestClock(); val repo=RoomAttendanceRepository(db,clock)
            repo.completeCheck(testCommit())
            val before=repo.loadDay(dev.daymark.fixturePlan().key)
            db.useWriterConnection { connection -> connection.executeSQL("CREATE TRIGGER reject_clear BEFORE DELETE ON attendance BEGIN SELECT RAISE(ABORT, 'test clear fault'); END") }
            val service=DataLifecycleService(db,RoomReportStore(db,clock),stopExternal={},forgetCredentials={},clearWidget={ true })
            assertEquals(DeletionResult.Failed,service.deleteLocalData())
            assertNotNull(db.dao().tracker())
            assertEquals(before,repo.loadDay(dev.daymark.fixturePlan().key))
        } finally { db.close(); folder.toFile().deleteRecursively() }
    }

}
