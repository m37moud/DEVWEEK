package dev.daymark.presentation.features.setup.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.daymark.app.HostPlatform
import dev.daymark.app.SetupStep
import dev.daymark.presentation.component.attendancePeriodRuleLabel
import dev.daymark.presentation.component.vacationYearBasisLabel
import dev.daymark.resources.attendance_period
import dev.daymark.app.openingBalanceDays
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.setup.SetupDraft
import dev.daymark.plan.WeeklySchedule
import dev.daymark.presentation.component.label
import dev.daymark.presentation.component.localTimeLabel
import dev.daymark.presentation.component.shortLabel
import dev.daymark.presentation.features.setup.SetupUiState
import dev.daymark.resources.Res
import dev.daymark.resources.attendance_zone_label
import dev.daymark.resources.automation_help
import dev.daymark.resources.departure_none
import dev.daymark.resources.desktop_manual_help
import dev.daymark.resources.google_optional
import dev.daymark.resources.grace
import dev.daymark.resources.grace_minutes
import dev.daymark.resources.ios_manual_help
import dev.daymark.resources.onboarding_edit_section
import dev.daymark.resources.onboarding_extras_help
import dev.daymark.resources.onboarding_place
import dev.daymark.resources.onboarding_profile
import dev.daymark.resources.opening_balance
import dev.daymark.resources.place_ready
import dev.daymark.resources.reminder_horizon
import dev.daymark.resources.schedule
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.isolated
import dev.daymark.settings.plural
import dev.daymark.settings.str
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

@Composable
internal fun SetupReview(state: SetupUiState, platform: HostPlatform, onEdit: (SetupStep) -> Unit) {
    val editEnabled = !state.interactionBusy && !state.completed
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SetupReviewSection(
            title = str(Res.string.attendance_period),
            editEnabled = editEnabled,
            onEdit = { onEdit(SetupStep.ATTENDANCE_PERIOD) }
        ) {
            Text(attendancePeriodRuleLabel(state.draft.attendanceClosingDay))
            if (state.draft.category == TrackerCategory.WORK) {
                Text(vacationYearBasisLabel(state.draft.vacationYearBasis))
            }
        }

        SetupReviewSection(
            title = str(Res.string.onboarding_profile),
            editEnabled = editEnabled,
            onEdit = { onEdit(SetupStep.PROFILE) }
        ) {
            Text(
                text = isolated(state.draft.displayName),
                style = MaterialTheme.typography.bodyLarge
            )
            state.draft.category?.let {
                Text(
                    text = str(it.label()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SetupReviewSection(
            title = str(Res.string.onboarding_place),
            editEnabled = editEnabled,
            onEdit = { onEdit(SetupStep.PLACE) }
        ) {
            Text(
                text = isolated(state.draft.placeName),
                style = MaterialTheme.typography.bodyLarge
            )
            state.draft.place?.let {
                Text(
                    text = str(
                        Res.string.place_ready,
                        isolated(DisplayFormats.number(it.radiusMetres, LocalAppLanguage.current))
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        SetupReviewSection(
            title = str(Res.string.schedule),
            editEnabled = editEnabled,
            onEdit = { onEdit(SetupStep.SCHEDULE) }
        ) {
            ScheduleSummary(state.draft)
        }

        SetupReviewHint(platform)
    }
}

@Composable
private fun SetupReviewHint(platform: HostPlatform) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = str(
                        when (platform) {
                            HostPlatform.ANDROID -> Res.string.automation_help
                            HostPlatform.IOS -> Res.string.ios_manual_help
                            HostPlatform.TEST -> Res.string.desktop_manual_help
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (platform == HostPlatform.IOS) {
                Text(
                    text = str(Res.string.reminder_horizon),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = str(Res.string.google_optional),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (platform != HostPlatform.TEST) {
                Text(
                    text = str(Res.string.onboarding_extras_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SetupReviewSection(
    title: String,
    editEnabled: Boolean,
    onEdit: () -> Unit,
    content: @Composable () -> Unit
) {
    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedButton(
                    onClick = onEdit,
                    enabled = editEnabled,
//                    modifier = Modifier.size(24.dp),
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = str(Res.string.onboarding_edit_section, title),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            content()
        }
    }
}

internal data class ScheduleDayDetails(
    val day: DayOfWeek,
    val arrival: LocalTime,
    val departure: LocalTime?,
    val index: Int = 0
)

@Composable
internal fun ScheduleSummary(
    draft: SetupDraft,
    modifier: Modifier = Modifier,
    appLanguage: AppLanguage = LocalAppLanguage.current
) {
    val schedule = draft.schedule ?: return
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(schedule) {
        entered = true
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val days = remember(schedule.days) { schedule.days.sortedBy { it.ordinal } }
        days.forEachIndexed { index, day ->
            val arrival = schedule.overrides[day] ?: schedule.arrival
            val departure = if (schedule.departureOverrides.containsKey(day)) {
                schedule.departureOverrides[day]
            } else {
                schedule.departure
            }
            ScheduleDayRow(
                details = ScheduleDayDetails(day, arrival, departure, index),
                appLanguage = appLanguage,
                visible = entered
            )
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            modifier = Modifier.padding(vertical = 4.dp)
        )

        val openingBalance = if (draft.category == TrackerCategory.WORK) {
            draft.openingBalanceDays()
        } else {
            null
        }

        AnimatedVisibility(
            visible = entered,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                    expandVertically(spring(stiffness = Spring.StiffnessMediumLow))
        ) {
            ScheduleMetadataChips(
                schedule = schedule,
                openingBalance = openingBalance,
                appLanguage = appLanguage
            )
        }
    }
}

@Composable
private fun ScheduleDayRow(
    details: ScheduleDayDetails,
    appLanguage: AppLanguage,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val delayMillis = details.index * 40
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            tween(
                durationMillis = 300,
                delayMillis = delayMillis,
                easing = FastOutSlowInEasing
            )
        ) +
                expandVertically(
                    tween(
                        durationMillis = 300,
                        delayMillis = delayMillis,
                        easing = FastOutSlowInEasing
                    )
                ),
        modifier = modifier.fillMaxWidth()
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScheduleDayBadge(details.day)
                Text(
                    text = str(details.day.label()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                ScheduleDayTimes(
                    arrival = details.arrival,
                    departure = details.departure,
                    appLanguage = appLanguage
                )
            }
        }
    }
}

@Composable
private fun ScheduleDayBadge(day: DayOfWeek, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.size(28.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = str(day.shortLabel()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun ScheduleDayTimes(
    arrival: LocalTime,
    departure: LocalTime?,
    appLanguage: AppLanguage,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScheduleArrivalBadge(
            timeText = localTimeLabel(arrival, appLanguage)
        )
        val departureText = departure?.let { localTimeLabel(it, appLanguage) }
            ?: str(Res.string.departure_none)
        ScheduleCutoffBadge(text = departureText)
    }
}

@Composable
private fun ScheduleArrivalBadge(timeText: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ScheduleCutoffBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ScheduleMetadataChips(
    schedule: WeeklySchedule,
    openingBalance: Int?,
    appLanguage: AppLanguage,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ScheduleStatChip(
            icon = Icons.Default.Info,
            text = str(Res.string.grace) ,
            highlightedText = plural(
                Res.plurals.grace_minutes,
                schedule.graceMinutes,
                isolated(DisplayFormats.number(schedule.graceMinutes, appLanguage))
            )
        )
        ScheduleStatChip(
            icon = Icons.Default.LocationOn,
            text = str(Res.string.attendance_zone_label),
            highlightedText = isolated(schedule.zoneId)
        )
        if (openingBalance != null) {
            ScheduleStatChip(
                icon = Icons.Default.DateRange,
                text = str(Res.string.opening_balance),
                highlightedText = isolated(DisplayFormats.number(openingBalance, appLanguage))
            )
        }
    }
}

@Composable
private fun ScheduleStatChip(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    highlightedText: String? = null,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

            }
            if (highlightedText != null) {
                Surface(
                    //                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = CircleShape
                ) {
                    Text(
                        text = highlightedText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,

                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
