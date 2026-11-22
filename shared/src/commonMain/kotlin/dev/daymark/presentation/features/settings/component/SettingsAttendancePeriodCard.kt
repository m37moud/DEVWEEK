package dev.daymark.presentation.features.settings.component

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.daymark.history.AttendancePeriodSettings
import dev.daymark.history.VacationYearBasis
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.presentation.component.AttendancePeriodEditor
import dev.daymark.presentation.component.AttendancePeriodEditorState
import dev.daymark.presentation.component.PlanMutationFeedback
import dev.daymark.presentation.component.attendancePeriodRuleLabel
import dev.daymark.presentation.features.settings.SettingsActions
import dev.daymark.presentation.features.settings.SettingsUiState
import dev.daymark.resources.Res
import dev.daymark.resources.attendance_period
import dev.daymark.resources.cancel
import dev.daymark.resources.plan_review
import dev.daymark.resources.vacation_year_calendar
import dev.daymark.resources.vacation_year_period
import dev.daymark.settings.str

@Composable
internal fun SettingsAttendancePeriodCard(state: SettingsUiState, actions: SettingsActions) {
    var editing by rememberSaveable { mutableStateOf(false) }
    SettingsEditorCard(
        SettingsEditorHeader(
            Res.string.attendance_period, "settings-attendance-period-edit", Res.string.cancel, editing,
            !state.busy && !state.deleting
        ),
        onToggle = { editing = !editing }
    ) {
        if (editing) AttendancePeriodDraftEditor(state, actions) { editing = false }
        else AttendancePeriodSummary(state)
    }
}

@Composable
private fun AttendancePeriodSummary(state: SettingsUiState) {
    val saved = state.settings.attendancePeriod
    SettingsSummaryText(attendancePeriodRuleLabel(saved.closingDay))
    if (state.annualVacation.configuration != null) {
        val yearLabel = if (saved.vacationYearBasis == VacationYearBasis.ATTENDANCE_PERIOD) {
            Res.string.vacation_year_period
        } else {
            Res.string.vacation_year_calendar
        }
        SettingsSummaryText(str(yearLabel))
    }
}

@Composable
private fun AttendancePeriodDraftEditor(state: SettingsUiState, actions: SettingsActions, onSaved: () -> Unit) {
    val saved = state.settings.attendancePeriod
    val enabled = !state.busy && !state.deleting
    var closingDay by rememberSaveable { mutableStateOf(saved.closingDay) }
    var periodYear by rememberSaveable { mutableStateOf(saved.vacationYearBasis == VacationYearBasis.ATTENDANCE_PERIOD) }
    var reviewed by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val draft = AttendancePeriodSettings(
        closingDay,
        if (periodYear) VacationYearBasis.ATTENDANCE_PERIOD else VacationYearBasis.CALENDAR
    )
    val applied = submitted && state.periodSaveResult == PlanMutationResult.Applied && saved == draft
    LaunchedEffect(applied) {
        if (applied) onSaved()
    }

    AttendancePeriodEditor(
        state = AttendancePeriodEditorState(draft, enabled, state.annualVacation.configuration != null),
        onChange = { updated ->
            closingDay = updated.closingDay
            periodYear = updated.vacationYearBasis == VacationYearBasis.ATTENDANCE_PERIOD
            reviewed = false
            submitted = false
        }
    )
    if (reviewed && state.periodSaveResult != PlanMutationResult.Applied) {
        PlanMutationFeedback(state.periodSaveResult)
    }
    Button(
        onClick = {
            reviewed = true
            actions.previewPeriod(draft)
        },
        enabled = enabled && draft != saved,
        modifier = Modifier.testTag("attendance-period-review")
    ) { Text(str(Res.string.plan_review)) }

    val review = state.periodReview
    if (reviewed && review != null) {
        AttendancePeriodReviewDialog(
            review = review,
            enabled = enabled,
            onConfirm = {
                submitted = true
                actions.confirmPeriodChange()
            },
            onDismiss = actions.dismissPeriodReview
        )
    }
}
