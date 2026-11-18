package dev.daymark.presentation.features.settings.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Clock
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.plan.WeeklySchedule
import dev.daymark.presentation.component.DepartureCutoffEditor
import dev.daymark.presentation.component.ValueSliderControl
import dev.daymark.presentation.component.ValueSliderState
import dev.daymark.presentation.component.WeekdayOverrides
import dev.daymark.presentation.component.label
import dev.daymark.presentation.component.localTimeLabel
import dev.daymark.resources.Res
import dev.daymark.resources.arrival
import dev.daymark.resources.attendance_days
import dev.daymark.resources.attendance_zone
import dev.daymark.resources.grace
import dev.daymark.resources.grace_minutes
import dev.daymark.resources.schedule
import dev.daymark.resources.setup_type
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.isolated
import dev.daymark.settings.plural
import dev.daymark.settings.str
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

data class PlanRoutineUiState(
    val category: TrackerCategory,
    val schedule: WeeklySchedule,
    val busy: Boolean
)

data class PlanRoutineActions(
    val changeCategory: (TrackerCategory) -> Unit,
    val toggleDay: (DayOfWeek) -> Unit,
    val pickArrival: () -> Unit,
    val changeGrace: (Int) -> Unit,
    val pickDayOverride: (DayOfWeek) -> Unit,
    val resetDayOverride: (DayOfWeek) -> Unit,
    val pickZone: () -> Unit,
    val changeSchedule: (WeeklySchedule) -> Unit
)

@Composable
fun PlanRoutineCard(
    state: PlanRoutineUiState,
    actions: PlanRoutineActions,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PlanRoutineHeader()

            PlanRoutineCategorySelector(
                category = state.category,
                busy = state.busy,
                onCategoryChange = actions.changeCategory
            )

            PlanRoutineDaysSelector(
                days = state.schedule.days,
                busy = state.busy,
                onDayToggle = actions.toggleDay
            )

            PlanRoutineArrivalButton(
                arrival = state.schedule.arrival,
                busy = state.busy,
                onPickArrival = actions.pickArrival
            )

            PlanRoutineGraceSlider(
                graceMinutes = state.schedule.graceMinutes,
                busy = state.busy,
                onGraceChange = actions.changeGrace
            )

            WeekdayOverrides(
                schedule = state.schedule,
                busy = state.busy,
                onPickDayOverride = actions.pickDayOverride,
                onResetOverride = actions.resetDayOverride
            )
            // Divider & Visual Legend Strip
            HorizontalDivider(
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            DepartureCutoffEditor(
                schedule = state.schedule,
                busy = state.busy,
                onChange = actions.changeSchedule
            )

            PlanRoutineZoneButton(
                zoneId = state.schedule.zoneId,
                busy = state.busy,
                onPickZone = actions.pickZone
            )

        }
    }
}

@Composable
private fun PlanRoutineHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.DateRange,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = str(Res.string.schedule),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun PlanRoutineCategorySelector(
    category: TrackerCategory,
    busy: Boolean,
    onCategoryChange: (TrackerCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = str(Res.string.setup_type),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (entry in TrackerCategory.entries) {
                FilterChip(
                    selected = category == entry,
                    enabled = !busy,
                    onClick = { onCategoryChange(entry) },
                    label = { Text(str(entry.label())) }
                )
            }
        }
    }
}

@Composable
private fun PlanRoutineDaysSelector(
    days: Set<DayOfWeek>,
    busy: Boolean,
    onDayToggle: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = str(Res.string.attendance_days),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (day in DayOfWeek.entries) {
                FilterChip(
                    selected = day in days,
                    enabled = !busy,
                    modifier = Modifier.testTag("plan-day-${day.name}"),
                    onClick = { onDayToggle(day) },
                    label = { Text(str(day.label())) }
                )
            }
        }
    }
}

@Composable
private fun PlanRoutineArrivalButton(
    arrival: LocalTime,
    busy: Boolean,
    onPickArrival: () -> Unit,
    modifier: Modifier = Modifier
) {
    val language = LocalAppLanguage.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = str(Res.string.arrival),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            enabled = !busy,
            onClick = onPickArrival,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("plan-arrival")
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = FontAwesomeIcons.Solid.Clock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = str(Res.string.arrival) + " · " + localTimeLabel(
                        arrival,
                        language
                    )
                )
            }
        }
    }
}

@Composable
private fun PlanRoutineGraceSlider(
    graceMinutes: Int,
    busy: Boolean,
    onGraceChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val language = LocalAppLanguage.current
    val graceLabel = str(Res.string.grace)
    val graceMinutesText = plural(
        Res.plurals.grace_minutes,
        graceMinutes,
        isolated(DisplayFormats.number(graceMinutes, language))
    )

    ValueSliderControl(
        state = ValueSliderState(
            label = graceLabel,
            value = graceMinutes,
            formattedValue = graceMinutesText,
            min = 0,
            max = 120,
            step = 5,
            sliderSteps = 119,
            enabled = !busy,
            testTag = "plan-grace"
        ),
        onValueChange = onGraceChange,
        modifier = modifier
    )
}

@Composable
private fun PlanRoutineZoneButton(
    zoneId: String,
    busy: Boolean,
    onPickZone: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        enabled = !busy,
        onClick = onPickZone,
        modifier = modifier
            .fillMaxWidth()
            .testTag("plan-zone")
    ) {
        Text(str(Res.string.attendance_zone, isolated(zoneId)))
    }
}
