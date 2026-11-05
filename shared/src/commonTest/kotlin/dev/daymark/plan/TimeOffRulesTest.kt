package dev.daymark.plan

import dev.daymark.data.local.plan.entry
import dev.daymark.fixturePlan
import dev.daymark.fixtureSchedule
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.plan.TimeOffEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

class TimeOffRulesTest {
    private val weekdays = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY
    )

    private fun entry(id: String, kind: TimeOffKind, from: String, through: String = from) =
        TimeOffEntry(
            id,
            kind,
            LocalDate.parse(from),
            LocalDate.parse(through),
            Instant.parse("2026-11-01T12:00:00Z")
        )

    private fun plan(date: LocalDate) = fixturePlan(fixtureSchedule(days = weekdays), date)

    @Test
    fun holiday_refund_and_overlapping_vacations_use_distinct_eligible_dates() {
        val vacation = entry("v", TimeOffKind.VACATION, "2026-11-02", "2026-11-03")
        val holiday = entry("h", TimeOffKind.HOLIDAY, "2026-11-03")
        assertEquals(10, availableVacationDays(12, emptyList(), listOf(vacation), ::plan))
        assertEquals(11, availableVacationDays(12, emptyList(), listOf(vacation, holiday), ::plan))
        assertEquals(
            10,
            availableVacationDays(
                12,
                emptyList(),
                listOf(vacation, vacation.copy(id = "overlap")),
                ::plan
            )
        )
        assertEquals(10, availableVacationDays(12, emptyList(), listOf(vacation), ::plan))
    }

    @Test
    fun weekends_and_non_work_categories_do_not_consume_balance() {
        val weekend = entry("weekend", TimeOffKind.VACATION, "2026-11-07", "2026-11-08")
        assertTrue(eligibleVacationDates(listOf(weekend), ::plan).isEmpty())
        val workdays = entry("days", TimeOffKind.VACATION, "2026-11-02", "2026-11-03")
        assertTrue(eligibleVacationDates(listOf(workdays)) { plan(it).copy(category = TrackerCategory.SCHOOL) }.isEmpty())
        assertTrue(eligibleVacationDates(listOf(workdays)) { plan(it).copy(category = TrackerCategory.COURSE) }.isEmpty())
    }

    @Test
    fun signed_adjustments_are_preserved_without_annual_reset() {
        val dates = listOf(entry("year-end", TimeOffKind.VACATION, "2026-12-31", "2027-01-04"))
        assertEquals(12, availableVacationDays(12, listOf(4, -1), dates, ::plan))
    }

    @Test
    fun invalid_range_and_negative_opening_are_rejected() {
        assertFailsWith<IllegalArgumentException> {
            entry(
                "bad",
                TimeOffKind.VACATION,
                "2026-11-03",
                "2026-11-02"
            )
        }
        assertFailsWith<IllegalArgumentException> {
            availableVacationDays(
                -1,
                emptyList(),
                emptyList(),
                ::plan
            )
        }
    }

    @Test
    fun whole_day_input_accepts_localized_digits_but_rejects_fractions_and_negative_values() {
        assertEquals(12, parseWholeDays("١٢"))
        assertEquals(12, parseWholeDays("۱۲"))
        assertEquals(0, parseWholeDays("0"))
        for (input in listOf("", "-1", "1.5", "١٫٥", "12.0", "1e3", "2147483648")) assertNull(
            parseWholeDays(input)
        )
    }
}
