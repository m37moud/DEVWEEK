package dev.daymark.plan

import dev.daymark.at
import dev.daymark.fixtureDate
import dev.daymark.fixturePlan
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class NextAttendanceTest {
    private class Plans(var start: Instant = at("08:00:00"), var enabled: Boolean = true) :
        PlanReader {
        var calls = 0
        val skipped = mutableMapOf<LocalDate, Int>()
        override suspend fun context() =
            ScheduleContext(fixturePlan().key.trackerId, "UTC", start, enabled)

        override suspend fun day(date: LocalDate): PlannedDay {
            calls++
            val flag = skipped[date]
            return PlannedDay(
                fixturePlan(date = date).copy(isScheduled = flag != 1),
                if (flag == 2) TimeOffKind.HOLIDAY else null, flag == 3, flag == 4
            )
        }
    }

    @Test
    fun recovery_during_grace_keeps_today_but_after_deadline_skips_it() = runTest {
        val plans = Plans()
        assertEquals(fixtureDate, nextAttendance(at("09:02:00"), plans)?.key?.date)
        assertEquals(fixtureDate, nextAttendance(at("09:05:00"), plans)?.key?.date)
        assertEquals(
            fixtureDate.plus(1, DateTimeUnit.DAY),
            nextAttendance(at("09:05:00.000000001"), plans)?.key?.date
        )
    }

    @Test
    fun installation_after_arrival_skips_today_without_creating_an_absence() = runTest {
        val plans = Plans(start = at("09:02:00"))
        assertEquals(
            fixtureDate.plus(1, DateTimeUnit.DAY),
            nextAttendance(at("09:02:00"), plans)?.key?.date
        )
    }

    @Test
    fun off_days_confirmed_dates_and_consumed_attempts_are_skipped() = runTest {
        val plans = Plans()
        for (offset in 0..3) plans.skipped[fixtureDate.plus(offset, DateTimeUnit.DAY)] = offset + 1
        assertEquals(
            fixtureDate.plus(4, DateTimeUnit.DAY),
            nextAttendance(at("09:02:00"), plans)?.key?.date
        )
    }

    @Test
    fun disabled_automation_never_projects_a_date() = runTest {
        val plans = Plans(enabled = false)
        assertNull(nextAttendance(at("09:02:00"), plans))
        assertEquals(0, plans.calls)
    }

    @Test
    fun no_available_occurrence_is_bounded() = runTest {
        val reader = object : PlanReader {
            override suspend fun context() =
                ScheduleContext(fixturePlan().key.trackerId, "UTC", at("08:00:00"), true)

            override suspend fun day(date: LocalDate) =
                PlannedDay(fixturePlan(date = date).copy(isScheduled = false), null, false, false)
        }
        assertNull(nextAttendance(at("09:02:00"), reader))
    }
}
