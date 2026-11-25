package dev.daymark.data.local

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal object AutomaticRetryMigration : Migration(6, 7) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE attendance ADD COLUMN automaticAttempts INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE attendance ADD COLUMN automaticAttemptLimit INTEGER NOT NULL DEFAULT 1")
        connection.execSQL("ALTER TABLE attendance ADD COLUMN automaticGeneration INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE attendance ADD COLUMN automaticSeriesEditRevision INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE attendance ADD COLUMN automaticRetryAt TEXT")
        connection.execSQL("ALTER TABLE attendance ADD COLUMN automaticResultPending INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("UPDATE attendance SET automaticAttempts = 1 WHERE automaticState <> 'UNCLAIMED'")
    }
}
