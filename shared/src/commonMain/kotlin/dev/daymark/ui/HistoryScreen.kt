package dev.daymark.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import dev.daymark.attendance.*
import dev.daymark.resources.*
import dev.daymark.settings.*
import kotlinx.datetime.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HistoryScreen(model: HistoryViewModel, onBack: () -> Unit, onEditTimeOff: () -> Unit) {
    val state by model.uiState.collectAsStateWithLifecycle()
    LifecycleStartEffect(model) { model.setActive(true); onStopOrDispose { model.setActive(false) } }
    val language = LocalAppLanguage.current
    var editing by remember { mutableStateOf<HistoryRecord?>(null) }
    var showOff by remember { mutableStateOf(false) }
    LaunchedEffect(state.savedKey) { if (editing?.plan?.key == state.savedKey) editing = null }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp).testTag("history-root")) {
        TextButton(onClick = onBack) { Text(str(Res.string.back)) }
        Text(str(Res.string.history), style = MaterialTheme.typography.headlineLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { val date = LocalDate(state.year,state.month,1).minus(1,DateTimeUnit.MONTH); model.selectMonth(date.year,date.month.number) }) { Text(str(Res.string.previous_month)) }
            TextButton(onClick = { val date = LocalDate(state.year,state.month,1).plus(1,DateTimeUnit.MONTH); model.selectMonth(date.year,date.month.number) }) { Text(str(Res.string.next_month)) }
        }
        Text(DisplayFormats.date(LocalDate(state.year,state.month,1),language), style = MaterialTheme.typography.titleMedium)
        Text(str(Res.string.month_counts, DisplayFormats.number(state.history.present,language), DisplayFormats.number(state.history.late,language), DisplayFormats.number(state.history.absent,language)))
        if (state.storageError) Text(str(Res.string.load_failed), color = MaterialTheme.colorScheme.error)
        if (!state.ready) CircularProgressIndicator()
        else if (state.history.records.isEmpty()) Text(str(Res.string.no_records), Modifier.padding(vertical = 24.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 20.dp)) {
            items(state.history.records, key = { it.plan.key.date.toString() }) { record ->
                Card(onClick = {
                    if (!record.plan.isScheduled || record.outcome.offReason != null) showOff = true else editing = record
                }, modifier = Modifier.fillMaxWidth().testTag("history-record-${record.plan.key.date}")) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(DisplayFormats.date(record.plan.key.date,language), style = MaterialTheme.typography.titleMedium)
                        Text(str(record.outcome.status.label()))
                        Text(str(record.outcome.source.label()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        record.evidence?.note?.takeIf { it.isNotEmpty() }?.let { Text(isolated(it)) }
                    }
                }
            }
        }
    }
    if (showOff) AlertDialog(onDismissRequest = { showOff = false }, title = { Text(str(Res.string.edit_time_off)) },
        text = { Text(str(Res.string.time_off_edit_help)) }, confirmButton = { TextButton(onClick = { showOff = false; onEditTimeOff() }) { Text(str(Res.string.edit_time_off)) } },
        dismissButton = { TextButton(onClick = { showOff = false }) { Text(str(Res.string.cancel)) } })
    editing?.let { record -> key(record.plan.key) {
        var status by rememberSaveable { mutableStateOf(record.outcome.status.takeIf { it in listOf(AttendanceStatus.PRESENT,AttendanceStatus.LATE,AttendanceStatus.ABSENT) } ?: AttendanceStatus.ABSENT) }
        var note by rememberSaveable { mutableStateOf(record.evidence?.note.orEmpty()) }
        val initial = record.evidence?.confirmedAt?.toLocalDateTime(TimeZone.of(record.plan.zoneId))?.time
        var arrival by rememberSaveable { mutableStateOf(initial?.toString()) }
        var pickTime by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { if (!state.busy) editing = null }, title = { Text(str(Res.string.edit_record)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(str(Res.string.correction_help))
                    for (option in listOf(AttendanceStatus.PRESENT,AttendanceStatus.LATE,AttendanceStatus.ABSENT)) {
                        FilterChip(selected = status == option, onClick = { status = option }, label = { Text(str(option.label())) })
                    }
                    if (status != AttendanceStatus.ABSENT) OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.testTag("correction-time")) {
                        Text(arrival?.let { DisplayFormats.time(dev.daymark.plan.resolveLocalArrival(record.plan.key.date,LocalTime.parse(it),record.plan.zoneId), record.plan.zoneId,language) } ?: str(Res.string.correction_time))
                    }
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(str(Res.string.correction_note)) }, modifier = Modifier.testTag("correction-note"), maxLines = 4)
                    if (state.invalidCorrection) Text(str(Res.string.invalid_correction), color = MaterialTheme.colorScheme.error)
                    if (state.storageError) Text(str(Res.string.local_save_failed), color = MaterialTheme.colorScheme.error)
                }
            }, confirmButton = { TextButton(enabled = !state.busy, modifier = Modifier.testTag("correction-save"), onClick = {
                model.saveCorrection(Correction(record.plan.key,status,arrival?.let(LocalTime::parse),note.takeIf { it.isNotEmpty() }))
            }) { Text(str(Res.string.save)) } }, dismissButton = { TextButton(enabled = !state.busy, onClick = { editing = null }) { Text(str(Res.string.cancel)) } })
        if (pickTime) {
            val time = rememberTimePickerState(initialHour = initial?.hour ?: 9, initialMinute = initial?.minute ?: 0, is24Hour = true)
            AlertDialog(onDismissRequest = { pickTime = false }, title = { Text(str(Res.string.correction_time)) }, text = { TimePicker(time) },
                confirmButton = { TextButton(onClick = { arrival = LocalTime(time.hour,time.minute).toString(); pickTime = false }) { Text(str(Res.string.save)) } },
                dismissButton = { TextButton(onClick = { pickTime = false }) { Text(str(Res.string.cancel)) } })
        }
    } }
}
