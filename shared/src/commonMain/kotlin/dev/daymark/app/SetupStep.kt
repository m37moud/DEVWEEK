package dev.daymark.app

import dev.daymark.attendance.TrackerCategory
import dev.daymark.attendance.DayKey
import dev.daymark.attendance.RegisteredPlace
import dev.daymark.plan.WeeklySchedule
import dev.daymark.plan.resolveDay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import dev.daymark.plan.parseWholeDays
import dev.daymark.reporting.exceedsReportTextLimit

enum class SetupStep { WELCOME, PROFILE, PLACE, SCHEDULE, REVIEW }

sealed interface SetupDraftLoad {
    data object Absent : SetupDraftLoad
    data class Loaded(val draft: SetupDraft) : SetupDraftLoad
    data object Unreadable : SetupDraftLoad
}

internal fun SetupDraft.invalidStep(now: Instant): SetupStep? = when {
    displayName.isBlank() || exceedsReportTextLimit(displayName) || category == null -> SetupStep.PROFILE
    placeName.isBlank() || place == null -> SetupStep.PLACE
    schedule == null || (category == TrackerCategory.WORK && parseWholeDays(openingBalanceText) == null) -> SetupStep.SCHEDULE
    schedule.firstSetupArrival(now, category, place) == null -> SetupStep.SCHEDULE
    else -> null
}

internal fun SetupDraft.restoredStep(now: Instant): SetupStep {
    val invalid = invalidStep(now)
    return if (invalid != null && invalid.ordinal < step.ordinal) invalid else step
}

internal fun WeeklySchedule.firstSetupArrival(
    now: Instant,
    category: TrackerCategory,
    place: RegisteredPlace
): Instant? = try {
    val date = now.toLocalDateTime(TimeZone.of(zoneId)).date
    (0..7).map { date.plus(it, DateTimeUnit.DAY) }.map {
        resolveDay(DayKey("setup", it), this, category, place, 1)
    }.firstOrNull { it.isScheduled && it.arrival >= now }?.arrival
} catch (_: IllegalArgumentException) {
    null
}
