package dev.daymark

import dev.daymark.attendance.*
import dev.daymark.location.LocationResult
import dev.daymark.plan.*
import kotlinx.datetime.*
import kotlin.time.Instant
import kotlin.time.Duration.Companion.seconds

val fixtureDate: LocalDate = LocalDate(2026, 10, 27)
fun at(time: String): Instant = Instant.parse("2026-10-27T${time}Z")
fun fixtureSchedule(days: Set<DayOfWeek> = DayOfWeek.entries.toSet(), arrival: LocalTime = LocalTime(9, 0),
    graceMinutes: Int = 5, zoneId: String = "UTC", overrides: Map<DayOfWeek, LocalTime> = emptyMap()) =
    WeeklySchedule(days, arrival, overrides, graceMinutes, zoneId)
fun fixturePlace() = RegisteredPlace(0.0, 0.0, 100, 1)
fun fixturePlan(schedule: WeeklySchedule = fixtureSchedule(), date: LocalDate = fixtureDate) =
    resolveDay(DayKey("f8428c3a-1347-40dc-9667-af38e69c6801", date), schedule, TrackerCategory.WORK, fixturePlace(), 1)
fun fixtureFix(capturedAt: Instant = at("09:02:00"), ageSeconds: Long = 0,
    accuracy: Double = 10.0, latitude: Double = 0.0, longitude: Double = 0.0) =
    LocationResult.Fix(latitude, longitude, accuracy, capturedAt, capturedAt + ageSeconds.seconds, ageSeconds.seconds)
fun acceptedStatus(plan: DayPlan, time: String, mode: CheckMode): AttendanceStatus =
    (classifyFix(plan, fixtureFix(capturedAt = at(time)), mode, at(time)) as FixVerdict.Accepted).status
