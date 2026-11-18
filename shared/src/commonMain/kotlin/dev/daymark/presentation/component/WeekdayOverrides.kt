package dev.daymark.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.daymark.plan.WeeklySchedule
import dev.daymark.resources.Res
import dev.daymark.resources.override_arrival
import dev.daymark.resources.preview_no_dates
import dev.daymark.resources.reset_day_override
import dev.daymark.resources.weekday_overrides
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.str
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

@Composable
internal fun WeekdayOverrides(
    schedule: WeeklySchedule,
    busy: Boolean,
    onPickDayOverride: (DayOfWeek) -> Unit,
    onResetOverride: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
    appLanguage: AppLanguage = LocalAppLanguage.current
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {


        val scheduledDays =
            rememberSaveable(schedule.days) { DayOfWeek.entries.filter { it in schedule.days } }

        if (scheduledDays.isEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = str(Res.string.preview_no_dates),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()

                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = str(Res.string.weekday_overrides),
                            style = MaterialTheme.typography.labelLarge,
//                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = str(Res.string.override_arrival),
                            style = MaterialTheme.typography.bodySmall,
//                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }

                    AddOverrideDay(
                        days = scheduledDays.filterNot { it in schedule.overrides },
                        busy = busy,
                        onSelect = onPickDayOverride,
                    )

                }
                for (day in scheduledDays.filter { it in schedule.overrides }) {
                    val overrideTime = schedule.overrides.getValue(day)
                    OverrideDayItem(
                        busy = busy,
                        onPickDayOverride = onPickDayOverride,
                        day = day,
                        overrideTime = overrideTime,
                        appLanguage = appLanguage,
//                        onResetOverride = onResetOverride
                        actions = {
                            IconButton(
                                onClick = { onResetOverride(day) },
                                enabled = !busy,
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = str(
                                        Res.string.reset_day_override,
                                        str(day.label())
                                    ),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    )

//                    WeekdayOverrideItem(
//                        busy = busy,
//                        onPickDayOverride = onPickDayOverride,
//                        day = day,
//                        overrideTime = overrideTime,
//                        appLanguage = appLanguage,
//                        onResetOverride = onResetOverride
//                    )
                }
            }
        }
    }
}

@Composable
private fun WeekdayOverrideItem(
    day: DayOfWeek,
    busy: Boolean,
    onPickDayOverride: (DayOfWeek) -> Unit,
    overrideTime: LocalTime,
    appLanguage: AppLanguage,
    onResetOverride: (DayOfWeek) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy) { onPickDayOverride(day) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = str(day.shortLabel()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),

                ) {
                Text(
                    text = str(day.label()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = localTimeLabel(overrideTime, appLanguage),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )


            }

            IconButton(
                onClick = { onResetOverride(day) },
                enabled = !busy,
                modifier = Modifier.size(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = str(
                        Res.string.reset_day_override,
                        str(day.label())
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

