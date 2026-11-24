package dev.daymark.data.local

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "time_off",
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
data class TimeOffEntity(
    @PrimaryKey val id: String, val trackerId: String, val kind: String,
    val startDate: String, val endDate: String, val note: String?, val updatedAt: String
) {
    override fun toString() = "TimeOffEntity(id=$id, kind=$kind, note=<redacted>)"
}
