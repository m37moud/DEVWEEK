package dev.daymark.data

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import dev.daymark.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class DatabaseMigrationTest {
    @Test fun schema_one_upgrades_without_losing_tracker_or_adjustment_history() = runTest {
        val schemaPath = listOf(Path.of("schemas/dev.daymark.data.DaymarkDatabase/1.json"),
            Path.of("shared/schemas/dev.daymark.data.DaymarkDatabase/1.json")).first { Files.exists(it) }
        val schema = Json.parseToJsonElement(Files.readString(schemaPath)).jsonObject.getValue("database").jsonObject
        val folder = Files.createTempDirectory("daymark-migration-")
        val path = folder.resolve("attendance.db")
        try {
            BundledSQLiteDriver().open(path.toString()).use { connection ->
                for (element in schema.getValue("entities").jsonArray) {
                    val entity = element.jsonObject
                    val table = entity.getValue("tableName").jsonPrimitive.content
                    connection.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                    for (index in entity["indices"]?.jsonArray ?: emptyList()) {
                        connection.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                    }
                }
                for (query in schema.getValue("setupQueries").jsonArray) connection.execSQL(query.jsonPrimitive.content)
                val id = fixturePlan().key.trackerId
                connection.execSQL("INSERT INTO trackers(id, displayName, createdAt, trackingStartsAt, openingBalance, automaticEnabled, singleton) VALUES('$id', 'Legacy person', '2026-10-27T08:00:00Z', '2026-10-27T09:00:00Z', 12, 1, 0)")
                connection.execSQL("INSERT INTO balance_adjustments(id, trackerId, delta, createdAt) VALUES('legacy-adjustment', '$id', -2, '2026-10-27T08:00:00Z')")
                connection.execSQL("PRAGMA user_version = 1")
            }
            val db = openJvmDatabase(path.toString())
            try {
                val tracker = requireNotNull(db.dao().tracker())
                assertEquals("Legacy person", tracker.displayName)
                assertEquals(12, tracker.openingBalance)
                assertEquals(0L, tracker.changeRevision)
                val adjustment = db.dao().balanceAdjustments(tracker.id).single()
                assertEquals(-2, adjustment.delta)
                assertNull(adjustment.note)
            } finally { db.close() }
        } finally { folder.toFile().deleteRecursively() }
    }
}
