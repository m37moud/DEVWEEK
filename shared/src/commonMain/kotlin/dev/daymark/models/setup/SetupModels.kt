package dev.daymark.models.setup

import dev.daymark.attendance.DEFAULT_AUTOMATIC_ATTEMPT_LIMIT
import dev.daymark.app.SetupStep
import dev.daymark.history.VacationYearBasis
import dev.daymark.models.attendance.RegisteredPlace
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.plan.WeeklySchedule

data class SetupDraft(
    val displayName: String = "",
    val category: TrackerCategory? = null,
    val placeName: String = "",
    val place: RegisteredPlace? = null,
    val schedule: WeeklySchedule? = null,
    val openingBalanceText: String = "",
    val step: SetupStep = SetupStep.WELCOME,
    val attendanceClosingDay: Int? = null,
    val vacationYearBasis: VacationYearBasis = VacationYearBasis.CALENDAR,
    val automaticEnabled: Boolean = false,
    val automaticAttemptLimit: Int = DEFAULT_AUTOMATIC_ATTEMPT_LIMIT,
    val resultNotificationsEnabled: Boolean = false
) {
    override fun toString() = "SetupDraft(metadata=<redacted>)"
}

data class SetupCommand(
    val displayName: String, val category: TrackerCategory, val placeName: String,
    val place: RegisteredPlace, val schedule: WeeklySchedule, val openingBalance: Int,
    val attendanceClosingDay: Int? = null,
    val vacationYearBasis: VacationYearBasis = VacationYearBasis.CALENDAR,
    val automaticEnabled: Boolean = false,
    val automaticAttemptLimit: Int? = null,
    val resultNotificationsEnabled: Boolean? = null
) {
    override fun toString() = "SetupCommand(metadata=<redacted>)"
}

sealed interface SetupResult {
    data object Completed : SetupResult
    data object Invalid : SetupResult
    data object LocalSaveFailed : SetupResult
}
