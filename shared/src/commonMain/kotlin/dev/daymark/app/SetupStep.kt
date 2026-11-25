package dev.daymark.app

import dev.daymark.models.attendance.DayKey
import dev.daymark.models.attendance.RegisteredPlace
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.setup.SetupDraft
import dev.daymark.plan.WeeklySchedule
import dev.daymark.plan.parseWholeDays
import dev.daymark.plan.resolveDay
import dev.daymark.reporting.exceedsReportTextLimit
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

enum class SetupStep { WELCOME, PREFERENCE, PROFILE, ATTENDANCE_PERIOD, PLACE, SCHEDULE, AUTOMATION, REVIEW }

internal fun setupSteps(platform: HostPlatform) =
    if (platform == HostPlatform.ANDROID) SetupStep.entries else SetupStep.entries - SetupStep.AUTOMATION

sealed interface SetupDraftLoad {
    data object Absent : SetupDraftLoad
    data class Loaded(val draft: SetupDraft) : SetupDraftLoad
    data object Unreadable : SetupDraftLoad
}

internal fun SetupDraft.invalidStep(now: Instant): SetupStep? = when {
    displayName.isBlank() || exceedsReportTextLimit(displayName) || category == null -> SetupStep.PROFILE
    placeName.isBlank() || place == null -> SetupStep.PLACE
    schedule == null || openingBalanceDays() == null -> SetupStep.SCHEDULE
    schedule.firstSetupArrival(now, category, place) == null -> SetupStep.SCHEDULE
    else -> null
}

internal fun SetupDraft.openingBalanceDays(): Int? = when {
    category != TrackerCategory.WORK || openingBalanceText.isBlank() -> 0
    else -> parseWholeDays(openingBalanceText)
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
