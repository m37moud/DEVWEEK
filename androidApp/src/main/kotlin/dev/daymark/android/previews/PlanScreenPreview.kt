package dev.daymark.android.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.daymark.models.attendance.RegisteredPlace
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.plan.PlanState
import dev.daymark.models.plan.PlanChange
import dev.daymark.models.plan.TimeOffEntry
import dev.daymark.plan.TimeOffKind
import dev.daymark.plan.WeeklySchedule
import dev.daymark.presentation.features.plan.PlanActions
import dev.daymark.presentation.features.plan.PlanContent
import dev.daymark.presentation.features.plan.PlanUiState
import dev.daymark.settings.ThemePreference
import dev.daymark.ui.theme.DaymarkTheme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Clock
import kotlin.time.Instant


@Preview
@Preview(name = "Narrow large text", widthDp = 320, fontScale = 2f)
@Composable
private fun PlanScreenLightPreview() {
    val actions =
        PlanActions({}, {}, {})
    DaymarkTheme(ThemePreference.LIGHT) {
        PlanContent(samplePlanState(), Clock.System, null, actions)
    }
}

@Preview
@Composable
private fun PlanScreenDarkPreview() {
    val actions =
        PlanActions({}, {}, {})
    DaymarkTheme(ThemePreference.DARK) {
        PlanContent(samplePlanState(), Clock.System, null, actions)
    }
}

private fun samplePlanState(): PlanUiState {
    val draft = PlanChange(
        WeeklySchedule(
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
            zoneId = "UTC"
        ),
        TrackerCategory.WORK,
        RegisteredPlace(37.7749, -122.4194, 150, 1)
    )
    return PlanUiState(
        ready = true,
        plan = PlanState(draft, null, null),
        entries = listOf(
            TimeOffEntry(
                "sample-1",
                TimeOffKind.VACATION,
                LocalDate(2026, 11, 20),
                LocalDate(2026, 11, 25),
                Instant.fromEpochMilliseconds(0)
            ),
            TimeOffEntry(
                "sample-2",
                TimeOffKind.HOLIDAY,
                LocalDate(2026, 12, 25),
                LocalDate(2026, 12, 25),
                Instant.fromEpochMilliseconds(0)
            )
        )
    )
}
