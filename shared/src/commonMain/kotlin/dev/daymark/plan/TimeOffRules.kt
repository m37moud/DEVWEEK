package dev.daymark.plan

import dev.daymark.models.attendance.DayPlan
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.plan.TimeOffEntry
import dev.daymark.models.plan.VacationBalanceSummary
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

internal fun dates(from: LocalDate, through: LocalDate): Sequence<LocalDate> = sequence {
    var date = from
    while (date <= through) {
        yield(date); if (date == through) break; date = date.plus(1, DateTimeUnit.DAY)
    }
}

private val dayEntryPriority = listOf(TimeOffKind.HOLIDAY, TimeOffKind.VACATION, TimeOffKind.WORK_MISSION)

internal fun dayEntry(
    entries: List<TimeOffEntry>,
    date: LocalDate,
    category: TrackerCategory? = null
): TimeOffEntry? = dayEntryPriority.firstNotNullOfOrNull { kind ->
    entries.firstOrNull {
        it.kind == kind && date in it.from..it.through &&
                (kind != TimeOffKind.WORK_MISSION || category == TrackerCategory.WORK)
    }
}

internal fun timeOffOn(entries: List<TimeOffEntry>, date: LocalDate): TimeOffKind? =
    dayEntry(entries, date)?.kind

fun eligibleVacationDates(
    entries: List<TimeOffEntry>,
    planForDate: (LocalDate) -> DayPlan
): Set<LocalDate> = deductibleVacationDates(entries) { date ->
    val plan = planForDate(date)
    plan.category == TrackerCategory.WORK && plan.isScheduled
}

internal fun deductibleVacationDates(
    entries: List<TimeOffEntry>,
    isChargeable: (LocalDate) -> Boolean
): Set<LocalDate> =
    entries.asSequence().filter { it.kind == TimeOffKind.VACATION }
        .flatMap { dates(it.from, it.through) }
        .distinct()
        .filter { date -> isChargeable(date) && timeOffOn(entries, date) != TimeOffKind.HOLIDAY }
        .toSet()

fun availableVacationDays(
    opening: Int,
    adjustments: List<Int>,
    entries: List<TimeOffEntry>,
    planForDate: (LocalDate) -> DayPlan
): Int {
    return vacationBalanceSummary(opening, adjustments, entries, planForDate).remainingDays
}

internal fun vacationBalanceSummary(
    opening: Int,
    adjustments: List<Int>,
    entries: List<TimeOffEntry>,
    planForDate: (LocalDate) -> DayPlan
): VacationBalanceSummary = vacationBalanceSummary(
    opening,
    adjustments.sumOf { it.toLong() },
    eligibleVacationDates(entries, planForDate).size
)

internal fun vacationBalanceSummary(
    opening: Int,
    adjustments: Long,
    usedDays: Int,
    year: Int? = null
): VacationBalanceSummary {
    require(opening >= 0)
    val remainingDays = opening.toLong() + adjustments - usedDays
    require(remainingDays in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
    return VacationBalanceSummary(opening, usedDays, remainingDays.toInt(), year)
}

fun parseWholeDays(text: String): Int? {
    val value = text.trim()
    if (value.isEmpty()) return null
    val digits = StringBuilder()
    for (character in value) digits.append(character.digitToIntOrNull() ?: return null)
    return digits.toString().toIntOrNull()
}
