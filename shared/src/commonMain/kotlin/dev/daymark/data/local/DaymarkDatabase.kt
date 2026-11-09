package dev.daymark.data.local

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import dev.daymark.data.local.address.PlaceAddressEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [SettingEntity::class, TrackerEntity::class, PlanVersionEntity::class, AttendanceEntity::class,
        TimeOffEntity::class, BalanceAdjustmentEntity::class, ReportBindingEntity::class, DirtyProjectionEntity::class, ReportBatchEntity::class, PlaceAddressEntity::class],
    version = 3, exportSchema = true
)
@ConstructedBy(DaymarkDatabaseConstructor::class)
abstract class DaymarkDatabase : RoomDatabase() {
    abstract fun dao(): DaymarkDao
}

expect object DaymarkDatabaseConstructor : RoomDatabaseConstructor<DaymarkDatabase> {
    override fun initialize(): DaymarkDatabase
}

internal fun configureDatabase(builder: RoomDatabase.Builder<DaymarkDatabase>): DaymarkDatabase =
    builder
        .addMigrations(object : Migration(1, 2) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE trackers ADD COLUMN changeRevision INTEGER NOT NULL DEFAULT 0")
                connection.execSQL("ALTER TABLE balance_adjustments ADD COLUMN note TEXT")
            }
        }, object : Migration(2, 3) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("CREATE TABLE IF NOT EXISTS place_addresses (latitude REAL NOT NULL, longitude REAL NOT NULL, language TEXT NOT NULL, address TEXT NOT NULL, PRIMARY KEY(latitude, longitude, language))")
            }
        })
        .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO)
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING).build()
