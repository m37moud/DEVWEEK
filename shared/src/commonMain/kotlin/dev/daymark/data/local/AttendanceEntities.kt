package dev.daymark.data.local

import androidx.room3.Entity
import dev.daymark.models.attendance.AttendanceEvidence
import dev.daymark.models.attendance.AttendanceSource
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.models.attendance.DayKey
import dev.daymark.models.attendance.DayPlan
import dev.daymark.models.attendance.RegisteredPlace
import dev.daymark.models.attendance.TrackerCategory
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

@Entity(tableName = "attendance", primaryKeys = ["trackerId", "date"])
data class AttendanceEntity(
    val trackerId: String,
    val date: String,
    val category: String,
    val arrival: String,
    val deadline: String,
    val zoneId: String,
    val scheduleVersion: Long,
    val placeLatitude: Double,
    val placeLongitude: Double,
    val placeRadiusMetres: Int,
    val placeVersion: Long,
    val isScheduled: Boolean,
    val updatedAt: String,
    val evidenceStatus: String? = null,
    val evidenceSource: String? = null,
    val confirmedAt: String? = null,
    val note: String? = null,
    val evidenceUpdatedAt: String? = null,
    val automaticState: String = "UNCLAIMED",
    val automaticAttemptId: String? = null,
    val automaticOwnerSession: String? = null,
    val automaticClaimedAt: String? = null,
    val automaticRejection: String? = null,
    val revision: Long = 1,
    val userEditRevision: Long = 0,
    val cachedStatus: String = "SCHEDULED",
    val cachedSource: String = "NONE",
    val cachedConfirmedAt: String? = null,
) {
    fun plan() = DayPlan(
        DayKey(trackerId, LocalDate.parse(date)),
        TrackerCategory.valueOf(category),
        Instant.parse(arrival),
        Instant.parse(deadline),
        zoneId,
        scheduleVersion,
        RegisteredPlace(placeLatitude, placeLongitude, placeRadiusMetres, placeVersion),
        isScheduled
    )

    fun evidence(): AttendanceEvidence? = evidenceStatus?.let {
        AttendanceEvidence(
            AttendanceStatus.valueOf(it), AttendanceSource.valueOf(requireNotNull(evidenceSource)),
            confirmedAt?.let(Instant::parse), note, Instant.parse(requireNotNull(evidenceUpdatedAt))
        )
    }

    override fun toString() =
        "AttendanceEntity(date=$date, revision=$revision, placeAndNotes=<redacted>)"

    companion object {
        fun fromPlan(plan: DayPlan, now: Instant) = AttendanceEntity(
            plan.key.trackerId,
            plan.key.date.toString(),
            plan.category.name,
            plan.arrival.toString(),
            plan.deadline.toString(),
            plan.zoneId,
            plan.scheduleVersion,
            plan.place.latitude,
            plan.place.longitude,
            plan.place.radiusMetres,
            plan.place.version,
            plan.isScheduled,
            now.toString()
        )
    }
}
