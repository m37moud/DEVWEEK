package dev.daymark.data

import androidx.room3.*

@Entity(tableName = "report_bindings")
data class ReportBindingEntity(@PrimaryKey val trackerId: String, val connectionGeneration: String,
    val accountReference: String, val spreadsheetId: String, val schemaVersion: Int, val annualTabIdsJson: String) {
    override fun toString() = "ReportBindingEntity(generation=$connectionGeneration, account=<redacted>)"
}
@Entity(tableName = "dirty_projections", primaryKeys = ["trackerId", "projectionKey"])
data class DirtyProjectionEntity(val trackerId: String, val projectionKey: String, val revision: Long, val acknowledgedRevision: Long = 0)
@Entity(tableName = "report_batches")
data class ReportBatchEntity(@PrimaryKey val batchIdentity: String, val trackerId: String, val connectionGeneration: String,
    val payloadJson: String, val status: String, val updatedAt: String) {
    override fun toString() = "ReportBatchEntity(batchIdentity=$batchIdentity, status=$status, payload=<redacted>)"
}
