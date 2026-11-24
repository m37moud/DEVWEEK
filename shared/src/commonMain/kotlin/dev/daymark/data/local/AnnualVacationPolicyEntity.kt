package dev.daymark.data.local

import androidx.room3.Entity
import androidx.room3.ForeignKey

@Entity(
    tableName = "annual_vacation_policies",
    primaryKeys = ["trackerId", "effectiveYear"],
    foreignKeys = [
        ForeignKey(
            entity = TrackerEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackerId"]
        )
    ]
)
data class AnnualVacationPolicyEntity(
    val trackerId: String,
    val effectiveYear: Int,
    val annualAllowanceDays: Int,
    val carryOverUnusedDays: Boolean
)
