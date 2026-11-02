package dev.daymark.data

import androidx.room3.*
import dev.daymark.attendance.*
import dev.daymark.plan.WeeklySchedule
import kotlinx.datetime.*
import kotlinx.serialization.json.*
import kotlin.time.Instant

@Entity(tableName = "settings")
data class SettingEntity(@PrimaryKey val key: String, val value: String) {
    override fun toString() = "SettingEntity(key=$key, value=<redacted>)"
}
@Entity(tableName = "trackers", indices = [Index(value = ["singleton"], unique = true)])
data class TrackerEntity(@PrimaryKey val id: String, val displayName: String, val createdAt: String,
    val trackingStartsAt: String, val openingBalance: Int, val automaticEnabled: Boolean, val singleton: Int = 0,
    @ColumnInfo(defaultValue = "0") val changeRevision: Long = 0) {
    override fun toString() = "TrackerEntity(id=$id, metadata=<redacted>)"
}
@Entity(tableName = "plan_versions", primaryKeys = ["trackerId", "version"])
data class PlanVersionEntity(val trackerId: String, val version: Long, val effectiveDate: String, val effectiveAt: String,
    val category: String, val scheduleJson: String, val placeLatitude: Double, val placeLongitude: Double,
    val placeRadiusMetres: Int, val placeVersion: Long, val placeName: String) {
    fun schedule(): WeeklySchedule = decodeSchedule(scheduleJson)
    fun place() = RegisteredPlace(placeLatitude, placeLongitude, placeRadiusMetres, placeVersion)
    override fun toString() = "PlanVersionEntity(version=$version, registeredPlace=<redacted>)"
    companion object {
        fun fromSnapshot(trackerId: String, version: Long, effectiveDate: LocalDate, effectiveAt: Instant,
            category: TrackerCategory, schedule: WeeklySchedule, place: RegisteredPlace, placeName: String) =
            PlanVersionEntity(trackerId, version, effectiveDate.toString(), effectiveAt.toString(), category.name,
                encodeSchedule(schedule), place.latitude, place.longitude, place.radiusMetres, place.version, placeName)
    }
}
@Entity(tableName = "time_off")
data class TimeOffEntity(@PrimaryKey val id: String, val trackerId: String, val kind: String,
    val startDate: String, val endDate: String, val note: String?, val updatedAt: String) {
    override fun toString() = "TimeOffEntity(id=$id, kind=$kind, note=<redacted>)"
}
@Entity(tableName = "balance_adjustments")
data class BalanceAdjustmentEntity(@PrimaryKey val id: String, val trackerId: String, val delta: Int, val createdAt: String,
    val note: String? = null) {
    override fun toString() = "BalanceAdjustmentEntity(id=$id, delta=$delta, note=<redacted>)"
}

internal fun encodeSchedule(schedule: WeeklySchedule): String = buildJsonObject {
    put("days", JsonArray(schedule.days.sortedBy { it.ordinal }.map { JsonPrimitive(it.name) }))
    put("arrival", schedule.arrival.toString()); put("graceMinutes", schedule.graceMinutes); put("zoneId", schedule.zoneId)
    putJsonObject("overrides") { schedule.overrides.forEach { (day, time) -> put(day.name, time.toString()) } }
}.toString()
internal fun decodeSchedule(text: String): WeeklySchedule {
    val json = Json.parseToJsonElement(text).jsonObject
    return WeeklySchedule(json.getValue("days").jsonArray.map { DayOfWeek.valueOf(it.jsonPrimitive.content) }.toSet(),
        LocalTime.parse(json.getValue("arrival").jsonPrimitive.content),
        json.getValue("overrides").jsonObject.map { (day, value) -> DayOfWeek.valueOf(day) to LocalTime.parse(value.jsonPrimitive.content) }.toMap(),
        json.getValue("graceMinutes").jsonPrimitive.int, json.getValue("zoneId").jsonPrimitive.content)
}
