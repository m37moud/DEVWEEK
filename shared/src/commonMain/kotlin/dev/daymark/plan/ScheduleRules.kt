package dev.daymark.plan

import dev.daymark.attendance.DayKey
import dev.daymark.attendance.DayPlan
import dev.daymark.attendance.RegisteredPlace
import dev.daymark.attendance.TrackerCategory
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes

enum class TimeOffKind { HOLIDAY, VACATION }

data class WeeklySchedule(
    val days: Set<DayOfWeek>, val arrival: LocalTime, val overrides: Map<DayOfWeek, LocalTime>,
    val graceMinutes: Int, val zoneId: String,
) {
    init {
        require(days.isNotEmpty())
        require(graceMinutes in 0..120)
        require(overrides.keys.all { it in days })
        TimeZone.of(zoneId)
    }
}

fun resolveDay(
    key: DayKey,
    schedule: WeeklySchedule,
    category: TrackerCategory,
    place: RegisteredPlace,
    scheduleVersion: Long
): DayPlan {
    val time = schedule.overrides[key.date.dayOfWeek] ?: schedule.arrival
    val arrival = resolveLocalArrival(key.date, time, schedule.zoneId)
    val deadline = arrival + schedule.graceMinutes.minutes
    val zone = TimeZone.of(schedule.zoneId)
    require(arrival.toLocalDateTime(zone).date == key.date && deadline.toLocalDateTime(zone).date == key.date) {
        "Attendance window must remain on its calendar date"
    }
    return DayPlan(
        key,
        category,
        arrival,
        deadline,
        schedule.zoneId,
        scheduleVersion,
        place,
        key.date.dayOfWeek in schedule.days
    )
}
