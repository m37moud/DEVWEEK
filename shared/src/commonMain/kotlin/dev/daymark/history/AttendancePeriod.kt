package dev.daymark.history

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

enum class VacationYearBasis { CALENDAR, ATTENDANCE_PERIOD }

data class AttendancePeriod(val month: LocalDate, val from: LocalDate, val through: LocalDate)

data class AttendancePeriodSettings(
    val closingDay: Int? = null,
    val vacationYearBasis: VacationYearBasis = VacationYearBasis.CALENDAR
) {
    init {
        require(closingDay == null || closingDay in 1..31)
    }

    val startDay: Int get() = (closingDay ?: 31) % 31 + 1

    fun withStartDay(day: Int): AttendancePeriodSettings {
        require(day in 1..31)
        return copy(closingDay = if (day == 1) 31 else day - 1)
    }

    fun period(month: LocalDate): AttendancePeriod {
        val label = LocalDate(month.year, month.month, 1)
        val previous = label.minus(1, DateTimeUnit.MONTH)
        return AttendancePeriod(label, close(previous).plus(1, DateTimeUnit.DAY), close(label))
    }

    fun periodFor(date: LocalDate): AttendancePeriod {
        val current = period(date)
        return if (date <= current.through) current else period(current.month.plus(1, DateTimeUnit.MONTH))
    }

    fun vacationYear(date: LocalDate): Int = when (vacationYearBasis) {
        VacationYearBasis.CALENDAR -> date.year
        VacationYearBasis.ATTENDANCE_PERIOD -> periodFor(date).month.year
    }

    fun vacationYearStart(year: Int): LocalDate {
        val january = LocalDate(year, 1, 1)
        return when (vacationYearBasis) {
            VacationYearBasis.CALENDAR -> january
            VacationYearBasis.ATTENDANCE_PERIOD -> period(january).from
        }
    }

    private fun close(month: LocalDate): LocalDate {
        val last = month.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
        return LocalDate(month.year, month.month, minOf(closingDay ?: 31, last.day))
    }
}
