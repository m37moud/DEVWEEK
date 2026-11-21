package dev.daymark.presentation.component

import dev.daymark.plan.dates
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** ISO Monday-first display grid; only actual repository records supply historical outcomes. */
fun calendarWeeks(year: Int, month: Int): List<List<LocalDate?>> {
    val first = LocalDate(year, month, 1)
    return calendarWeeks(first, first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY))
}

internal fun calendarWeeks(from: LocalDate, through: LocalDate): List<List<LocalDate?>> {
    val days = List<LocalDate?>(from.dayOfWeek.ordinal) { null } + dates(from, through).toList()
    return (days + List<LocalDate?>((7 - days.size % 7) % 7) { null }).chunked(7)
}
