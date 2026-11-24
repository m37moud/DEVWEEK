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
import dev.daymark.data.local.dao.AttendanceDao
import dev.daymark.data.local.dao.DirtyProjectionDao
import dev.daymark.data.local.dao.LocalDataDeletionDao
import dev.daymark.data.local.dao.PlaceAddressDao
import dev.daymark.data.local.dao.PlanningDao
import dev.daymark.data.local.dao.ProfileDao
import dev.daymark.data.local.dao.ReportDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [SettingEntity::class, TrackerEntity::class, PlanVersionEntity::class, AttendanceEntity::class,
        TimeOffEntity::class, BalanceAdjustmentEntity::class, ReportBindingEntity::class, DirtyProjectionEntity::class,
        ReportBatchEntity::class, PlaceAddressEntity::class, AnnualVacationPolicyEntity::class],
    version = 6, exportSchema = true
)
@ConstructedBy(DaymarkDatabaseConstructor::class)
abstract class DaymarkDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun planningDao(): PlanningDao
    abstract fun dirtyProjectionDao(): DirtyProjectionDao
    abstract fun reportDao(): ReportDao
    abstract fun placeAddressDao(): PlaceAddressDao
    abstract fun localDataDeletionDao(): LocalDataDeletionDao
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
        }, object : Migration(3, 4) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE attendance ADD COLUMN departure TEXT")
            }
        }, object : Migration(4, 5) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE balance_adjustments ADD COLUMN balanceYear INTEGER")
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS annual_vacation_policies (
                        trackerId TEXT NOT NULL,
                        effectiveYear INTEGER NOT NULL,
                        annualAllowanceDays INTEGER NOT NULL,
                        carryOverUnusedDays INTEGER NOT NULL,
                        PRIMARY KEY (trackerId, effectiveYear)
                    )
                    """.trimIndent()
                )
            }
        }, TrackerIntegrityMigration)
        .addCallback(object : RoomDatabase.Callback() {
            override suspend fun onCreate(connection: SQLiteConnection) {
                connection.createTrackerSingletonTriggers()
            }
        })
        .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO)
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING).build()
