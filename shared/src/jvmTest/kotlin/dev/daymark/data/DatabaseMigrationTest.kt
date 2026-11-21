package dev.daymark.data

import androidx.room3.useReaderConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import dev.daymark.data.local.AnnualVacationPolicyEntity
import dev.daymark.data.local.openJvmDatabase
import dev.daymark.fixturePlan
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DatabaseMigrationTest {
    @Test
    fun schema_four_upgrades_without_losing_tracker_or_adjustment_history() = verifyMigration(4)

    @Test
    fun schema_three_upgrades_without_losing_tracker_or_adjustment_history() = verifyMigration(3)

    @Test
    fun schema_one_upgrades_without_losing_tracker_or_adjustment_history() = verifyMigration(1)

    @Test
    fun schema_two_upgrades_without_losing_tracker_or_adjustment_history() = verifyMigration(2)

    private fun verifyMigration(version: Int) = runTest {
        val schemaPath = listOf(
            Path.of("schemas/dev.daymark.data.local.DaymarkDatabase/$version.json"),
            Path.of("shared/schemas/dev.daymark.data.local.DaymarkDatabase/$version.json")
        ).first { Files.exists(it) }
        val schema =
            Json.parseToJsonElement(Files.readString(schemaPath)).jsonObject.getValue("database").jsonObject
        val folder = Files.createTempDirectory("daymark-migration-")
        val path = folder.resolve("attendance.db")
        try {
            BundledSQLiteDriver().open(path.toString()).use { connection ->
                for (element in schema.getValue("entities").jsonArray) {
                    val entity = element.jsonObject
                    val table = entity.getValue("tableName").jsonPrimitive.content
                    connection.execSQL(
                        entity.getValue("createSql").jsonPrimitive.content.replace(
                            "\${TABLE_NAME}",
                            table
                        )
                    )
                    for (index in entity["indices"]?.jsonArray ?: emptyList()) {
                        connection.execSQL(
                            index.jsonObject.getValue("createSql").jsonPrimitive.content.replace(
                                "\${TABLE_NAME}",
                                table
                            )
                        )
                    }
                }
                for (query in schema.getValue("setupQueries").jsonArray) connection.execSQL(query.jsonPrimitive.content)
                val id = fixturePlan().key.trackerId
                connection.execSQL("INSERT INTO trackers(id, displayName, createdAt, trackingStartsAt, openingBalance, automaticEnabled, singleton) VALUES('$id', 'Legacy person', '2026-10-27T08:00:00Z', '2026-10-27T09:00:00Z', 12, 1, 0)")
                connection.execSQL("INSERT INTO balance_adjustments(id, trackerId, delta, createdAt) VALUES('legacy-adjustment', '$id', -2, '2026-10-27T08:00:00Z')")
                connection.execSQL("PRAGMA user_version = $version")
            }
            val db = openJvmDatabase(path.toString())
            try {
                val tracker = requireNotNull(db.profileDao().tracker())
                assertEquals("Legacy person", tracker.displayName)
                assertEquals(12, tracker.openingBalance)
                assertEquals(0L, tracker.changeRevision)
                val adjustment = db.planningDao().balanceAdjustments(tracker.id).single()
                assertEquals(-2, adjustment.delta)
                assertNull(adjustment.note)
                assertNull(adjustment.balanceYear)
                assertEquals(emptyList(), db.planningDao().annualVacationPolicies(tracker.id))
                assertNull(db.placeAddressDao().placeAddress(30.0, 31.0, "en"))
                val migratedVersion = db.useReaderConnection { connection ->
                    connection.usePrepared("PRAGMA user_version") { statement ->
                        check(statement.step())
                        statement.getInt(0)
                    }
                }
                assertEquals(5, migratedVersion)
                val policy = AnnualVacationPolicyEntity(tracker.id, 2027, 14, false)
                val updatedPolicy = policy.copy(annualAllowanceDays = 16, carryOverUnusedDays = true)
                db.planningDao().putAnnualVacationPolicy(policy)
                db.planningDao().putAnnualVacationPolicy(updatedPolicy)
                assertEquals(listOf(updatedPolicy), db.planningDao().annualVacationPolicies(tracker.id))
            } finally {
                db.close()
            }
        } finally {
            folder.toFile().deleteRecursively()
        }
    }
}
