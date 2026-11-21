package dev.daymark.data.local.dao

import androidx.room3.Dao
import androidx.room3.Query

@Dao
interface LocalDataDeletionDao {
    @Query("DELETE FROM place_addresses")
    suspend fun deletePlaceAddresses()

    @Query("DELETE FROM attendance")
    suspend fun deleteAttendance()

    @Query("DELETE FROM time_off")
    suspend fun deleteTimeOffRows()

    @Query("DELETE FROM balance_adjustments")
    suspend fun deleteBalanceRows()

    @Query("DELETE FROM annual_vacation_policies")
    suspend fun deleteAnnualVacationPolicies()

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
