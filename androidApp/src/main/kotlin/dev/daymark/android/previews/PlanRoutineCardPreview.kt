package dev.daymark.android.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.plan.WeeklySchedule
import dev.daymark.presentation.features.plan.component.PlanRoutineActions
import dev.daymark.presentation.features.plan.component.PlanRoutineCard
import dev.daymark.presentation.features.plan.component.PlanRoutineUiState
import dev.daymark.settings.ThemePreference
import dev.daymark.ui.theme.DaymarkTheme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

private val sampleSchedule = WeeklySchedule(
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

private val emptyActions = PlanRoutineActions(
    changeCategory = {},
    toggleDay = {},
    pickArrival = {},
    changeGrace = {},
    pickDayOverride = {},
    resetDayOverride = {},
    pickZone = {},
    reviewChanges = {},
    changeSchedule = {}
)

@Preview
@Composable
fun PlanRoutineCardPreview() {
    DaymarkTheme(ThemePreference.LIGHT) {
        PlanRoutineCard(
            state = PlanRoutineUiState(
                category = TrackerCategory.WORK,
                schedule = sampleSchedule,
                busy = false,
                invalidInput = false,
                result = null
            ),
            actions = emptyActions
        )
    }
}

@Preview
@Composable
fun PlanRoutineCardDarkPreview() {
    DaymarkTheme(ThemePreference.DARK) {
        PlanRoutineCard(
            state = PlanRoutineUiState(
                category = TrackerCategory.WORK,
                schedule = sampleSchedule,
                busy = false,
                invalidInput = false,
                result = null
            ),
            actions = emptyActions
        )
    }
}

@Preview
@Composable
fun PlanRoutineCardErrorPreview() {
    DaymarkTheme(ThemePreference.LIGHT) {
        PlanRoutineCard(
            state = PlanRoutineUiState(
                category = TrackerCategory.WORK,
                schedule = sampleSchedule,
                busy = false,
                invalidInput = true,
                result = PlanMutationResult.StalePreview
            ),
            actions = emptyActions
        )
    }
}
