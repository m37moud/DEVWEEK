package dev.daymark.widgets

import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.settings.ThemePreference
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Display-only cache. It cannot be used as location evidence or an attendance write command. */
@Serializable
data class WidgetSnapshot(
    val schemaVersion: Int = 1,
    val trackerId: String,
    val generatedAtEpochMillis: Long,
    val attendanceDate: String,
    val effectiveLocale: String,
    val displayStatus: String,
    val statusCode: String,
    val placeLabel: String,
    val deadlineDisplay: String?,
    val deadlineEpochMillis: Long?,
    val zoneId: String,
    val quickCheckRoute: String,
    val arrivalEpochMillis: Long? = null,
    val themePreference: String = "SYSTEM",
    val isTracked: Boolean = true
) {
    override fun toString() =
        "WidgetSnapshot(schemaVersion=$schemaVersion, statusCode=$statusCode, metadata=<redacted>)"
}

private val snapshotJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }
fun encodeWidgetSnapshot(snapshot: WidgetSnapshot): String {
    require(validWidgetSnapshot(snapshot))
    return snapshotJson.encodeToString(snapshot)
}

fun decodeWidgetSnapshot(json: String): WidgetSnapshot? {
    if (json.length > 262_144) return null
    return runCatching {
        if (snapshotJson.parseToJsonElement(json).jsonObject["schemaVersion"]?.jsonPrimitive?.intOrNull != 1) return@runCatching null
        snapshotJson.decodeFromString<WidgetSnapshot>(json).takeIf(::validWidgetSnapshot)
    }.getOrNull()
}

private fun validWidgetSnapshot(value: WidgetSnapshot): Boolean = runCatching {
    require(value.schemaVersion == 1 && value.generatedAtEpochMillis >= 0)
    val id = Uuid.parse(value.trackerId).toString()
    LocalDate.parse(value.attendanceDate)
    TimeZone.of(value.zoneId)
    AttendanceStatus.valueOf(value.statusCode)
    ThemePreference.valueOf(value.themePreference)
    require(value.effectiveLocale in setOf("en", "ar") && value.displayStatus.isNotBlank())
    require(parseQuickCheckRoute(value.quickCheckRoute)?.trackerId == id)
    require((value.deadlineEpochMillis == null) == (value.deadlineDisplay == null))
    require(value.deadlineEpochMillis == null || value.deadlineEpochMillis >= 0)
    require(value.arrivalEpochMillis == null || value.arrivalEpochMillis >= 0)
    if (value.arrivalEpochMillis != null && value.deadlineEpochMillis != null) require(value.arrivalEpochMillis <= value.deadlineEpochMillis)
    true
}.getOrDefault(false)
