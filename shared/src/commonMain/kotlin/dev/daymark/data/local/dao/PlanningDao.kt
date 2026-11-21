package dev.daymark.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Upsert
import dev.daymark.data.local.AnnualVacationPolicyEntity
import dev.daymark.data.local.BalanceAdjustmentEntity
import dev.daymark.data.local.PlanVersionEntity
import dev.daymark.data.local.TimeOffEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanningDao {
    @Query("SELECT * FROM plan_versions WHERE trackerId = :trackerId ORDER BY version")
    suspend fun versions(trackerId: String): List<PlanVersionEntity>

    @Upsert
    suspend fun putVersion(version: PlanVersionEntity)

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

    @Query("SELECT * FROM annual_vacation_policies WHERE trackerId = :trackerId ORDER BY effectiveYear")
    suspend fun annualVacationPolicies(trackerId: String): List<AnnualVacationPolicyEntity>

    @Upsert
    suspend fun putAnnualVacationPolicy(policy: AnnualVacationPolicyEntity)
}
