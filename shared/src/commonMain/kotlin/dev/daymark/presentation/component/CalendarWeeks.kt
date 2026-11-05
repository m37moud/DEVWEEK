package dev.daymark.presentation.component

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** ISO Monday-first display grid; only actual repository records supply historical outcomes. */
fun calendarWeeks(year: Int, month: Int): List<List<LocalDate?>> {
    val first = LocalDate(year, month, 1)
    val last = first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).day
    val offset = first.dayOfWeek.ordinal
    val cells = ((offset + last + 6) / 7) * 7
    return List(cells) { index ->
        (index - offset + 1).takeIf { it in 1..last }?.let { LocalDate(year, month, it) }
    }.chunked(7)
}
