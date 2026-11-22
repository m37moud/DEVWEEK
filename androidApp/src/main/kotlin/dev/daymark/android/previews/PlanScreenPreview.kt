package dev.daymark.android.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.daymark.models.attendance.RegisteredPlace
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.plan.PlanChange
import dev.daymark.models.plan.PlanState
import dev.daymark.models.plan.TimeOffEntry
import dev.daymark.models.plan.TimeOffPreview
import dev.daymark.models.plan.VacationBalancePeriodChange
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

@Preview(name = "Vacation before tracking began")
@Composable
private fun PlanScreenBeforeTrackingPreview() {
    val review = TimeOffPreview(
        1, emptyList(), emptyList(), -1, 9, null,
        preTrackingDates = listOf(LocalDate(2026, 11, 19))
    )
    DaymarkTheme(ThemePreference.LIGHT) {
        PlanContent(
            samplePlanState().copy(timeOffPreview = review),
            Clock.System,
            null,
            PlanActions({}, {}, {})
        )
    }
}

@Preview(name = "Vacation across annual balance periods")
@Composable
private fun PlanScreenAnnualReviewPreview() {
    val review = TimeOffPreview(
        expectedRevision = 1,
        affectedDays = emptyList(),
        resultingOutcomes = emptyList(),
        balanceDelta = -3,
        availableAfter = 8,
        rejection = null,
        balancePeriods = listOf(
            VacationBalancePeriodChange(null, -2, 8),
            VacationBalancePeriodChange(2027, -1, 19)
        )
    )
    DaymarkTheme(ThemePreference.LIGHT) {
        PlanContent(samplePlanState().copy(timeOffPreview = review), Clock.System, null, PlanActions({}, {}, {}))
    }
}

@Preview(name = "Work mission editor", widthDp = 320, fontScale = 1.5f)
@Composable
private fun WorkMissionEditorPreview() {
    DaymarkTheme(ThemePreference.DARK) {
        PlanContent(
            samplePlanState(),
            Clock.System,
            LocalDate(2026, 11, 19),
            PlanActions({}, {}, {}),
            initialWorkMission = true
        )
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
                "sample-mission", TimeOffKind.WORK_MISSION,
                LocalDate(2026, 11, 19), LocalDate(2026, 11, 19),
                Instant.fromEpochMilliseconds(0), "Client visit"
            ),
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
