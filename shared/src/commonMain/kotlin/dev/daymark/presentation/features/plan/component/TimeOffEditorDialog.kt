package dev.daymark.presentation.features.plan.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.plan.TimeOffCommand
import dev.daymark.models.plan.TimeOffEntry
import dev.daymark.plan.TimeOffKind
import dev.daymark.plan.dates
import dev.daymark.presentation.component.label
import dev.daymark.reporting.exceedsReportTextLimit
import dev.daymark.resources.Res
import dev.daymark.resources.cancel
import dev.daymark.resources.plan_review
import dev.daymark.resources.remove_time_off
import dev.daymark.resources.save
import dev.daymark.resources.status_work_mission
import dev.daymark.resources.time_off
import dev.daymark.resources.time_off_from
import dev.daymark.resources.time_off_select_days
import dev.daymark.resources.time_off_select_range
import dev.daymark.resources.time_off_through
import dev.daymark.resources.work_mission_note
import dev.daymark.resources.work_mission_note_too_long
import dev.daymark.resources.work_mission_remove
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.str
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Instant

internal data class TimeOffEditorTarget(
    val entry: TimeOffEntry?,
    val date: LocalDate,
    val category: TrackerCategory,
    val availableKinds: List<TimeOffKind>
)

@Composable
internal fun TimeOffEditorDialog(
    target: TimeOffEditorTarget,
    busy: Boolean,
    onDismiss: () -> Unit,
    onPreview: (TimeOffCommand) -> Unit
) {
    val (entry, date, category, availableKinds) = target
    val fixedDay = availableKinds.singleOrNull() == TimeOffKind.WORK_MISSION || entry?.kind == TimeOffKind.WORK_MISSION
    val initialKind = entry?.kind ?: if (fixedDay) TimeOffKind.WORK_MISSION else TimeOffKind.VACATION
    var kind by rememberSaveable(entry?.id) { mutableStateOf(initialKind) }
    var fromDate by rememberSaveable(entry?.id) { mutableStateOf((entry?.from ?: date).toString()) }
    var throughDate by rememberSaveable(entry?.id) { mutableStateOf((entry?.through ?: date).toString()) }
    var note by rememberSaveable(entry?.id) { mutableStateOf(entry?.note.orEmpty()) }
    var picker by rememberSaveable { mutableStateOf<DateBoundary?>(null) }
    var throughSelected by rememberSaveable(entry?.id) { mutableStateOf(false) }
    var pickingSeparateDays by rememberSaveable { mutableStateOf(false) }
    var selectedDayKeys by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val selectedDates = selectedDayKeys.map(LocalDate::parse).toSet()
    val noteTooLong = kind == TimeOffKind.WORK_MISSION && exceedsReportTextLimit(note)
    val from = LocalDate.parse(fromDate)
    val through = LocalDate.parse(throughDate)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = if (pickingSeparateDays) Modifier.padding(horizontal = 12.dp) else Modifier,
        properties = DialogProperties(usePlatformDefaultWidth = !pickingSeparateDays),
        title = { Text(str(if (fixedDay) Res.string.status_work_mission else Res.string.time_off)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (fixedDay) {
                    Text(DisplayFormats.date(from, LocalAppLanguage.current))
                } else {
                    TimeOffKindOptions(kind, category, availableKinds, onSelect = { kind = it })
                    if (entry == null) {
                        DateSelectionOptions(pickingSeparateDays) { pickingSeparateDays = it }
                    }
                    if (pickingSeparateDays) {
                        SeparateDaysCalendar(from, selectedDates, onToggle = { selected ->
                            val key = selected.toString()
                            selectedDayKeys = ArrayList(
                                if (key in selectedDayKeys) selectedDayKeys - key else selectedDayKeys + key
                            )
                        })
                    } else {
                        DateField(DateBoundary.FROM, from) { picker = DateBoundary.FROM }
                        DateField(DateBoundary.THROUGH, through) { picker = DateBoundary.THROUGH }
                    }
                }
                if (kind == TimeOffKind.WORK_MISSION) {
                    WorkMissionNoteField(note, noteTooLong, onChange = { note = it })
                }
                if (entry != null) {
                    TextButton(
                        enabled = !busy,
                        onClick = { onPreview(TimeOffCommand.Remove(entry.id)) },
                        modifier = Modifier.testTag("time-off-remove")
                    ) {
                        Text(
                            str(if (fixedDay) Res.string.work_mission_remove else Res.string.remove_time_off),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && !noteTooLong && (!pickingSeparateDays || selectedDates.isNotEmpty()),
                modifier = Modifier.testTag("time-off-preview"),
                onClick = {
                    val command = when {
                        entry != null -> TimeOffCommand.Replace(entry.id, kind, from, through, note.takeIf { fixedDay })
                        pickingSeparateDays -> TimeOffCommand.AddDates(
                            kind,
                            selectedDates,
                            note.takeIf { kind == TimeOffKind.WORK_MISSION })

                        kind == TimeOffKind.WORK_MISSION -> TimeOffCommand.AddDates(
                            kind,
                            dates(from, through).toSet(),
                            note
                        )

                        else -> TimeOffCommand.Add(kind, from, through)
                    }
                    onPreview(command)
                }
            ) {
                Text(str(Res.string.plan_review))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(str(Res.string.cancel)) }
        }
    )

    picker?.let { boundary ->
        TimeOffDatePicker(
            initial = if (boundary == DateBoundary.FROM) from else through,
            onSelect = { selected ->
                if (boundary == DateBoundary.FROM) {
                    fromDate = selected.toString()
                    val newSingleDay = entry == null && !throughSelected && from == through
                    if (newSingleDay || through < selected) throughDate = selected.toString()
                } else {
                    throughSelected = true
                    throughDate = selected.toString()
                    if (from > selected) fromDate = selected.toString()
                }
                picker = null
            },
            onDismiss = { picker = null }
        )
    }
}

private enum class DateBoundary(val label: StringResource) {
    FROM(Res.string.time_off_from),
    THROUGH(Res.string.time_off_through)
}

@Composable
private fun DateField(boundary: DateBoundary, date: LocalDate, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("time-off-${boundary.name.lowercase()}")
    ) {
        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text(str(boundary.label, DisplayFormats.date(date, LocalAppLanguage.current)))
    }
}

@Composable
private fun TimeOffKindOptions(
    selected: TimeOffKind,
    category: TrackerCategory,
    options: List<TimeOffKind>,
    onSelect: (TimeOffKind) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (kind in options) {
            FilterChip(selected == kind, { onSelect(kind) }, label = { Text(str(kind.label(category))) })
        }
    }
}

@Composable
private fun DateSelectionOptions(separateDays: Boolean, onSelect: (Boolean) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!separateDays, { onSelect(false) }, label = { Text(str(Res.string.time_off_select_range)) })
        FilterChip(
            separateDays, { onSelect(true) },
            label = { Text(str(Res.string.time_off_select_days)) },
            modifier = Modifier.testTag("time-off-separate-days")
        )
    }
}

@Composable
private fun WorkMissionNoteField(note: String, tooLong: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = note,
        onValueChange = onChange,
        label = { Text(str(Res.string.work_mission_note)) },
        isError = tooLong,
        supportingText = if (tooLong) ({ Text(str(Res.string.work_mission_note_too_long)) }) else null,
        maxLines = 4,
        modifier = Modifier.fillMaxWidth().testTag("work-mission-note")
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeOffDatePicker(initial: LocalDate, onSelect: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag("time-off-date-save"),
                onClick = {
                    val millis = dateState.selectedDateMillis
                    if (millis == null) onDismiss()
                    else onSelect(Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date)
                }
            ) { Text(str(Res.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(Res.string.cancel)) } }
    ) {
        DatePicker(dateState)
    }
}
