package dev.daymark.reminders

import dev.daymark.*
import dev.daymark.attendance.*
import dev.daymark.plan.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.*
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Instant

class ReminderPlannerTest {
    private class Reader : PlanReader {
        var schedule = fixtureSchedule()
        val leave = mutableMapOf<LocalDate, TimeOffKind>()
        val confirmed = mutableSetOf<LocalDate>()
        override suspend fun context() = ScheduleContext(fixturePlan().key.trackerId, schedule.zoneId, at("08:00:00"), false)
        override suspend fun day(date: LocalDate) = PlannedDay(fixturePlan(schedule, date), leave[date], date in confirmed, false)
    }
    private val english = ReminderCopy("en", "Arrival time", "Open Daymark to check in")
    @Test fun horizon_is_thirty_calendar_dates_not_thirty_occurrences() = runTest {
        val reminders = planReminders(at("08:00:00"), Reader(), english)
        assertEquals(30, reminders.size)
        assertEquals("2026-10-27", reminders.first().attendanceDate)
        assertEquals("2026-11-25", reminders.last().attendanceDate)
        assertEquals(30, reminders.map { it.id }.toSet().size)
        assertEquals(reminders.map { it.id }, planReminders(at("08:00:00"), Reader(), english).map { it.id })
    }
    @Test fun thursday_override_leave_and_confirmation_change_only_eligible_dates() = runTest {
        val reader = Reader()
        reader.schedule = fixtureSchedule(overrides = mapOf(DayOfWeek.THURSDAY to LocalTime(10, 30)))
        reader.leave[LocalDate(2026, 10, 28)] = TimeOffKind.HOLIDAY
        reader.leave[LocalDate(2026, 10, 30)] = TimeOffKind.VACATION
        reader.confirmed += LocalDate(2026, 10, 31)
        val reminders = planReminders(at("08:00:00"), reader, english)
        assertEquals(27, reminders.size)
        assertEquals(Instant.parse("2026-10-29T10:30:00Z").toEpochMilliseconds(), reminders.single { it.attendanceDate == "2026-10-29" }.fireAtEpochMillis)
        assertTrue(reminders.none { it.attendanceDate in setOf("2026-10-28", "2026-10-30", "2026-10-31") })
    }
    @Test fun an_already_passed_arrival_has_no_immediate_grace_deadline_duplicate() = runTest {
        val reminders = planReminders(at("09:02:00"), Reader(), english)
        assertEquals(29, reminders.size)
        assertEquals("2026-10-28", reminders.first().attendanceDate)
        assertEquals("2026-11-25", reminders.last().attendanceDate)
    }
    @Test fun language_change_preserves_instants_and_replacement_identity() = runTest {
        val reader = Reader().apply { schedule = fixtureSchedule(zoneId = "Asia/Kathmandu") }
        val before = planReminders(at("08:00:00"), reader, english)
        val arabic = planReminders(at("08:00:00"), reader, ReminderCopy("ar", "حان وقت الحضور", "افتح التطبيق لتسجيل الحضور"))
        assertEquals(before.map { it.fireAtEpochMillis }, arabic.map { it.fireAtEpochMillis })
        assertEquals(before.map { it.id }, arabic.map { it.id })
        assertTrue(arabic.all { it.localeTag == "ar" })
    }
    @Test fun replacement_envelope_identifies_owner_even_when_the_horizon_is_empty() = runTest {
        val id = fixturePlan().key.trackerId
        val payload = Json.parseToJsonElement(encodeReminders(id, emptyList())).jsonObject
        assertEquals(id, payload.getValue("trackerId").jsonPrimitive.content)
        assertTrue(payload.getValue("reminders").jsonArray.isEmpty())
    }
}
