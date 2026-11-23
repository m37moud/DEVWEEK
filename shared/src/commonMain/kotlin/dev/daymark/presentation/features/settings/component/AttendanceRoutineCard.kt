package dev.daymark.presentation.features.settings.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Clock
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.plan.PlanChange
import dev.daymark.models.plan.PlanState
import dev.daymark.presentation.component.label
import dev.daymark.presentation.component.localTimeLabel
import dev.daymark.presentation.component.shortLabel
import dev.daymark.presentation.features.settings.AttendanceSettingsUiState
import dev.daymark.resources.Res
import dev.daymark.resources.arrival
import dev.daymark.resources.attendance_zone_label
import dev.daymark.resources.cancel
import dev.daymark.resources.grace_minutes
import dev.daymark.resources.plan_review
import dev.daymark.resources.schedule
import dev.daymark.resources.settings_summary_value
import dev.daymark.resources.status_day_off
import dev.daymark.resources.status_scheduled
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.isolated
import dev.daymark.settings.plural
import dev.daymark.settings.str
import kotlinx.datetime.DayOfWeek

@Composable
internal fun AttendanceRoutineCard(
    plan: PlanState,
    draft: PlanChange?,
    state: AttendanceSettingsUiState,
    actions: AttendanceSettingsActions,
    editing: Boolean,
    onToggleEditing: (() -> Unit)?,
    onPickZone: () -> Unit,
    onPickTime: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsEditorCard(
        header = SettingsEditorHeader(
            title = Res.string.schedule,
            editTag = "settings-routine-edit",
            closeLabel = Res.string.cancel,
            editing = editing,
            enabled = !state.busy && onToggleEditing != null
        ),
        onToggle = onToggleEditing ?: {},
        modifier = modifier
    ) {
        if (editing && draft != null) {
            AttendanceRoutineEditor(
                state = state,
                draft = draft,
                actions = actions,
                onPickZone = onPickZone,
                onPickTime = onPickTime
            )
        } else {
            AttendanceRoutineSummary(plan = plan)
        }
    }
}

@Composable
private fun AttendanceRoutineSummary(
    plan: PlanState,
    modifier: Modifier = Modifier
) {
    val saved = plan.pending ?: plan.current
    val language = LocalAppLanguage.current
    val arrivalTimeText = isolated(value = localTimeLabel(time = saved.schedule.arrival, language = language))

    val graceText = if (saved.schedule.graceMinutes > 0) {
        plural(
            resource = Res.plurals.grace_minutes,
            quantity = saved.schedule.graceMinutes,
            isolated(value = DisplayFormats.number(value = saved.schedule.graceMinutes, language = language))
        )
    } else {
        null
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(space = 14.dp)
    ) {
        SummaryCategoryHeader(category = saved.category)

        SummaryWeekdaysRow(activeDays = saved.schedule.days)

        SummaryInfoTile(
            icon = FontAwesomeIcons.Solid.Clock,
            title = str(resource = Res.string.arrival),
            badgeText = arrivalTimeText,
            secondaryText = graceText
        )

        SummaryInfoTile(
            icon = Icons.Default.DateRange,
            title = str(resource = Res.string.attendance_zone_label),
            badgeText = isolated(value = saved.schedule.zoneId)
        )

        AttendancePlanEffectiveDate(plan = plan)
    }
}

@Composable
private fun AttendanceRoutineEditor(
    state: AttendanceSettingsUiState,
    draft: PlanChange,
    actions: AttendanceSettingsActions,
    onPickZone: () -> Unit,
    onPickTime: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(space = 16.dp)
    ) {
        PlanRoutineContent(
            state = PlanRoutineUiState(
                category = draft.category,
                schedule = draft.schedule,
                busy = state.busy
            ),
            actions = PlanRoutineActions(
                changeCategory = { newCategory ->
                    actions.edit { it.copy(category = newCategory) }
                },
                toggleDay = { day ->
                    actions.edit {
                        val days =
                            if (day in it.schedule.days) it.schedule.days - day else it.schedule.days + day
                        it.copy(
                            schedule = it.schedule.copy(
                                days = days,
                                overrides = it.schedule.overrides.filterKeys { selected -> selected in days },
                                departureOverrides = it.schedule.departureOverrides.filterKeys { selected -> selected in days }
                            )
                        )
                    }
                },
                pickArrival = { onPickTime("default") },
                changeGrace = { minutes ->
                    actions.edit {
                        it.copy(schedule = it.schedule.copy(graceMinutes = minutes))
                    }
                },
                pickDayOverride = { day -> onPickTime(day.name) },
                resetDayOverride = { day ->
                    actions.edit {
                        it.copy(schedule = it.schedule.copy(overrides = it.schedule.overrides - day))
                    }
                },
                pickZone = onPickZone,
                changeSchedule = { schedule -> actions.edit { it.copy(schedule = schedule) } }
            )
        )

        AttendanceEditorReviewFooter(
            state = state,
            draft = draft,
            actions = actions,
            reviewTestTag = "plan-review",
            reviewButtonText = str(resource = Res.string.plan_review)
        )
    }
}

@Composable
private fun SummaryCategoryHeader(
    category: TrackerCategory,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(size = 18.dp)
            )
            Text(
                text = str(resource = Res.string.schedule),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        ) {
            Text(
                text = str(resource = category.label()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SummaryWeekdaysRow(
    activeDays: Set<DayOfWeek>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape = MaterialTheme.shapes.medium)
            .background(color = MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DayOfWeek.entries.forEach { day ->
            val isActive = day in activeDays
            val status = if (isActive) Res.string.status_scheduled else Res.string.status_day_off
            val description = str(Res.string.settings_summary_value, str(day.label()), str(status))
            Surface(
                modifier = Modifier
                    .weight(weight = 1f)
                    .heightIn(min = 34.dp),
                shape = MaterialTheme.shapes.small,
                color = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                border = if (isActive) {
                    null
                } else {
                    BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
            ) {
                Box(
                    modifier = Modifier.clearAndSetSemantics { contentDescription = description },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = str(resource = day.shortLabel()),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        }
                    )
                }
            }
        }
    }
}
