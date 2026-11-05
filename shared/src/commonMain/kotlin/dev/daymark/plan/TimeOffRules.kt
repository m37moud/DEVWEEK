package dev.daymark.plan

import dev.daymark.models.attendance.DayPlan
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.plan.TimeOffEntry
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

internal fun dates(from: LocalDate, through: LocalDate): Sequence<LocalDate> = sequence {
    var date = from
    while (date <= through) {
        yield(date); if (date == through) break; date = date.plus(1, DateTimeUnit.DAY)
    }
}

internal fun timeOffOn(entries: List<TimeOffEntry>, date: LocalDate): TimeOffKind? = when {
    entries.any { it.kind == TimeOffKind.HOLIDAY && date in it.from..it.through } -> TimeOffKind.HOLIDAY
    entries.any { it.kind == TimeOffKind.VACATION && date in it.from..it.through } -> TimeOffKind.VACATION
    else -> null
}

fun eligibleVacationDates(
    entries: List<TimeOffEntry>,
    planForDate: (LocalDate) -> DayPlan
): Set<LocalDate> =
    entries.asSequence().filter { it.kind == TimeOffKind.VACATION }
        .flatMap { dates(it.from, it.through) }
        .distinct().filter { date ->
            val plan = planForDate(date)
            plan.category == TrackerCategory.WORK && plan.isScheduled && timeOffOn(
                entries,
                date
            ) != TimeOffKind.HOLIDAY
        }.toSet()

fun availableVacationDays(
    opening: Int,
    adjustments: List<Int>,
    entries: List<TimeOffEntry>,
    planForDate: (LocalDate) -> DayPlan
): Int {
    require(opening >= 0)
    val value = opening.toLong() + adjustments.sumOf { it.toLong() } - eligibleVacationDates(
        entries,
        planForDate
    ).size
    require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
    return value.toInt()
}

fun parseWholeDays(text: String): Int? {
    val value = text.trim()
    if (value.isEmpty()) return null
    val digits = StringBuilder()
    for (character in value) digits.append(character.digitToIntOrNull() ?: return null)
    return digits.toString().toIntOrNull()
}
