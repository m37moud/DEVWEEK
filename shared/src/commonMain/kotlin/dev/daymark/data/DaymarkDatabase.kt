package dev.daymark.data

import androidx.room3.*
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.*

@Database(entities = [SettingEntity::class, TrackerEntity::class, PlanVersionEntity::class, AttendanceEntity::class,
    TimeOffEntity::class, BalanceAdjustmentEntity::class, ReportBindingEntity::class, DirtyProjectionEntity::class, ReportBatchEntity::class],
    version = 1, exportSchema = true)
@ConstructedBy(DaymarkDatabaseConstructor::class)
abstract class DaymarkDatabase : RoomDatabase() { abstract fun dao(): DaymarkDao }

expect object DaymarkDatabaseConstructor : RoomDatabaseConstructor<DaymarkDatabase> {
    override fun initialize(): DaymarkDatabase
}

internal fun configureDatabase(builder: RoomDatabase.Builder<DaymarkDatabase>): DaymarkDatabase = builder
    .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO)
    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING).build()
