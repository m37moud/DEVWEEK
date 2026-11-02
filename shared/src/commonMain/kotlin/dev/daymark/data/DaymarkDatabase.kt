package dev.daymark.data

import androidx.room3.*
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.room3.migration.Migration
import kotlinx.coroutines.*

@Database(entities = [SettingEntity::class, TrackerEntity::class, PlanVersionEntity::class, AttendanceEntity::class,
    TimeOffEntity::class, BalanceAdjustmentEntity::class, ReportBindingEntity::class, DirtyProjectionEntity::class, ReportBatchEntity::class],
    version = 2, exportSchema = true)
@ConstructedBy(DaymarkDatabaseConstructor::class)
abstract class DaymarkDatabase : RoomDatabase() { abstract fun dao(): DaymarkDao }

expect object DaymarkDatabaseConstructor : RoomDatabaseConstructor<DaymarkDatabase> {
    override fun initialize(): DaymarkDatabase
}

internal fun configureDatabase(builder: RoomDatabase.Builder<DaymarkDatabase>): DaymarkDatabase = builder
    .addMigrations(object : Migration(1, 2) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE trackers ADD COLUMN changeRevision INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE balance_adjustments ADD COLUMN note TEXT")
        }
    })
    .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO)
    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING).build()
