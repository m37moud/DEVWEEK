package dev.daymark.presentation.features.setup.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.daymark.plan.WeeklySchedule
import dev.daymark.presentation.component.ZonePicker
import dev.daymark.presentation.component.label
import dev.daymark.presentation.component.localTimeLabel
import dev.daymark.presentation.features.setup.title
import dev.daymark.resources.Res
import dev.daymark.resources.arrival
import dev.daymark.resources.attendance_days
import dev.daymark.resources.attendance_zone
import dev.daymark.resources.cancel
import dev.daymark.resources.grace
import dev.daymark.resources.grace_minutes
import dev.daymark.resources.reset
import dev.daymark.resources.same_arrival
import dev.daymark.resources.save
import dev.daymark.resources.schedule
import dev.daymark.resources.weekday_overrides
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.isolated
import dev.daymark.settings.plural
import dev.daymark.settings.str
import kotlin.math.roundToInt
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

@Composable
internal fun SetupSchedule(
    schedule: WeeklySchedule,
    busy: Boolean,
    onScheduleChange: (WeeklySchedule) -> Unit
) {
    var chooseZone by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    HorizontalDivider()
    Text(str(Res.string.schedule), style = MaterialTheme.typography.titleLarge)
    AttendanceDays(schedule, busy, onScheduleChange)
    OutlinedButton(
        onClick = { picker = "default" },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            str(Res.string.arrival) + " · " + localTimeLabel(
                schedule.arrival,
                LocalAppLanguage.current
            )
        )
    }
    GracePeriod(schedule, busy, onScheduleChange)
    WeekdayOverrides(schedule, busy, onScheduleChange) { picker = it.name }
    OutlinedButton(onClick = { chooseZone = true }, enabled = !busy) {
        Text(str(Res.string.attendance_zone, isolated(schedule.zoneId)))
    }
    if (chooseZone) ZonePicker(schedule.zoneId, onSelect = { zone ->
        onScheduleChange(schedule.copy(zoneId = zone)); chooseZone = false
    }, onDismiss = { chooseZone = false })
    picker?.let { selected ->
        val day = selected.takeUnless { it == "default" }?.let(DayOfWeek::valueOf)
        val initial = day?.let { schedule.overrides[it] } ?: schedule.arrival
        key(selected) {
            ArrivalTimeDialog(initial, onConfirm = { time ->
                onScheduleChange(
                    if (day == null) schedule.copy(arrival = time)
                    else schedule.copy(overrides = schedule.overrides + (day to time))
                )
                picker = null
            }, onDismiss = { picker = null })
        }
    }
}

@Composable
private fun AttendanceDays(
    schedule: WeeklySchedule,
    busy: Boolean,
    onChange: (WeeklySchedule) -> Unit
) {
    Text(str(Res.string.attendance_days), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEach { day ->
            FilterChip(
                selected = day in schedule.days, enabled = !busy,
                onClick = {
                    val days =
                        if (day in schedule.days) schedule.days - day else schedule.days + day
                    if (days.isNotEmpty()) {
                        onChange(
                            schedule.copy(
                                days = days,
                                overrides = schedule.overrides.filterKeys { it in days })
                        )
                    }
                }, label = { Text(str(day.label())) }
            )
        }
    }
}

@Composable
private fun GracePeriod(
    schedule: WeeklySchedule,
    busy: Boolean,
    onChange: (WeeklySchedule) -> Unit
) {
    val label = str(Res.string.grace)
    val minutes = plural(
        Res.plurals.grace_minutes, schedule.graceMinutes,
        isolated(DisplayFormats.number(schedule.graceMinutes, LocalAppLanguage.current))
    )
    Text(label + " · " + minutes)
    Slider(
        schedule.graceMinutes.toFloat(),
        { onChange(schedule.copy(graceMinutes = it.roundToInt())) },
        enabled = !busy,
        valueRange = 0f..120f,
        steps = 119,
        modifier = Modifier.testTag("setup-grace").semantics {
            contentDescription = label
            stateDescription = minutes
        }
    )
}

@Composable
private fun WeekdayOverrides(
    schedule: WeeklySchedule,
    busy: Boolean,
    onChange: (WeeklySchedule) -> Unit,
    onPickTime: (DayOfWeek) -> Unit
) {
    val language = LocalAppLanguage.current
    Text(str(Res.string.weekday_overrides), style = MaterialTheme.typography.titleSmall)
    schedule.days.sortedBy { it.ordinal }.forEach { day ->
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            OutlinedButton(onClick = { onPickTime(day) }, enabled = !busy) {
                Text(str(day.label()) + " · " + (schedule.overrides[day]?.let {
                    localTimeLabel(it, language)
                } ?: str(Res.string.same_arrival)))
            }
            if (day in schedule.overrides) {
                TextButton(
                    onClick = { onChange(schedule.copy(overrides = schedule.overrides - day)) },
                    enabled = !busy
                ) {
                    Text(str(Res.string.reset))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArrivalTimeDialog(
    initial: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val time = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(Res.string.arrival)) },
        text = { TimePicker(time) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    LocalTime(
                        time.hour,
                        time.minute
                    )
                )
            }) { Text(str(Res.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(Res.string.cancel)) } }
    )
}
