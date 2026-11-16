package dev.daymark.android.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.plan.WeeklySchedule
import dev.daymark.presentation.features.plan.component.PlanRoutineCard
import dev.daymark.settings.ThemePreference
import dev.daymark.ui.theme.DaymarkTheme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime


@Preview
@Composable
 fun PlanRoutineCardPreview() {
    val sampleSchedule = WeeklySchedule(
        days = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY
        ),
        arrival = LocalTime(9, 0),
        overrides = mapOf(DayOfWeek.FRIDAY to LocalTime(8, 30)),
        graceMinutes = 10,
        zoneId = "UTC",
        departure = LocalTime(17, 0)
    )
    DaymarkTheme(ThemePreference.LIGHT) {
        PlanRoutineCard(
            category = TrackerCategory.WORK,
            schedule = sampleSchedule,
            busy = false,
            invalidInput = false,
            result = null,
            onCategoryChange = {},
            onDayToggle = {},
            onPickArrival = {},
            onGraceChange = {},
            onPickDayOverride = {},
            onResetOverride = {},
            onPickZone = {},
            onReviewChanges = {},
            onScheduleChange = {}
        )
    }
}
