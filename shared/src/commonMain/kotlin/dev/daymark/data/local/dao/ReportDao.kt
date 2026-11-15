package dev.daymark.data.local.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import dev.daymark.data.local.ReportBatchEntity
import dev.daymark.data.local.ReportBindingEntity

@Dao
interface ReportDao {
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
}
