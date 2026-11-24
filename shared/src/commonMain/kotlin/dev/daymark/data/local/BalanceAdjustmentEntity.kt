package dev.daymark.data.local

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "balance_adjustments",
    indices = [
        Index(value = ["trackerId", "id"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = TrackerEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackerId"]
        )
    ]
)
data class BalanceAdjustmentEntity(
    @PrimaryKey val id: String, val trackerId: String, val delta: Int, val createdAt: String,
    val note: String? = null,
    val balanceYear: Int? = null
) {
    override fun toString() = "BalanceAdjustmentEntity(id=$id, delta=$delta, note=<redacted>)"
}
