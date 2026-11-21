package dev.daymark.data.local.settings

import dev.daymark.history.AttendancePeriodSettings
import dev.daymark.history.VacationYearBasis

internal const val ATTENDANCE_PERIOD_KEY = "attendance-period"

internal fun attendancePeriodSettings(stored: String?): AttendancePeriodSettings {
    val parts = stored.orEmpty().split(':', limit = 2)
    val closingDay = parts.first().toIntOrNull()?.takeIf { it in 1..31 }
    val basis = if (parts.getOrNull(1) == VacationYearBasis.ATTENDANCE_PERIOD.name)
        VacationYearBasis.ATTENDANCE_PERIOD else VacationYearBasis.CALENDAR
    return AttendancePeriodSettings(closingDay, basis)
}

internal fun AttendancePeriodSettings.storedValue(): String =
    "${closingDay ?: ""}:${vacationYearBasis.name}"
