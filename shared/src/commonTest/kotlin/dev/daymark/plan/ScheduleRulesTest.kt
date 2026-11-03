package dev.daymark.plan

import dev.daymark.acceptedStatus
import dev.daymark.at
import dev.daymark.attendance.AttendanceStatus
import dev.daymark.attendance.CheckMode
import dev.daymark.attendance.RegisteredPlace
import dev.daymark.fixtureDate
import dev.daymark.fixturePlan
import dev.daymark.fixtureSchedule
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class ScheduleRulesTest {
    @Test
    fun gap_uses_first_valid_instant_and_overlap_uses_earlier_occurrence() {
        assertEquals(
            Instant.parse("2026-03-08T07:00:00Z"),
            resolveLocalArrival(LocalDate(2026, 3, 8), LocalTime(2, 30), "America/New_York")
        )
        assertEquals(
            Instant.parse("2026-11-01T05:30:00Z"),
            resolveLocalArrival(LocalDate(2026, 11, 1), LocalTime(1, 30), "America/New_York")
        )
    }

    @Test
    fun weekday_override_and_zero_grace() {
        val schedule = fixtureSchedule(
            graceMinutes = 0,
            overrides = mapOf(DayOfWeek.THURSDAY to LocalTime(9, 30))
        )
        val p = fixturePlan(schedule, LocalDate(2026, 10, 29))
        assertEquals(Instant.parse("2026-10-29T09:30:00Z"), p.arrival)
        assertEquals(p.arrival, p.deadline)
        val normal = fixturePlan(schedule)
        assertEquals(at("09:00:00"), normal.arrival)
        assertEquals(
            AttendanceStatus.PRESENT,
            acceptedStatus(normal, "09:00:00", CheckMode.USER_INITIATED)
        )
        assertEquals(
            AttendanceStatus.LATE,
            acceptedStatus(normal, "09:00:00.001", CheckMode.USER_INITIATED)
        )
    }

    @Test
    fun attendance_zone_and_gregorian_date_do_not_follow_display_zone() {
        val p = fixturePlan(fixtureSchedule(zoneId = "Asia/Tokyo"))
        assertEquals(Instant.parse("2026-10-27T00:00:00Z"), p.arrival)
        assertEquals(
            LocalDate(2026, 10, 26),
            p.arrival.toLocalDateTime(TimeZone.of("America/Los_Angeles")).date
        )
        assertEquals("2026-10-27", p.key.date.toString())
        assertEquals("Asia/Tokyo", p.zoneId)
    }

    @Test
    fun non_hour_offset_and_subsecond_resolution() {
        assertEquals(
            Instant.parse("2026-10-27T03:15:00Z"),
            resolveLocalArrival(fixtureDate, LocalTime(9, 0), "Asia/Kathmandu")
        )
        assertEquals(
            Instant.parse("2026-10-27T09:00:00.000000001Z"),
            resolveLocalArrival(fixtureDate, LocalTime(9, 0, 0, 1), "UTC")
        )
    }

    @Test
    fun invalid_schedule_and_place_inputs_are_rejected() {
        assertFailsWith<IllegalArgumentException> { fixtureSchedule(days = emptySet()) }
        assertFailsWith<IllegalArgumentException> { fixtureSchedule(graceMinutes = -1) }
        assertFailsWith<IllegalArgumentException> { fixtureSchedule(graceMinutes = 121) }
        assertFailsWith<IllegalArgumentException> { fixtureSchedule(zoneId = "Not/AZone") }
        assertFailsWith<IllegalArgumentException> {
            fixtureSchedule(
                days = setOf(DayOfWeek.MONDAY),
                overrides = mapOf(DayOfWeek.TUESDAY to LocalTime(10, 0))
            )
        }
        assertFailsWith<IllegalArgumentException> { RegisteredPlace(Double.NaN, 0.0, 100, 1) }
        assertFailsWith<IllegalArgumentException> { RegisteredPlace(0.0, 181.0, 100, 1) }
        assertFailsWith<IllegalArgumentException> { RegisteredPlace(0.0, 0.0, 99, 1) }
        assertFailsWith<IllegalArgumentException> { RegisteredPlace(0.0, 0.0, 501, 1) }
    }

    @Test
    fun midnight_crossing_grace_is_rejected_in_the_single_date_mvp() {
        assertFailsWith<IllegalArgumentException> {
            fixturePlan(
                fixtureSchedule(
                    arrival = LocalTime(
                        23,
                        59
                    ), graceMinutes = 5
                )
            )
        }
        assertEquals(
            Instant.parse("2026-10-27T23:59:00Z"),
            fixturePlan(fixtureSchedule(arrival = LocalTime(23, 59), graceMinutes = 0)).arrival
        )
    }
}
