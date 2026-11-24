package dev.daymark.data.local

import androidx.room3.Entity
import androidx.room3.ForeignKey
import dev.daymark.models.attendance.RegisteredPlace
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.plan.WeeklySchedule
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

@Entity(
    tableName = "plan_versions",
    primaryKeys = ["trackerId", "version"],
    foreignKeys = [
        ForeignKey(
            entity = TrackerEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackerId"]
        )
    ]
)
data class PlanVersionEntity(
    val trackerId: String,
    val version: Long,
    val effectiveDate: String,
    val effectiveAt: String,
    val category: String,
    val scheduleJson: String,
    val placeLatitude: Double,
    val placeLongitude: Double,
    val placeRadiusMetres: Int,
    val placeVersion: Long,
    val placeName: String
) {
    fun schedule(): WeeklySchedule = decodeSchedule(scheduleJson)
    fun place() = RegisteredPlace(placeLatitude, placeLongitude, placeRadiusMetres, placeVersion)
    override fun toString() = "PlanVersionEntity(version=$version, registeredPlace=<redacted>)"

    companion object {
        fun fromSnapshot(
            trackerId: String,
            version: Long,
            effectiveDate: LocalDate,
            effectiveAt: Instant,
            category: TrackerCategory,
            schedule: WeeklySchedule,
            place: RegisteredPlace,
            placeName: String
        ) =
            PlanVersionEntity(
                trackerId,
                version,
                effectiveDate.toString(),
                effectiveAt.toString(),
                category.name,
                encodeSchedule(schedule),
                place.latitude,
                place.longitude,
                place.radiusMetres,
                place.version,
                placeName
            )
    }
}
