package dev.daymark.app

import dev.daymark.data.local.RoomDatabaseTransactions

import androidx.room3.executeSQL
import androidx.room3.useWriterConnection
import dev.daymark.attendance.TestClock
import dev.daymark.data.local.AnnualVacationPolicyEntity
import dev.daymark.data.local.application.DataLifecycleService
import dev.daymark.data.local.attendance.RoomAttendanceRepository
import dev.daymark.data.local.openJvmDatabase
import dev.daymark.data.local.reporting.RoomReportStore
import dev.daymark.data.seedDatabase
import dev.daymark.data.testCommit
import dev.daymark.models.DeletionResult
import dev.daymark.models.reporting.ReportBinding
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DataLifecycleTest {
    @Test
    fun deletion_fences_callbacks_cancels_external_work_and_clears_all_local_rows(): Unit =
        runBlocking {
            val folder = Files.createTempDirectory("daymark-delete-");
            val db = openJvmDatabase(folder.resolve("test.db").toString())
            try {
                seedDatabase(db);
                val store = RoomReportStore(
                    db.profileDao(),
                    db.attendanceDao(),
                    db.dirtyProjectionDao(),
                    db.reportDao(),
                    dev.daymark.data.local.plan.PlanningSnapshotReader(
                        db.profileDao(),
                        db.attendanceDao(),
                        db.planningDao()
                    ),
                    RoomDatabaseTransactions(db),
                    TestClock()
                )
                val binding = ReportBinding(
                    db.profileDao().tracker()!!.id,
                    store.beginConnection()!!,
                    "a",
                    "r",
                    annualTabIds = emptyMap()
                )
                db.planningDao().putAnnualVacationPolicy(
                    AnnualVacationPolicyEntity(binding.trackerId, 2027, 14, false)
                )
                store.bind(binding)
                val events = mutableListOf<String>()
                val service = DataLifecycleService(
                    db.profileDao(), db.localDataDeletionDao(),
                    RoomDatabaseTransactions(db),
                    store,
                    stopExternal = {
                        assertNull(store.activeBinding()); events += "stopped"
                    },
                    forgetCredentials = { events += "forgot" },
                    clearWidget = { events += "widget"; true },
                    settings = dev.daymark.data.local.settings.SettingsRepository(
                        db.profileDao(), db.dirtyProjectionDao(),
                        RoomDatabaseTransactions(db)
                    )
                )
                assertEquals(DeletionResult.Deleted, service.deleteLocalData())
                assertEquals(setOf("stopped", "forgot", "widget"), events.toSet())
                assertNull(db.profileDao().tracker())
                assertTrue(db.dirtyProjectionDao().dirtyProjections(binding.trackerId).isEmpty())
                assertTrue(db.planningDao().annualVacationPolicies(binding.trackerId).isEmpty())
                store.bind(binding)
                assertNull(store.activeBinding())
            } finally {
                db.close(); folder.toFile().deleteRecursively()
            }
        }

    @Test
    fun failed_external_cleanup_keeps_local_records_and_reports_failure(): Unit = runBlocking {
        val folder = Files.createTempDirectory("daymark-delete-failure-");
        val db = openJvmDatabase(folder.resolve("test.db").toString())
        try {
            seedDatabase(db);
            val store = RoomReportStore(
                db.profileDao(),
                db.attendanceDao(),
                db.dirtyProjectionDao(),
                db.reportDao(),
                dev.daymark.data.local.plan.PlanningSnapshotReader(
                    db.profileDao(),
                    db.attendanceDao(),
                    db.planningDao()
                ),
                RoomDatabaseTransactions(db),
                TestClock()
            )
            val service = DataLifecycleService(
                db.profileDao(), db.localDataDeletionDao(),
                RoomDatabaseTransactions(db),
                store,
                stopExternal = {},
                forgetCredentials = { error("native_failed") },
                clearWidget = { true },
                settings = dev.daymark.data.local.settings.SettingsRepository(
                    db.profileDao(), db.dirtyProjectionDao(),
                    RoomDatabaseTransactions(db)
                )
            )
            assertEquals(DeletionResult.Failed, service.deleteLocalData())
            assertNotNull(db.profileDao().tracker())
        } finally {
            db.close(); folder.toFile().deleteRecursively()
        }
    }

    @Test
    fun database_clear_failure_rolls_back_records_and_does_not_report_deleted(): Unit =
        runBlocking {
            val folder = Files.createTempDirectory("daymark-delete-transaction-");
            val db = openJvmDatabase(folder.resolve("test.db").toString())
            try {
                seedDatabase(db);
                val clock = TestClock();
                val repo = RoomAttendanceRepository(
                    db.profileDao(),
                    db.attendanceDao(),
                    db.planningDao(),
                    db.dirtyProjectionDao(),
                    RoomDatabaseTransactions(db),
                    clock
                )
                repo.completeCheck(testCommit())
                val before = repo.loadDay(dev.daymark.fixturePlan().key)
                db.useWriterConnection { connection -> connection.executeSQL("CREATE TRIGGER reject_clear BEFORE DELETE ON attendance BEGIN SELECT RAISE(ABORT, 'test clear fault'); END") }
                val service = DataLifecycleService(
                    db.profileDao(), db.localDataDeletionDao(),
                    RoomDatabaseTransactions(db),
                    RoomReportStore(
                        db.profileDao(),
                        db.attendanceDao(),
                        db.dirtyProjectionDao(),
                        db.reportDao(),
                        dev.daymark.data.local.plan.PlanningSnapshotReader(
                            db.profileDao(),
                            db.attendanceDao(),
                            db.planningDao()
                        ),
                        RoomDatabaseTransactions(db),
                        clock
                    ),
                    stopExternal = {},
                    forgetCredentials = {},
                    clearWidget = { true },
                    settings = dev.daymark.data.local.settings.SettingsRepository(
                        db.profileDao(), db.dirtyProjectionDao(),
                        RoomDatabaseTransactions(db)
                    )
                )
                assertEquals(DeletionResult.Failed, service.deleteLocalData())
                assertNotNull(db.profileDao().tracker())
                assertEquals(before, repo.loadDay(dev.daymark.fixturePlan().key))
            } finally {
                db.close(); folder.toFile().deleteRecursively()
            }
        }

}
