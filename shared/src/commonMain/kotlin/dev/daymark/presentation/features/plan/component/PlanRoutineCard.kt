package dev.daymark.presentation.features.plan.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Clock
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.plan.WeeklySchedule
import dev.daymark.presentation.component.WeekdayOverrides
import dev.daymark.presentation.component.label
import dev.daymark.presentation.component.localTimeLabel
import dev.daymark.presentation.component.shortLabel
import dev.daymark.resources.Res
import dev.daymark.resources.arrival
import dev.daymark.resources.attendance_days
import dev.daymark.resources.attendance_zone
import dev.daymark.resources.grace
import dev.daymark.resources.grace_minutes
import dev.daymark.resources.local_save_failed
import dev.daymark.resources.override_arrival
import dev.daymark.resources.plan_insufficient
import dev.daymark.resources.plan_invalid
import dev.daymark.resources.plan_review
import dev.daymark.resources.plan_saved
import dev.daymark.resources.plan_stale
import dev.daymark.resources.preview_no_dates
import dev.daymark.resources.reset
import dev.daymark.resources.schedule
import dev.daymark.resources.setup_type
import dev.daymark.resources.weekday_overrides
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.isolated
import dev.daymark.settings.plural
import dev.daymark.settings.str
import dev.daymark.ui.theme.DaymarkTheme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlin.math.roundToInt

@Composable
internal fun PlanRoutineCard(
    category: TrackerCategory,
    schedule: WeeklySchedule,
    busy: Boolean,
    invalidInput: Boolean,
    result: PlanMutationResult?,
    onCategoryChange: (TrackerCategory) -> Unit,
    onDayToggle: (DayOfWeek) -> Unit,
    onPickArrival: () -> Unit,
    onGraceChange: (Int) -> Unit,
    onPickDayOverride: (DayOfWeek) -> Unit,
    onResetOverride: (DayOfWeek) -> Unit,
    onPickZone: () -> Unit,
    onReviewChanges: () -> Unit,
    modifier: Modifier = Modifier,
    appLanguage: AppLanguage = LocalAppLanguage.current,
) {
    val graceLabel = str(Res.string.grace)
    val graceMinutesText = plural(
        Res.plurals.grace_minutes,
        schedule.graceMinutes,
        isolated(DisplayFormats.number(schedule.graceMinutes, appLanguage))
    )

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
            Row(
                modifier = Modifier.fillMaxWidth(),
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

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = str(Res.string.attendance_days),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (day in DayOfWeek.entries) {
                        FilterChip(
                            selected = day in schedule.days,
                            enabled = !busy,
                            modifier = Modifier.testTag("plan-day-${day.name}"),
                            onClick = { onDayToggle(day) },
                            label = { Text(str(day.label())) }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                schedule.arrival,
                                appLanguage
                            )
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = graceLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = graceMinutesText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Slider(
                    value = schedule.graceMinutes.toFloat(),
                    onValueChange = { onGraceChange(it.roundToInt()) },
                    valueRange = 0f..120f,
                    steps = 119,
                    enabled = !busy,
                    modifier = Modifier
                        .testTag("plan-grace")
                        .semantics {
                            contentDescription = graceLabel
                            stateDescription = graceMinutesText
                        }
                )
            }

            WeekdayOverrides(
                schedule = schedule,
                busy = busy,
                onPickDayOverride = onPickDayOverride,
                appLanguage = appLanguage,
                onResetOverride = onResetOverride
            )

            OutlinedButton(
                enabled = !busy,
                onClick = onPickZone,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("plan-zone")
            ) {
                Text(str(Res.string.attendance_zone, isolated(schedule.zoneId)))
            }

            if (invalidInput) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = str(Res.string.plan_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            result?.let {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (it == PlanMutationResult.Applied)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = str(
                            when (it) {
                                PlanMutationResult.Applied -> Res.string.plan_saved
                                PlanMutationResult.StalePreview -> Res.string.plan_stale
                                PlanMutationResult.InsufficientBalance -> Res.string.plan_insufficient
                                PlanMutationResult.LocalSaveFailed -> Res.string.local_save_failed
                                is PlanMutationResult.Invalid -> Res.string.plan_invalid
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (it == PlanMutationResult.Applied)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Button(
                enabled = !busy,
                onClick = onReviewChanges,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("plan-review")
            ) {
                Text(str(Res.string.plan_review))
            }
        }
    }
}



@Preview
@Composable
private fun PlanRoutineCardPreview() {
    val sampleSchedule = WeeklySchedule(
        days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
        arrival = LocalTime(9, 0),
        overrides = mapOf(DayOfWeek.FRIDAY to LocalTime(8, 30)),
        graceMinutes = 10,
        zoneId = "UTC"
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
            onReviewChanges = {}
        )
    }
}
