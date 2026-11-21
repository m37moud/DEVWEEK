package dev.daymark.history

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals

class AttendancePeriodTest {
    @Test
    fun eitherBoundaryProducesTheSameRecurringRule() {
        for (closingDay in 1..31) {
            val rule = AttendancePeriodSettings(closingDay)
            assertEquals(rule, AttendancePeriodSettings().withStartDay(rule.startDay))
        }
        val start = AttendancePeriodSettings(10).withStartDay(21)
        assertEquals(20, start.closingDay)
        assertEquals(6, start.copy(closingDay = 5).startDay)
        val january = start.periodFor(LocalDate(2026, 12, 21))
        assertEquals(LocalDate(2027, 1, 20), january.through)
        assertEquals(
            AttendancePeriodSettings().period(january.month),
            start.withStartDay(1).period(january.month)
        )
    }

    @Test
    fun closingDayDeterminesInclusiveRangeAndLabel() {
        val settings = AttendancePeriodSettings(20)
        val november = settings.periodFor(LocalDate(2026, 11, 20))
        assertEquals(LocalDate(2026, 11, 1), november.month)
        assertEquals(LocalDate(2026, 10, 21), november.from)
        assertEquals(LocalDate(2026, 11, 20), november.through)
        assertEquals(LocalDate(2026, 12, 1), settings.periodFor(LocalDate(2026, 11, 21)).month)
    }

    @Test
    fun shortMonthsCloseWithoutGaps() {
        val settings = AttendancePeriodSettings(30)
        for (year in listOf(2024, 2026)) {
            val february = settings.period(LocalDate(year, 2, 1))
            assertEquals(if (year == 2024) 29 else 28, february.through.day)
            val march = settings.period(LocalDate(year, 3, 1))
            assertEquals(february.through.plus(1, DateTimeUnit.DAY), march.from)
            assertEquals(march, settings.periodFor(march.from))
            assertEquals(
                AttendancePeriodSettings().period(february.month),
                AttendancePeriodSettings(31).period(february.month)
            )
        }
    }

    @Test
    fun vacationBasisChangesOnlyWhenSelected() {
        val december = LocalDate(2026, 12, 21)
        assertEquals(2026, AttendancePeriodSettings(20).vacationYear(december))
        val settings = AttendancePeriodSettings(20, VacationYearBasis.ATTENDANCE_PERIOD)
        assertEquals(2027, settings.vacationYear(december))
        assertEquals(december, settings.vacationYearStart(2027))
    }
}
