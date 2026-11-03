package dev.daymark.data

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import dev.daymark.attendance.RoomAttendanceRepository
import dev.daymark.attendance.TestClock
import dev.daymark.reporting.ReportBinding
import dev.daymark.reporting.RoomReportStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
                val repo = RoomAttendanceRepository(db, clock); repo.completeCheck(testCommit())
                val tracker = db.dao().tracker()!!.id
                db.dao().putTimeOff(
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
                db.dao().insertBalanceAdjustment(
                    BalanceAdjustmentEntity(
                        "adjustment",
                        tracker,
                        -2,
                        clock.now().toString()
                    )
                )
                val store = RoomReportStore(db, clock)
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
                val records = db.dao().days(tracker);
                val leave = db.dao().timeOff(tracker);
                val pending = db.dao().dirtyProjections(tracker)
                val journal = db.dao().batches(tracker)
                db.close()
                val schemaPath = listOf(
                    Path.of("schemas/dev.daymark.data.DaymarkDatabase/1.json"),
                    Path.of("shared/schemas/dev.daymark.data.DaymarkDatabase/1.json")
                ).first { Files.exists(it) }
                val old =
                    Json.parseToJsonElement(Files.readString(schemaPath)).jsonObject.getValue("database").jsonObject
                // Explicitly synthetic prior-schema fixture; no older shipped application/version is claimed.
                BundledSQLiteDriver().open(path).use { connection ->
                    connection.execSQL("ALTER TABLE trackers DROP COLUMN changeRevision")
                    connection.execSQL("ALTER TABLE balance_adjustments DROP COLUMN note")
                    connection.execSQL("UPDATE room_master_table SET identity_hash='${old.getValue("identityHash").jsonPrimitive.content}' WHERE id=42")
                    connection.execSQL("PRAGMA user_version=1")
                }
                db = openJvmDatabase(path)
                assertEquals(records, db.dao().days(tracker)); assertEquals(
                    leave,
                    db.dao().timeOff(tracker)
                )
                assertEquals(pending, db.dao().dirtyProjections(tracker)); assertEquals(
                    journal,
                    db.dao().batches(tracker)
                )
                assertEquals(10, db.dao().tracker()!!.openingBalance); assertEquals(
                    -2,
                    db.dao().balanceAdjustments(tracker).single().delta
                )
                db.close()
                BundledSQLiteDriver().open(path).use { it.execSQL("PRAGMA user_version=3") }
                val attempted = openJvmDatabase(path)
                try {
                    assertFailsWith<Exception> { attempted.dao().tracker() }
                } finally {
                    attempted.close()
                }
                // Restore only the synthetic version marker after the refusal; the current schema/data were never changed.
                BundledSQLiteDriver().open(path).use { it.execSQL("PRAGMA user_version=2") }
                db = openJvmDatabase(path)
                assertEquals(records, db.dao().days(tracker)); assertEquals(
                    leave,
                    db.dao().timeOff(tracker)
                )
                assertEquals(journal, db.dao().batches(tracker))
            } finally {
                db.close(); folder.toFile().deleteRecursively()
            }
        }
}
