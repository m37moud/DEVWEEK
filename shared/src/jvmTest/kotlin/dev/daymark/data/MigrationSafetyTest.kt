package dev.daymark.data

import dev.daymark.data.local.RoomDatabaseTransactions

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import dev.daymark.attendance.TestClock
import dev.daymark.data.local.BalanceAdjustmentEntity
import dev.daymark.data.local.DaymarkDatabase
import dev.daymark.data.local.TimeOffEntity
import dev.daymark.data.local.attendance.RoomAttendanceRepository
import dev.daymark.data.local.openJvmDatabase
import dev.daymark.data.local.reporting.RoomReportStore
import dev.daymark.models.reporting.ReportBinding
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class MigrationSafetyTest {
    @Test
    fun prior_schema_fixture_preserves_records_leave_balance_and_uncertain_journal_and_refuses_downgrade(): Unit =
        runBlocking {
            val folder = Files.createTempDirectory("daymark-migration-safety-");
            val path = folder.resolve("test.db").toString()
            var db = openJvmDatabase(path)
            try {
                seedDatabase(db)
                val clock = TestClock();
                val repo = RoomAttendanceRepository(
                    db.profileDao(), db.attendanceDao(), db.planningDao(), db.dirtyProjectionDao(),
                    RoomDatabaseTransactions(db),
                    clock
                ); repo.completeCheck(testCommit())
                val tracker = db.profileDao().tracker()!!.id
                db.planningDao().putTimeOff(
                    TimeOffEntity(
                        "holiday",
                        tracker,
                        "HOLIDAY",
                        "2026-10-28",
                        "2026-10-28",
                        null,
                        clock.now().toString()
                    )
                )
                db.planningDao().insertBalanceAdjustment(
                    BalanceAdjustmentEntity(
                        "adjustment",
                        tracker,
                        -2,
                        clock.now().toString()
                    )
                )
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
                    clock
                )
                val binding = ReportBinding(
                    tracker,
                    store.beginConnection()!!,
                    "account",
                    "report",
                    annualTabIds = mapOf(2026 to 3)
                )
                store.bind(binding)
                val batch = store.buildBatch(
                    binding,
                    100
                )!!; store.journalBeforeDispatch(batch); store.markDispatched(batch)
                val records = db.attendanceDao().days(tracker);
                val leave = db.planningDao().timeOff(tracker);
                val pending = db.dirtyProjectionDao().dirtyProjections(tracker)
                val journal = db.reportDao().batches(tracker)
                db.close()
                val schemaPath = listOf(
                    Path.of("schemas/dev.daymark.data.local.DaymarkDatabase/1.json"),
                    Path.of("shared/schemas/dev.daymark.data.local.DaymarkDatabase/1.json")
                ).first { Files.exists(it) }
                val old =
                    Json.parseToJsonElement(Files.readString(schemaPath)).jsonObject.getValue("database").jsonObject
                // Explicitly synthetic prior-schema fixture; no older shipped application/version is claimed.
                BundledSQLiteDriver().open(path).use { connection ->
                    connection.execSQL("ALTER TABLE trackers DROP COLUMN changeRevision")
                    connection.execSQL("ALTER TABLE balance_adjustments DROP COLUMN note")
                    connection.execSQL("DROP TABLE place_addresses")
                    connection.execSQL("UPDATE room_master_table SET identity_hash='${old.getValue("identityHash").jsonPrimitive.content}' WHERE id=42")
                    connection.execSQL("PRAGMA user_version=1")
                }
                db = openJvmDatabase(path)
                assertEquals(records, db.attendanceDao().days(tracker)); assertEquals(
                    leave,
                    db.planningDao().timeOff(tracker)
                )
                assertEquals(
                    pending,
                    db.dirtyProjectionDao().dirtyProjections(tracker)
                ); assertEquals(
                    journal,
                    db.reportDao().batches(tracker)
                )
                assertEquals(10, db.profileDao().tracker()!!.openingBalance); assertEquals(
                    -2,
                    db.planningDao().balanceAdjustments(tracker).single().delta
                )
                db.close()
                BundledSQLiteDriver().open(path).use { it.execSQL("PRAGMA user_version=4") }
                val attempted = openJvmDatabase(path)
                try {
                    assertFailsWith<Exception> { attempted.profileDao().tracker() }
                } finally {
                    attempted.close()
                }
                // Restore only the synthetic version marker after the refusal; the current schema/data were never changed.
                BundledSQLiteDriver().open(path).use { it.execSQL("PRAGMA user_version=3") }
                db = openJvmDatabase(path)
                assertEquals(records, db.attendanceDao().days(tracker)); assertEquals(
                    leave,
                    db.planningDao().timeOff(tracker)
                )
                assertEquals(journal, db.reportDao().batches(tracker))
            } finally {
                db.close(); folder.toFile().deleteRecursively()
            }
        }
}
