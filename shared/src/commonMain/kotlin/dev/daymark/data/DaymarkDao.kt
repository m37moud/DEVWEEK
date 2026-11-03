package dev.daymark.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DaymarkDao {
    @Query("SELECT * FROM trackers LIMIT 1")
    suspend fun tracker(): TrackerEntity?
    @Insert
    suspend fun insertTracker(tracker: TrackerEntity)
    @Upsert
    suspend fun putTracker(tracker: TrackerEntity)
    @Query("SELECT * FROM trackers LIMIT 1")
    fun observeTracker(): Flow<TrackerEntity?>
    @Query("UPDATE trackers SET changeRevision = changeRevision + 1 WHERE id = :trackerId")
    suspend fun advanceRevision(trackerId: String)
    @Query("SELECT * FROM settings WHERE `key` = :key")
    suspend fun setting(key: String): SettingEntity?
    @Query("SELECT * FROM settings WHERE `key` = :key")
    fun observeSetting(key: String): Flow<SettingEntity?>
    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun deleteSetting(key: String)
    @Upsert
    suspend fun putSetting(setting: SettingEntity)
    @Query("SELECT * FROM plan_versions WHERE trackerId = :trackerId ORDER BY version")
    suspend fun versions(trackerId: String): List<PlanVersionEntity>
    @Upsert
    suspend fun putVersion(version: PlanVersionEntity)
    @Query("SELECT * FROM attendance WHERE trackerId = :trackerId AND date = :date")
    suspend fun day(trackerId: String, date: String): AttendanceEntity?
    @Query("SELECT * FROM attendance WHERE trackerId = :trackerId AND date = :date")
    fun observeDay(trackerId: String, date: String): Flow<AttendanceEntity?>
    @Query("SELECT * FROM attendance WHERE trackerId = :trackerId ORDER BY date")
    suspend fun days(trackerId: String): List<AttendanceEntity>
    @Query("SELECT * FROM attendance WHERE trackerId = :trackerId ORDER BY date")
    fun observeDays(trackerId: String): Flow<List<AttendanceEntity>>
    @Query("SELECT COUNT(*) FROM attendance WHERE trackerId = :trackerId AND date = :date")
    suspend fun countDay(trackerId: String, date: String): Int
    @Upsert
    suspend fun putDay(day: AttendanceEntity)
    @Query("SELECT * FROM time_off WHERE trackerId = :trackerId ORDER BY id")
    suspend fun timeOff(trackerId: String): List<TimeOffEntity>
    @Query("SELECT * FROM time_off WHERE trackerId = :trackerId ORDER BY id")
    fun observeTimeOff(trackerId: String): Flow<List<TimeOffEntity>>
    @Upsert
    suspend fun putTimeOff(entry: TimeOffEntity)
    @Query("DELETE FROM time_off WHERE id = :id")
    suspend fun deleteTimeOff(id: String)
    @Query("SELECT * FROM balance_adjustments WHERE trackerId = :trackerId ORDER BY id")
    suspend fun balanceAdjustments(trackerId: String): List<BalanceAdjustmentEntity>
    @Insert
    suspend fun insertBalanceAdjustment(adjustment: BalanceAdjustmentEntity)
    @Query("SELECT * FROM dirty_projections WHERE trackerId = :trackerId ORDER BY projectionKey")
    suspend fun dirtyProjections(trackerId: String): List<DirtyProjectionEntity>
    @Query("SELECT * FROM dirty_projections WHERE trackerId = :trackerId ORDER BY projectionKey")
    fun observeDirtyProjections(trackerId: String): Flow<List<DirtyProjectionEntity>>
    @Query("SELECT * FROM dirty_projections WHERE trackerId = :trackerId AND projectionKey = :key")
    suspend fun dirtyProjection(trackerId: String, key: String): DirtyProjectionEntity?
    @Upsert
    suspend fun putDirtyProjection(projection: DirtyProjectionEntity)
    @Query("SELECT * FROM report_bindings WHERE trackerId = :trackerId")
    suspend fun binding(trackerId: String): ReportBindingEntity?
    @Upsert
    suspend fun putBinding(binding: ReportBindingEntity)
    @Query("DELETE FROM report_bindings WHERE trackerId = :trackerId")
    suspend fun deleteBinding(trackerId: String)
    @Query("SELECT * FROM report_batches WHERE trackerId = :trackerId ORDER BY batchIdentity")
    suspend fun batches(trackerId: String): List<ReportBatchEntity>
    @Query("DELETE FROM report_batches WHERE batchIdentity = :identity")
    suspend fun deleteBatch(identity: String)
    @Upsert
    suspend fun putBatch(batch: ReportBatchEntity)
    @Query("DELETE FROM attendance")
    suspend fun deleteAttendance()
    @Query("DELETE FROM time_off")
    suspend fun deleteTimeOffRows()
    @Query("DELETE FROM balance_adjustments")
    suspend fun deleteBalanceRows()
    @Query("DELETE FROM report_batches")
    suspend fun deleteReportBatches()
    @Query("DELETE FROM dirty_projections")
    suspend fun deleteDirtyRows()
    @Query("DELETE FROM report_bindings")
    suspend fun deleteBindings()
    @Query("DELETE FROM plan_versions")
    suspend fun deletePlanVersions()
    @Query("DELETE FROM trackers")
    suspend fun deleteTrackers()
    @Query("DELETE FROM settings")
    suspend fun deleteSettings()

}

/** Caller must hold a write transaction so data changes and projection revisions remain atomic. */
internal suspend fun DaymarkDao.markDirty(trackerId: String, key: String) {
    val before = dirtyProjection(trackerId, key)
    putDirtyProjection(
        before?.copy(revision = before.revision + 1) ?: DirtyProjectionEntity(
            trackerId,
            key,
            1
        )
    )
}

/** Caller owns the write transaction. Failure rolls back every authoritative table together. */
internal suspend fun DaymarkDao.deleteAllLocalRows() {
    deleteAttendance(); deleteTimeOffRows(); deleteBalanceRows(); deleteReportBatches(); deleteDirtyRows()
    deleteBindings(); deletePlanVersions(); deleteTrackers(); deleteSettings()
}
