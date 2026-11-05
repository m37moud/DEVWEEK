package dev.daymark.models

import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.models.attendance.DayPlan
import kotlinx.datetime.LocalDate

data class WeekDaySummary(val date: LocalDate, val status: AttendanceStatus?, val planned: Boolean)
data class TodayDashboard(
    val ready: Boolean = false, val failed: Boolean = false, val date: LocalDate? = null,
    val placeName: String = "", val present: Int = 0, val late: Int = 0, val absent: Int = 0,
    val week: List<WeekDaySummary> = emptyList(), val nextAttendance: DayPlan? = null
)
