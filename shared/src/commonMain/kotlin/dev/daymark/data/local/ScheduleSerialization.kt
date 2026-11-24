package dev.daymark.data.local

import dev.daymark.plan.WeeklySchedule
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

internal fun encodeSchedule(schedule: WeeklySchedule): String = buildJsonObject {
    put("days", JsonArray(schedule.days.sortedBy { it.ordinal }.map { JsonPrimitive(it.name) }))
    put("arrival", schedule.arrival.toString()); put(
    "graceMinutes",
    schedule.graceMinutes
); put("zoneId", schedule.zoneId)
    put("departure", schedule.departure?.let { JsonPrimitive(it.toString()) } ?: JsonNull)
    putJsonObject("departureOverrides") {
        schedule.departureOverrides.forEach { (day, time) ->
            put(day.name, time?.let { JsonPrimitive(it.toString()) } ?: JsonNull)
        }
    }
    putJsonObject("overrides") {
        schedule.overrides.forEach { (day, time) ->
            put(
                day.name,
                time.toString()
            )
        }
    }
}.toString()

internal fun decodeSchedule(text: String): WeeklySchedule {
    val json = Json.parseToJsonElement(text).jsonObject
    return WeeklySchedule(
        json.getValue("days").jsonArray.map { DayOfWeek.valueOf(it.jsonPrimitive.content) }.toSet(),
        LocalTime.parse(json.getValue("arrival").jsonPrimitive.content),
        json.getValue("overrides").jsonObject.map { (day, value) ->
            DayOfWeek.valueOf(day) to LocalTime.parse(
                value.jsonPrimitive.content
            )
        }.toMap(),
        json.getValue("graceMinutes").jsonPrimitive.int,
        json.getValue("zoneId").jsonPrimitive.content,
        json["departure"]?.jsonPrimitive?.contentOrNull?.let(LocalTime::parse),
        json["departureOverrides"]?.jsonObject?.map { (day, time) ->
            DayOfWeek.valueOf(day) to time.jsonPrimitive.contentOrNull?.let(LocalTime::parse)
        }?.toMap() ?: emptyMap()
    )
}
