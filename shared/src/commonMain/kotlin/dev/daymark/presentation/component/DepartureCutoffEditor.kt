package dev.daymark.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.daymark.plan.WeeklySchedule
import dev.daymark.resources.Res
import dev.daymark.resources.departure_cutoff
import dev.daymark.resources.departure_explanation
import dev.daymark.resources.departure_inherit
import dev.daymark.resources.departure_none
import dev.daymark.resources.departure_set
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.str
import dev.daymark.ui.theme.DaymarkTheme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

@Composable
internal fun DepartureCutoffEditor(
    schedule: WeeklySchedule,
    busy: Boolean,
    onChange: (WeeklySchedule) -> Unit,
    modifier: Modifier = Modifier
) {
    val language = LocalAppLanguage.current
    var editing by remember { mutableStateOf<String?>(null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(str(Res.string.departure_cutoff), style = MaterialTheme.typography.titleSmall)
        Text(str(Res.string.departure_explanation), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            onClick = { editing = "default" },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(schedule.departure?.let { localTimeLabel(it, language) }
                ?: str(Res.string.departure_set))
        }
        AnimatedVisibility(visible = schedule.departure != null) {
            TextButton(onClick = { onChange(schedule.copy(departure = null)) }, enabled = !busy) {
                Text(str(Res.string.departure_none))
            }
        }
        for (day in schedule.days.sortedBy { it.ordinal }) {
            val overridden = schedule.departureOverrides.containsKey(day)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(str(day.label()), style = MaterialTheme.typography.labelLarge)
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
                                    language
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

@Preview(name = "Departure cutoff large text", widthDp = 320, fontScale = 2f)
@Composable
private fun DepartureCutoffEditorPreview() {
    DaymarkTheme(ThemePreference.LIGHT) {
        DepartureCutoffEditor(
            WeeklySchedule(
                setOf(DayOfWeek.MONDAY),
                LocalTime(9, 0),
                emptyMap(),
                5,
                "UTC",
                LocalTime(17, 0)
            ),
            busy = false, onChange = {}
        )
    }
}
