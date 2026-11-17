package dev.daymark.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Clock
import compose.icons.fontawesomeicons.solid.Info
import compose.icons.fontawesomeicons.solid.Redo
import dev.daymark.plan.WeeklySchedule
import dev.daymark.resources.Res
import dev.daymark.resources.departure_cutoff
import dev.daymark.resources.departure_explanation
import dev.daymark.resources.departure_inherit
import dev.daymark.resources.departure_none
import dev.daymark.resources.departure_set
import dev.daymark.resources.info
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
    var editing by remember { mutableStateOf<String?>(null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {

        DepartureCutoffTitle()

        // Divider & Visual Legend Strip
        HorizontalDivider(
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )

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
                        contentDescription = "schedule",
                        modifier = Modifier.size(24.dp)
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

        // Divider & Visual Legend Strip
        HorizontalDivider(
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )

        for (day in schedule.days.sortedBy { it.ordinal }) {
            val overridden = schedule.departureOverrides.containsKey(day)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = str(day.label()),
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !overridden, enabled = !busy,
                        onClick = { onChange(schedule.copy(departureOverrides = schedule.departureOverrides - day)) },
                        label = { Text(str(Res.string.departure_inherit)) }
                    )
                    FilterChip(
                        selected = overridden && schedule.departureOverrides[day] == null,
                        enabled = !busy,
                        onClick = { onChange(schedule.copy(departureOverrides = schedule.departureOverrides + (day to null))) },
                        label = { Text(str(Res.string.departure_none)) }
                    )
                    FilterChip(
                        selected = schedule.departureOverrides[day] != null, enabled = !busy,
                        onClick = { editing = day.name },
                        label = {
                            Text(schedule.departureOverrides[day]?.let {
                                localTimeLabel(
                                    it,
                                    appLanguage
                                )
                            } ?: str(Res.string.departure_set))
                        }
                    )
                }
            }
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
private fun ColumnScope.DepartureCutoffTitle() {
    var isInfoShow by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(str(Res.string.departure_cutoff), style = MaterialTheme.typography.titleSmall)
        IconButton(
            onClick = { isInfoShow = !isInfoShow },
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.size(16.dp),
        ) {
            Icon(
                imageVector = FontAwesomeIcons.Solid.Info,
                contentDescription = str(Res.string.info)
            )
        }
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
}

