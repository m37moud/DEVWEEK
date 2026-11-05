package dev.daymark.presentation.features.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.models.attendance.Correction
import dev.daymark.models.attendance.HistoryRecord
import dev.daymark.presentation.component.AttendanceCalendar
import dev.daymark.presentation.component.label
import dev.daymark.presentation.features.setup.title
import dev.daymark.reporting.REPORT_TEXT_LIMIT
import dev.daymark.resources.Res
import dev.daymark.resources.back
import dev.daymark.resources.cancel
import dev.daymark.resources.confirmed_at
import dev.daymark.resources.correction_help
import dev.daymark.resources.correction_note
import dev.daymark.resources.correction_time
import dev.daymark.resources.edit_record
import dev.daymark.resources.edit_time_off
import dev.daymark.resources.future_date_plan
import dev.daymark.resources.history
import dev.daymark.resources.invalid_correction
import dev.daymark.resources.load_failed
import dev.daymark.resources.local_save_failed
import dev.daymark.resources.month_counts
import dev.daymark.resources.next_month
import dev.daymark.resources.no_records
import dev.daymark.resources.note_too_long
import dev.daymark.resources.previous_month
import dev.daymark.resources.save
import dev.daymark.resources.show_all_records
import dev.daymark.resources.time_off_edit_help
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.isolated
import dev.daymark.settings.str
import dev.daymark.ui.theme.DaymarkTheme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

private data class HistoryActions(
    val selectMonth: (Int, Int) -> Unit,
    val saveCorrection: (Correction) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(model: HistoryViewModel, onBack: () -> Unit, onEditTimeOff: (LocalDate) -> Unit) {
    val state by model.uiState.collectAsStateWithLifecycle()
    LifecycleStartEffect(model) { model.setActive(true); onStopOrDispose { model.setActive(false) } }
    val actions = remember(model) { HistoryActions(model::selectMonth, model::saveCorrection) }
    HistoryContent(state, onBack, onEditTimeOff, actions)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryContent(
    state: HistoryUiState,
    onBack: () -> Unit,
    onEditTimeOff: (LocalDate) -> Unit,
    actions: HistoryActions
) {
    val language = LocalAppLanguage.current
    var editing by remember { mutableStateOf<HistoryRecord?>(null) }
    var offDate by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(state.savedKey) { if (editing?.plan?.key == state.savedKey) editing = null }
    var selectedDate by rememberSaveable(state.year, state.month) { mutableStateOf<String?>(null) }
    val selected = selectedDate?.let(LocalDate::parse)
    val shown = state.history.records.filter { selected == null || it.plan.key.date == selected }
    LazyColumn(
        Modifier.fillMaxSize().safeDrawingPadding().testTag("history-root"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { TextButton(onClick = onBack) { Text(str(Res.string.back)) } }
        item { Text(str(Res.string.history), style = MaterialTheme.typography.headlineLarge) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = {
                    val date = LocalDate(state.year, state.month, 1).minus(
                        1,
                        DateTimeUnit.MONTH
                    ); actions.selectMonth(date.year, date.month.number)
                }) { Text(str(Res.string.previous_month)) }
                TextButton(onClick = {
                    val date = LocalDate(state.year, state.month, 1).plus(
                        1,
                        DateTimeUnit.MONTH
                    ); actions.selectMonth(date.year, date.month.number)
                }) { Text(str(Res.string.next_month)) }
            }
        }
        item {
            Text(
                DisplayFormats.date(LocalDate(state.year, state.month, 1), language),
                style = MaterialTheme.typography.titleMedium
            )
        }
        item {
            Text(
                str(
                    Res.string.month_counts,
                    DisplayFormats.number(state.history.present, language),
                    DisplayFormats.number(state.history.late, language),
                    DisplayFormats.number(state.history.absent, language)
                )
            )
        }
        item {
            AttendanceCalendar(
                state.year,
                state.month,
                state.history.records,
                state.today,
                selected
            ) { selectedDate = it.toString() }
        }
        if (selected != null) item {
            TextButton(
                onClick = { selectedDate = null },
                modifier = Modifier.testTag("history-show-all")
            ) { Text(str(Res.string.show_all_records)) }
            if (selected > state.today && state.history.records.none { it.plan.key.date == selected }) {
                Text(
                    str(Res.string.future_date_plan),
                    modifier = Modifier.testTag("history-future-plan")
                )
                TextButton(onClick = { onEditTimeOff(selected) }) { Text(str(Res.string.edit_time_off)) }
            }
        }
        if (state.storageError) item {
            Text(
                str(Res.string.load_failed),
                color = MaterialTheme.colorScheme.error
            )
        }
        if (!state.ready) item { CircularProgressIndicator() }
        else if (shown.isEmpty() && (selected == null || selected <= state.today)) item {
            Text(
                str(
                    Res.string.no_records
                ), Modifier.padding(vertical = 24.dp)
            )
        }
        items(shown, key = { it.plan.key.date.toString() }) { record ->
            Card(
                onClick = {
                    if (!record.plan.isScheduled || record.outcome.offReason != null) offDate =
                        record.plan.key.date else editing = record
                },
                modifier = Modifier.fillMaxWidth().testTag("history-record-${record.plan.key.date}")
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        DisplayFormats.date(record.plan.key.date, language),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(str(record.outcome.status.label()))
                    record.outcome.confirmedAt?.let { at ->
                        Text(
                            str(
                                Res.string.confirmed_at,
                                DisplayFormats.time(at, record.plan.zoneId, language)
                            ),
                            modifier = Modifier.testTag("history-confirmed-${record.plan.key.date}")
                        )
                    }
                    Text(
                        str(record.outcome.source.label()),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    record.evidence?.note?.takeIf { it.isNotEmpty() }
                        ?.let { Text(isolated(it), maxLines = 4, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
    if (offDate != null) AlertDialog(
        onDismissRequest = { offDate = null },
        title = { Text(str(Res.string.edit_time_off)) },
        text = { Text(str(Res.string.time_off_edit_help)) },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag("history-time-off-action"),
                onClick = { val date = offDate; offDate = null; date?.let(onEditTimeOff) }) {
                Text(
                    str(Res.string.edit_time_off)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = {
                offDate = null
            }) { Text(str(Res.string.cancel)) }
        })
    editing?.let { record ->
        key(record.plan.key) {
            var status by rememberSaveable {
                mutableStateOf(record.outcome.status.takeIf {
                    it in listOf(
                        AttendanceStatus.PRESENT,
                        AttendanceStatus.LATE,
                        AttendanceStatus.ABSENT
                    )
                } ?: AttendanceStatus.ABSENT)
            }
            var note by rememberSaveable { mutableStateOf(record.evidence?.note.orEmpty()) }
            val initial =
                record.evidence?.confirmedAt?.toLocalDateTime(TimeZone.of(record.plan.zoneId))?.time
            var arrival by rememberSaveable { mutableStateOf(initial?.toString()) }
            var pickTime by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = { if (!state.busy) editing = null },
                title = { Text(str(Res.string.edit_record)) },
                text = {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(str(Res.string.correction_help))
                        for (option in listOf(
                            AttendanceStatus.PRESENT,
                            AttendanceStatus.LATE,
                            AttendanceStatus.ABSENT
                        )) {
                            FilterChip(
                                selected = status == option,
                                onClick = { status = option },
                                label = { Text(str(option.label())) })
                        }
                        if (status != AttendanceStatus.ABSENT) OutlinedButton(onClick = {
                            pickTime = true
                        }, modifier = Modifier.testTag("correction-time")) {
                            Text(arrival?.let {
                                DisplayFormats.time(
                                    dev.daymark.plan.resolveLocalArrival(
                                        record.plan.key.date,
                                        LocalTime.parse(it),
                                        record.plan.zoneId
                                    ), record.plan.zoneId, language
                                )
                            } ?: str(Res.string.correction_time))
                        }
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text(str(Res.string.correction_note)) },
                            modifier = Modifier.testTag("correction-note"),
                            maxLines = 4
                        )
                        TextButton(onClick = {
                            editing = null; onEditTimeOff(record.plan.key.date)
                        }) { Text(str(Res.string.edit_time_off)) }
                        if (state.noteTooLong || dev.daymark.reporting.exceedsReportTextLimit(note)) Text(
                            str(
                                Res.string.note_too_long,
                                DisplayFormats.number(
                                    REPORT_TEXT_LIMIT,
                                    language
                                )
                            ),
                            color = MaterialTheme.colorScheme.error
                        )
                        else if (state.invalidCorrection) Text(
                            str(Res.string.invalid_correction),
                            color = MaterialTheme.colorScheme.error
                        )
                        if (state.storageError) Text(
                            str(Res.string.local_save_failed),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !state.busy,
                        modifier = Modifier.testTag("correction-save"),
                        onClick = {
                            actions.saveCorrection(
                                Correction(
                                    record.plan.key,
                                    status,
                                    arrival?.let(LocalTime::parse),
                                    note.takeIf { it.isNotEmpty() })
                            )
                        }) { Text(str(Res.string.save)) }
                },
                dismissButton = {
                    TextButton(
                        enabled = !state.busy,
                        onClick = { editing = null }) { Text(str(Res.string.cancel)) }
                })
            if (pickTime) {
                val time = rememberTimePickerState(
                    initialHour = initial?.hour ?: 9,
                    initialMinute = initial?.minute ?: 0,
                    is24Hour = true
                )
                AlertDialog(
                    onDismissRequest = { pickTime = false },
                    title = { Text(str(Res.string.correction_time)) },
                    text = { TimePicker(time) },
                    confirmButton = {
                        TextButton(onClick = {
                            arrival = LocalTime(time.hour, time.minute).toString(); pickTime = false
                        }) { Text(str(Res.string.save)) }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            pickTime = false
                        }) { Text(str(Res.string.cancel)) }
                    })
            }
        }
    }
}

@Preview
@Composable
private fun HistoryScreenPreview() {
    val actions = HistoryActions({ _, _ -> }, { _ -> })
    DaymarkTheme(ThemePreference.LIGHT) {
        HistoryContent(
            HistoryUiState(2026, 11, ready = true),
            {},
            {},
            actions
        )
    }
}
