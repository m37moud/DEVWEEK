package dev.daymark.presentation.component

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Clock
import compose.icons.fontawesomeicons.solid.Edit
import compose.icons.fontawesomeicons.solid.Info
import compose.icons.fontawesomeicons.solid.Redo
import dev.daymark.plan.WeeklySchedule
import dev.daymark.resources.Res
import dev.daymark.resources.departure_cutoff
import dev.daymark.resources.departure_explanation
import dev.daymark.resources.departure_none
import dev.daymark.resources.departure_set
import dev.daymark.resources.info
import dev.daymark.resources.no_day_cutoff
import dev.daymark.resources.reset_day_override
import dev.daymark.resources.set_day_cutoff
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.str
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

@Composable
fun DepartureCutoffEditor(
    schedule: WeeklySchedule,
    busy: Boolean,
    onChange: (WeeklySchedule) -> Unit,
    modifier: Modifier = Modifier,
    appLanguage: AppLanguage = LocalAppLanguage.current,
) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var isInfoShow by rememberSaveable { mutableStateOf(false) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            DepartureCutoffTitle(
                modifier = Modifier.weight(1f),
                onShowInfoClick = { isInfoShow = !isInfoShow }
            )
            AddOverrideDay(
                days = schedule.days.sortedBy { it.ordinal }
                    .filterNot { it in schedule.departureOverrides },
                busy = busy,
                onSelect = { editing = it.name },
            )
        }
        AnimatedVisibility(visible = isInfoShow, label = "Departure Cutoff Info") {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = str(Res.string.departure_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }




        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { editing = "default" },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
                    .weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = FontAwesomeIcons.Solid.Clock,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )

                    Text(
                        text = schedule.departure?.let {
                            localTimeLabel(
                                time = it,
                                language = appLanguage
                            )
                        }
                            ?: str(Res.string.departure_set))
                }
            }
            AnimatedVisibility(visible = schedule.departure != null) {
                OutlinedButton(
                    onClick = { onChange(schedule.copy(departure = null)) },
                    enabled = !busy,
                ) {
                    Icon(
                        imageVector = FontAwesomeIcons.Solid.Redo,
                        contentDescription = str(Res.string.departure_none),
                        modifier = Modifier.size(16.dp)

                    )

                }
            }
        }



        for (
        day in schedule.days.sortedBy { it.ordinal }
            .filter { it in schedule.departureOverrides }
        ) {
            val cutoff = schedule.departureOverrides[day]
            OverrideDayItem(
                day = day,
                busy = busy,
                overrideTime = cutoff,
                appLanguage = appLanguage,
                onPickDayOverride = {
                    editing = day.name
                },
                actions = {

                    IconButton(
                        onClick = {
                            editing = day.name
                        },
                        enabled = !busy,
                        modifier = Modifier.size(16.dp)

                    ) {
                        Icon(
                            FontAwesomeIcons.Solid.Clock,
                            contentDescription = str(Res.string.set_day_cutoff, str(day.label())),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (schedule.departure != null) {
                        IconButton(
                            onClick = {
                                onChange(schedule.copy(departureOverrides = schedule.departureOverrides + (day to null)))
                            },
                            enabled = !busy,
                            modifier = Modifier.size(16.dp)

                        ) {
                            Icon(
                                FontAwesomeIcons.Solid.Edit,
                                contentDescription = str(
                                    Res.string.no_day_cutoff,
                                    str(day.label())
                                ),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = { onChange(schedule.copy(departureOverrides = schedule.departureOverrides - day)) },
                        enabled = !busy,
                        modifier = Modifier.size(16.dp)

                    ) {
                        Icon(
                            Icons.Default.Close,
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


//            DepartureCutoffItem(
//                day = day,
//                cutoff = cutoff,
//                appLanguage = appLanguage,
//                busy = busy,
//                schedule = schedule,
//                onChange = onChange,
//                onPickDayOverride = {
//                    editing = it.name
//                }
//            )
        }

    }
    editing?.let { selected ->
        val day = selected.takeUnless { it == "default" }?.let(DayOfWeek::valueOf)
        key(selected) {
            ArrivalTimeDialog(
                initial = day?.let { schedule.departureOverrides[it] } ?: schedule.departure
                ?: LocalTime(17, 0),
                title = str(Res.string.departure_cutoff),
                onConfirm = { time ->
                    onChange(
                        if (day == null) schedule.copy(departure = time)
                        else schedule.copy(departureOverrides = schedule.departureOverrides + (day to time))
                    )
                    editing = null
                },
                onDismiss = { editing = null }
            )
        }
    }
}

@Composable
private fun DepartureCutoffItem(
    day: DayOfWeek,
    busy: Boolean,
    cutoff: LocalTime?,
    appLanguage: AppLanguage,
    schedule: WeeklySchedule,
    onChange: (WeeklySchedule) -> Unit,
    onPickDayOverride: (DayOfWeek) -> Unit
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
                .padding(horizontal = 12.dp, vertical = 6.dp),
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

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = str(day.label()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    cutoff?.let { localTimeLabel(it, appLanguage) }
                        ?: str(Res.string.departure_none),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {


                IconButton(
                    onClick = {
                        onPickDayOverride(day)
                    },
                    enabled = !busy,
                    modifier = Modifier.size(16.dp)

                ) {
                    Icon(
                        FontAwesomeIcons.Solid.Clock,
                        contentDescription = str(Res.string.set_day_cutoff, str(day.label())),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                if (schedule.departure != null) {
                    IconButton(
                        onClick = {
                            onChange(schedule.copy(departureOverrides = schedule.departureOverrides + (day to null)))
                        },
                        enabled = !busy,
                        modifier = Modifier.size(16.dp)

                    ) {
                        Icon(
                            FontAwesomeIcons.Solid.Edit,
                            contentDescription = str(Res.string.no_day_cutoff, str(day.label())),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                IconButton(
                    onClick = { onChange(schedule.copy(departureOverrides = schedule.departureOverrides - day)) },
                    enabled = !busy,
                    modifier = Modifier.size(16.dp)

                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = str(Res.string.reset_day_override, str(day.label())),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

        }
    }
}

@Composable
private fun DepartureCutoffTitle(
    modifier: Modifier = Modifier,
    onShowInfoClick: () -> Unit = {}
) {

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(
            onClick = { onShowInfoClick() },
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.size(16.dp),
        ) {
            Icon(
                imageVector = FontAwesomeIcons.Solid.Info,
                contentDescription = str(Res.string.info)
            )
        }
        Text(str(Res.string.departure_cutoff), style = MaterialTheme.typography.labelLarge)

    }

}

