package dev.daymark.presentation.features.settings.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Check
import compose.icons.fontawesomeicons.solid.Clock
import dev.daymark.app.reducedMotionEnabled
import dev.daymark.history.AttendancePeriodSettings
import dev.daymark.history.VacationYearBasis
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.presentation.component.AttendancePeriodEditor
import dev.daymark.presentation.component.AttendancePeriodEditorState
import dev.daymark.presentation.component.PlanMutationFeedback
import dev.daymark.presentation.component.attendancePeriodDates
import dev.daymark.presentation.component.attendancePeriodRuleLabel
import dev.daymark.presentation.features.settings.SettingsActions
import dev.daymark.presentation.features.settings.SettingsUiState
import dev.daymark.resources.Res
import dev.daymark.resources.attendance_period
import dev.daymark.resources.attendance_period_vacation_year
import dev.daymark.resources.cancel
import dev.daymark.resources.plan_review
import dev.daymark.resources.preview_title
import dev.daymark.resources.vacation_year_calendar
import dev.daymark.resources.vacation_year_period
import dev.daymark.settings.str

@Composable
fun SettingsAttendancePeriodCard(
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    initiallyEditing: Boolean = false
) {
    var editing by rememberSaveable { mutableStateOf(value = initiallyEditing) }
    SettingsEditorCard(
        header = SettingsEditorHeader(
            title = Res.string.attendance_period,
            editTag = "settings-attendance-period-edit",
            closeLabel = Res.string.cancel,
            editing = editing,
            enabled = !state.busy && !state.deleting
        ),
        onToggle = {
            if (editing) actions.dismissPeriodReview()
            editing = !editing
        },
        modifier = modifier
    ) {
        if (editing) {
            AttendancePeriodDraftEditor(state = state, actions = actions, onSave = { editing = false })
        } else {
            AttendancePeriodSummary(state = state)
        }
    }
}

@Composable
private fun AttendancePeriodSummary(
    state: SettingsUiState,
    modifier: Modifier = Modifier
) {
    val saved = state.settings.attendancePeriod
    val activePeriod = state.attendanceDate?.let(saved::periodFor)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(space = 12.dp)
    ) {
        AttendancePeriodActiveRange(
            rangeText = if (activePeriod == null) attendancePeriodRuleLabel(closingDay = saved.closingDay)
            else attendancePeriodDates(period = activePeriod)
        )
        if (state.annualVacation.configuration != null) {
            val yearResource = if (saved.vacationYearBasis == VacationYearBasis.ATTENDANCE_PERIOD) {
                Res.string.vacation_year_period
            } else {
                Res.string.vacation_year_calendar
            }
            AttendancePeriodSummaryRow(
                icon = FontAwesomeIcons.Solid.Clock,
                category = str(resource = Res.string.attendance_period_vacation_year),
                badgeText = str(resource = yearResource)
            )
        }
    }
}

@Composable
private fun AttendancePeriodSummaryRow(
    icon: ImageVector,
    category: String,
    badgeText: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(size = 16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = category,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun AttendancePeriodActiveRange(
    rangeText: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                modifier = Modifier.size(size = 14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = rangeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AttendancePeriodDraftEditor(
    state: SettingsUiState,
    actions: SettingsActions,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val saved = state.settings.attendancePeriod
    val enabled = !state.busy && !state.deleting
    val reducedMotion = reducedMotionEnabled()
    var closingDay by rememberSaveable { mutableStateOf(value = saved.closingDay) }
    var periodYear by rememberSaveable {
        mutableStateOf(value = saved.vacationYearBasis == VacationYearBasis.ATTENDANCE_PERIOD)
    }
    var reviewed by rememberSaveable { mutableStateOf(value = false) }
    var submitted by rememberSaveable { mutableStateOf(value = false) }
    val draft = AttendancePeriodSettings(
        closingDay = closingDay,
        vacationYearBasis = if (periodYear) VacationYearBasis.ATTENDANCE_PERIOD else VacationYearBasis.CALENDAR
    )
    val applied =
        submitted && state.periodSaveResult == PlanMutationResult.Applied && saved == draft
    val closeEditor by rememberUpdatedState(newValue = onSave)
    LaunchedEffect(key1 = applied) {
        if (applied) closeEditor()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(space = 16.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        ) {
            Box(modifier = Modifier.padding(all = 16.dp)) {
                AttendancePeriodEditor(
                    state = AttendancePeriodEditorState(
                        rule = draft,
                        enabled = enabled,
                        showVacationYear = state.annualVacation.configuration != null
                    ),
                    onChange = { updated ->
                        closingDay = updated.closingDay
                        periodYear = updated.vacationYearBasis == VacationYearBasis.ATTENDANCE_PERIOD
                        reviewed = false
                        submitted = false
                    }
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
        ) {
            Column(
                modifier = Modifier.padding(all = 16.dp),
                verticalArrangement = Arrangement.spacedBy(space = 12.dp)
            ) {
                AttendancePeriodReviewButton(
                    enabled = enabled && draft != saved,
                    busy = state.busy || state.deleting,
                    onClick = {
                        reviewed = true
                        actions.previewPeriod(draft)
                    }
                )
                AttendancePeriodMutationFeedback(
                    visible = reviewed && state.periodSaveResult != PlanMutationResult.Applied,
                    mutation = state.periodSaveResult,
                    reducedMotion = reducedMotion
                )
                AttendancePeriodUnsavedChangesHint(visible = draft != saved, reducedMotion = reducedMotion)
            }
        }
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
}

@Composable
private fun AttendancePeriodMutationFeedback(
    visible: Boolean,
    mutation: PlanMutationResult?,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = if (reducedMotion) {
            EnterTransition.None
        } else {
            fadeIn() + expandVertically()
        },
        exit = if (reducedMotion) {
            ExitTransition.None
        } else {
            fadeOut() + shrinkVertically()
        },
        modifier = modifier
    ) {
        PlanMutationFeedback(mutation = mutation)
    }
}

@Composable
private fun AttendancePeriodUnsavedChangesHint(
    visible: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = if (reducedMotion) {
            EnterTransition.None
        } else {
            fadeIn() + expandVertically()
        },
        exit = if (reducedMotion) {
            ExitTransition.None
        } else {
            fadeOut() + shrinkVertically()
        },
        modifier = modifier
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(size = 18.dp)
                )
                Text(
                    text = str(resource = Res.string.preview_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
private fun AttendancePeriodReviewButton(
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .testTag(tag = "attendance-period-review")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp)
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(size = 14.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = FontAwesomeIcons.Solid.Check,
                    contentDescription = null,
                    modifier = Modifier.size(size = 12.dp)
                )
            }
            Text(text = str(resource = Res.string.plan_review))
        }
    }
}
