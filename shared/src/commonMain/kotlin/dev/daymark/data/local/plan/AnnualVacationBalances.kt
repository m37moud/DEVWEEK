package dev.daymark.data.local.plan

import dev.daymark.data.local.PlanVersionEntity
import dev.daymark.history.AttendancePeriodSettings
import dev.daymark.history.VacationYearBasis
import dev.daymark.models.plan.AttendancePeriodBalanceChange
import dev.daymark.models.plan.AttendancePeriodReview
import dev.daymark.models.plan.PlanRejection
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.models.plan.TimeOffEntry
import dev.daymark.models.plan.VacationBalancePeriodChange
import dev.daymark.models.plan.VacationBalanceSummary
import dev.daymark.plan.deductibleVacationDates
import dev.daymark.plan.vacationBalanceSummary
import kotlinx.datetime.LocalDate

internal fun PlanningStore.balanceYear(year: Int): Int? =
    year.takeIf { policies.any { policy -> policy.effectiveYear <= year } }

private fun PlanningStore.vacationBalanceYear(date: LocalDate): Int? =
    if (date < firstDate) null else balanceYear(periodSettings.vacationYear(date))

internal fun PlanningStore.balances(
    entries: List<TimeOffEntry> = this.entries,
    expectations: List<PlanVersionEntity> = versions,
    throughYear: Int = vacationYear
): Map<Int?, VacationBalanceSummary> {
    val startsAsWork = versions.first().category == TrackerCategory.WORK.name
    val used = deductibleVacationDates(entries) { date ->
        if (date < firstDate) {
            // Explicit earlier vacation is charged without inventing a weekly schedule.
            startsAsWork
        } else {
            val day = plan(date, expectations)
            day.category == TrackerCategory.WORK && day.isScheduled
        }
    }.groupingBy { vacationBalanceYear(it) }.eachCount()
    val adjustmentDays = adjustments.groupBy { it.balanceYear }
        .mapValues { (_, period) -> period.sumOf { it.delta.toLong() } }
    val legacy = vacationBalanceSummary(tracker.openingBalance, adjustmentDays[null] ?: 0, used[null] ?: 0)
    val policiesByYear = policies.associateBy { it.effectiveYear }
    val firstYear = policiesByYear.keys.minOrNull() ?: return mapOf(null to legacy)
    val lastYear = maxOf(
        throughYear,
        policiesByYear.keys.max(),
        used.keys.filterNotNull().maxOrNull() ?: firstYear,
        adjustmentDays.keys.filterNotNull().maxOrNull() ?: firstYear
    )
    return buildMap {
        put(null, legacy)
        var policy = policiesByYear.getValue(firstYear)
        var previous = legacy
        for (year in firstYear..lastYear) {
            policy = policiesByYear[year] ?: policy
            val carryover = if (policy.carryOverUnusedDays) previous.remainingDays.coerceAtLeast(0) else 0
            val opening = policy.annualAllowanceDays.toLong() + carryover
            require(opening <= Int.MAX_VALUE)
            previous = vacationBalanceSummary(opening.toInt(), adjustmentDays[year] ?: 0, used[year] ?: 0, year)
            put(year, previous)
        }
    }
}

internal data class VacationBalanceImpact(
    val changes: List<VacationBalancePeriodChange>,
    val current: VacationBalancePeriodChange,
    val shortfall: VacationBalancePeriodChange?
)

internal fun PlanningStore.balanceImpact(
    dates: List<LocalDate>,
    entries: List<TimeOffEntry> = this.entries,
    expectations: List<PlanVersionEntity> = versions
): VacationBalanceImpact {
    val throughYear = maxOf(vacationYear, dates.lastOrNull()?.let(periodSettings::vacationYear) ?: vacationYear)
    val before = balances(throughYear = throughYear)
    val after = balances(entries, expectations, throughYear)
    val affectedYears = dates.mapTo(mutableSetOf()) { vacationBalanceYear(it) }
    val changes = after.map { (year, summary) ->
        val delta = summary.remainingDays.toLong() - (before[year]?.remainingDays ?: summary.remainingDays)
        require(delta in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
        VacationBalancePeriodChange(year, delta.toInt(), summary.remainingDays)
    }
    return VacationBalanceImpact(
        changes.filter { it.delta != 0 || it.year in affectedYears }.takeIf { list ->
            list.any { it.year != null } || balanceYear(vacationYear) != null
        }.orEmpty(),
        changes.first { it.year == balanceYear(vacationYear) },
        changes.firstOrNull { it.remainingDays < 0 && it.delta < 0 }
    )
}

internal fun Map<Int?, VacationBalanceSummary>.balanceShortfallComparedTo(
    before: Map<Int?, VacationBalanceSummary>
): Map.Entry<Int?, VacationBalanceSummary>? = entries.firstOrNull { (year, summary) ->
    summary.remainingDays < 0 && summary.remainingDays < (before[year]?.remainingDays ?: 0)
}

internal fun PlanningStore.reviewPeriod(settings: AttendancePeriodSettings): AttendancePeriodReview {
    val review = AttendancePeriodReview(settings, settings.periodFor(today), reviewToken, emptyList(), null)
    if (settings.vacationYearBasis != periodSettings.vacationYearBasis &&
        active.category != TrackerCategory.WORK.name
    ) return review.copy(rejection = PlanMutationResult.Invalid(PlanRejection.INVALID_VALUE))
    if (settings == periodSettings || policies.isEmpty() ||
        settings.vacationYearBasis == VacationYearBasis.CALENDAR &&
        periodSettings.vacationYearBasis == VacationYearBasis.CALENDAR
    ) return review

    val changed = copy(periodSettings = settings)
    val throughYear = maxOf(vacationYear, changed.vacationYear)
    val before = balances(throughYear = throughYear)
    val after = try {
        changed.balances(throughYear = throughYear)
    } catch (_: IllegalArgumentException) {
        return review.copy(rejection = PlanMutationResult.Invalid(PlanRejection.INVALID_VALUE))
    }
    val years = before.keys + after.keys
    val changes = years.mapNotNull { year ->
        val previous = before[year]
        val updated = after[year]
        val remaining = updated?.remainingDays ?: 0
        val usedDelta = (updated?.usedDays ?: 0) - (previous?.usedDays ?: 0)
        if (usedDelta == 0 && remaining == (previous?.remainingDays ?: 0)) null
        else AttendancePeriodBalanceChange(year, usedDelta, remaining)
    }
    val rejection = after.balanceShortfallComparedTo(before)?.let { insufficientVacationBalance(it.key) }
    return review.copy(balances = changes, rejection = rejection)
}

internal fun insufficientVacationBalance(year: Int?): PlanMutationResult =
    if (year == null) PlanMutationResult.InsufficientBalance
    else PlanMutationResult.InsufficientYear(year)
