package dev.daymark.data.local

import androidx.room3.Entity

@Entity(tableName = "annual_vacation_policies", primaryKeys = ["trackerId", "effectiveYear"])
data class AnnualVacationPolicyEntity(
    val trackerId: String,
    val effectiveYear: Int,
    val annualAllowanceDays: Int,
    val carryOverUnusedDays: Boolean
)
