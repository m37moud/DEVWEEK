package dev.daymark.data.local

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "report_bindings",
    foreignKeys = [
        ForeignKey(
            entity = TrackerEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackerId"]
        )
    ]
)
data class ReportBindingEntity(
    @PrimaryKey val trackerId: String,
    val connectionGeneration: String,
    val accountReference: String,
    val spreadsheetId: String,
    val schemaVersion: Int,
    @ColumnInfo(name = "annualTabIdsJson") val metadataJson: String
) {
    override fun toString() =
        "ReportBindingEntity(generation=$connectionGeneration, account=<redacted>)"
}

@Entity(
    tableName = "dirty_projections",
    primaryKeys = ["trackerId", "projectionKey"],
    foreignKeys = [
        ForeignKey(
            entity = TrackerEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackerId"]
        )
    ]
)
data class DirtyProjectionEntity(
    val trackerId: String,
    val projectionKey: String,
    val revision: Long,
    val acknowledgedRevision: Long = 0
)

@Entity(
    tableName = "report_batches",
    indices = [
        Index(value = ["trackerId", "connectionGeneration", "status", "batchIdentity"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = TrackerEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackerId"]
        )
    ]
)
data class ReportBatchEntity(
    @PrimaryKey val batchIdentity: String, val trackerId: String, val connectionGeneration: String,
    val payloadJson: String, val status: String, val updatedAt: String
) {
    override fun toString() =
        "ReportBatchEntity(batchIdentity=$batchIdentity, status=$status, payload=<redacted>)"
}
