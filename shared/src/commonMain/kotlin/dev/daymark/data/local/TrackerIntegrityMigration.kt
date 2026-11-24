package dev.daymark.data.local

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal object TrackerIntegrityMigration : Migration(startVersion = 5, endVersion = 6) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.prepare(sql = "SELECT COUNT(*) FROM trackers").use { statement ->
            check(statement.step() && statement.getLong(index = 0) <= 1) { "multiple_trackers" }
        }
        connection.execSQL(sql = "UPDATE trackers SET singleton = 0 WHERE singleton != 0")
        for (table in trackerTables) connection.addTrackerOwnership(table = table)
        connection.createTrackerSingletonTriggers()
    }
}

internal fun SQLiteConnection.createTrackerSingletonTriggers() {
    for ((name, operation) in listOf("insert" to "INSERT", "update" to "UPDATE OF singleton")) {
        execSQL(
            sql = """
                CREATE TRIGGER tracker_singleton_$name BEFORE $operation ON trackers
                WHEN NEW.singleton != 0
                BEGIN
                    SELECT RAISE(ABORT, 'invalid_tracker_singleton');
                END
            """.trimIndent()
        )
    }
}

private fun SQLiteConnection.addTrackerOwnership(table: TrackerTableMigration) {
    val name = table.name
    prepare(sql = "SELECT 1 FROM $name WHERE trackerId NOT IN (SELECT id FROM trackers) LIMIT 1")
        .use { check(!it.step()) { "orphan_tracker_rows" } }

    val replacement = "_daymark_v6_$name"
    execSQL(sql = "CREATE TABLE $replacement (${table.columns})")
    execSQL(sql = "INSERT INTO $replacement SELECT * FROM $name")
    execSQL(sql = "DROP TABLE $name")
    execSQL(sql = "ALTER TABLE $replacement RENAME TO $name")
    for (columns in table.indices) {
        val indexName = "index_${name}_${columns.joinToString(separator = "_")}"
        execSQL(sql = "CREATE INDEX $indexName ON $name (${columns.joinToString()})")
    }
}

private data class TrackerTableMigration(
    val name: String,
    val columns: String,
    val indices: List<List<String>> = emptyList()
)

private val trackerTables = listOf(
    TrackerTableMigration(
        name = "plan_versions",
        columns = """
            trackerId TEXT NOT NULL,
            version INTEGER NOT NULL,
            effectiveDate TEXT NOT NULL,
            effectiveAt TEXT NOT NULL,
            category TEXT NOT NULL,
            scheduleJson TEXT NOT NULL,
            placeLatitude REAL NOT NULL,
            placeLongitude REAL NOT NULL,
            placeRadiusMetres INTEGER NOT NULL,
            placeVersion INTEGER NOT NULL,
            placeName TEXT NOT NULL,
            PRIMARY KEY (trackerId, version),
            FOREIGN KEY (trackerId) REFERENCES trackers (id)
        """.trimIndent()
    ),
    TrackerTableMigration(
        name = "attendance",
        columns = """
            trackerId TEXT NOT NULL,
            date TEXT NOT NULL,
            category TEXT NOT NULL,
            arrival TEXT NOT NULL,
            deadline TEXT NOT NULL,
            zoneId TEXT NOT NULL,
            scheduleVersion INTEGER NOT NULL,
            placeLatitude REAL NOT NULL,
            placeLongitude REAL NOT NULL,
            placeRadiusMetres INTEGER NOT NULL,
            placeVersion INTEGER NOT NULL,
            isScheduled INTEGER NOT NULL,
            updatedAt TEXT NOT NULL,
            evidenceStatus TEXT,
            evidenceSource TEXT,
            confirmedAt TEXT,
            note TEXT,
            evidenceUpdatedAt TEXT,
            automaticState TEXT NOT NULL,
            automaticAttemptId TEXT,
            automaticOwnerSession TEXT,
            automaticClaimedAt TEXT,
            automaticRejection TEXT,
            revision INTEGER NOT NULL,
            userEditRevision INTEGER NOT NULL,
            cachedStatus TEXT NOT NULL,
            cachedSource TEXT NOT NULL,
            cachedConfirmedAt TEXT,
            departure TEXT,
            PRIMARY KEY (trackerId, date),
            FOREIGN KEY (trackerId) REFERENCES trackers (id)
        """.trimIndent(),
        indices = listOf(listOf("trackerId", "cachedStatus"), listOf("trackerId", "automaticState"))
    ),
    TrackerTableMigration(
        name = "time_off",
        columns = """
            id TEXT NOT NULL,
            trackerId TEXT NOT NULL,
            kind TEXT NOT NULL,
            startDate TEXT NOT NULL,
            endDate TEXT NOT NULL,
            note TEXT,
            updatedAt TEXT NOT NULL,
            PRIMARY KEY (id),
            FOREIGN KEY (trackerId) REFERENCES trackers (id)
        """.trimIndent(),
        indices = listOf(listOf("trackerId", "id"))
    ),
    TrackerTableMigration(
        name = "balance_adjustments",
        columns = """
            id TEXT NOT NULL,
            trackerId TEXT NOT NULL,
            delta INTEGER NOT NULL,
            createdAt TEXT NOT NULL,
            note TEXT,
            balanceYear INTEGER,
            PRIMARY KEY (id),
            FOREIGN KEY (trackerId) REFERENCES trackers (id)
        """.trimIndent(),
        indices = listOf(listOf("trackerId", "id"))
    ),
    TrackerTableMigration(
        name = "report_bindings",
        columns = """
            trackerId TEXT NOT NULL,
            connectionGeneration TEXT NOT NULL,
            accountReference TEXT NOT NULL,
            spreadsheetId TEXT NOT NULL,
            schemaVersion INTEGER NOT NULL,
            annualTabIdsJson TEXT NOT NULL,
            PRIMARY KEY (trackerId),
            FOREIGN KEY (trackerId) REFERENCES trackers (id)
        """.trimIndent()
    ),
    TrackerTableMigration(
        name = "dirty_projections",
        columns = """
            trackerId TEXT NOT NULL,
            projectionKey TEXT NOT NULL,
            revision INTEGER NOT NULL,
            acknowledgedRevision INTEGER NOT NULL,
            PRIMARY KEY (trackerId, projectionKey),
            FOREIGN KEY (trackerId) REFERENCES trackers (id)
        """.trimIndent()
    ),
    TrackerTableMigration(
        name = "report_batches",
        columns = """
            batchIdentity TEXT NOT NULL,
            trackerId TEXT NOT NULL,
            connectionGeneration TEXT NOT NULL,
            payloadJson TEXT NOT NULL,
            status TEXT NOT NULL,
            updatedAt TEXT NOT NULL,
            PRIMARY KEY (batchIdentity),
            FOREIGN KEY (trackerId) REFERENCES trackers (id)
        """.trimIndent(),
        indices = listOf(listOf("trackerId", "connectionGeneration", "status", "batchIdentity"))
    ),
    TrackerTableMigration(
        name = "annual_vacation_policies",
        columns = """
            trackerId TEXT NOT NULL,
            effectiveYear INTEGER NOT NULL,
            annualAllowanceDays INTEGER NOT NULL,
            carryOverUnusedDays INTEGER NOT NULL,
            PRIMARY KEY (trackerId, effectiveYear),
            FOREIGN KEY (trackerId) REFERENCES trackers (id)
        """.trimIndent()
    ),
)
