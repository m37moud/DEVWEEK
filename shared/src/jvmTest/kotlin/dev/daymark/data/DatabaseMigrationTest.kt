package dev.daymark.data

import androidx.room3.useReaderConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import dev.daymark.data.local.createTrackerSingletonTriggers
import dev.daymark.data.local.AnnualVacationPolicyEntity
import dev.daymark.data.local.openJvmDatabase
import dev.daymark.fixturePlan
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @Test
    fun schema_five_upgrades_without_losing_tracker_or_adjustment_history() = verifyMigration(5)

    @Test
    fun schema_six_keeps_consumed_claims_and_preserves_their_evidence() = verifyMigration(6)

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
                if (version == 6) {
                    connection.createTrackerSingletonTriggers()
                    connection.execSQL(
                        """INSERT INTO attendance(trackerId, date, category, arrival, deadline, zoneId, scheduleVersion,
                            placeLatitude, placeLongitude, placeRadiusMetres, placeVersion, isScheduled, updatedAt,
                            automaticState, automaticAttemptId, revision, userEditRevision, cachedStatus, cachedSource,
                            evidenceStatus, evidenceSource, confirmedAt, evidenceUpdatedAt)
                            VALUES('$id', '2026-10-27', 'WORK', '2026-10-27T09:00:00Z', '2026-10-27T09:05:00Z',
                            'UTC', 1, 0.0, 0.0, 100, 1, 1, '2026-10-27T09:00:00Z', 'FINISHED', 'legacy', 4, 0,
                            'PRESENT', 'AUTOMATIC_GPS', 'PRESENT', 'AUTOMATIC_GPS',
                            '2026-10-27T09:00:00Z', '2026-10-27T09:00:00Z')"""
                    )
                }
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
                assertEquals(7, migratedVersion)
                if (version == 6) {
                    val legacy = db.attendanceDao().day(tracker.id, "2026-10-27")!!
                    assertEquals(1, legacy.automaticAttempts)
                    assertEquals(1, legacy.automaticAttemptLimit)
                    assertEquals("legacy", legacy.automaticAttemptId)
                    assertEquals("PRESENT", legacy.evidenceStatus)
                    kotlin.test.assertFalse(legacy.automaticResultPending)
                    assertNull(legacy.automaticRetryAt)
                }
                val policy = AnnualVacationPolicyEntity(tracker.id, 2027, 14, false)
                val updatedPolicy = policy.copy(annualAllowanceDays = 16, carryOverUnusedDays = true)
                db.planningDao().putAnnualVacationPolicy(policy)
                db.planningDao().putAnnualVacationPolicy(updatedPolicy)
                assertEquals(listOf(updatedPolicy), db.planningDao().annualVacationPolicies(tracker.id))
                assertFailsWith<SQLiteException> {
                    db.profileDao()
                        .insertTracker(tracker = tracker.copy(id = "other", singleton = 1))
                }
                assertFailsWith<SQLiteException> {
                    db.profileDao().putTracker(tracker = tracker.copy(singleton = 1))
                }
                assertFailsWith<SQLiteException> {
                    db.planningDao()
                        .putAnnualVacationPolicy(policy = policy.copy(trackerId = "missing"))
                }
            } finally {
                db.close()
            }
        } finally {
            folder.toFile().deleteRecursively()
        }
    }
}
