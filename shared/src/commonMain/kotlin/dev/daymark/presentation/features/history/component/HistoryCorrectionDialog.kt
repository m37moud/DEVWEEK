package dev.daymark.presentation.features.history.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Clock
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.models.attendance.Correction
import dev.daymark.models.attendance.HistoryRecord
import dev.daymark.presentation.component.label
import dev.daymark.reporting.REPORT_TEXT_LIMIT
import dev.daymark.reporting.exceedsReportTextLimit
import dev.daymark.resources.Res
import dev.daymark.resources.cancel
import dev.daymark.resources.correction_help
import dev.daymark.resources.correction_note
import dev.daymark.resources.correction_time
import dev.daymark.resources.edit_record
import dev.daymark.resources.edit_time_off
import dev.daymark.resources.invalid_correction
import dev.daymark.resources.local_save_failed
import dev.daymark.resources.note_too_long
import dev.daymark.resources.save
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.str
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryCorrectionDialog(
    record: HistoryRecord,
    busy: Boolean,
    noteTooLong: Boolean,
    invalidCorrection: Boolean,
    storageError: Boolean,
    language: AppLanguage,
    onSaveCorrection: (Correction) -> Unit,
    onEditTimeOff: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var status by rememberSaveable {
        mutableStateOf(
            record.outcome.status.takeIf {
                it in listOf(
                    AttendanceStatus.PRESENT,
                    AttendanceStatus.LATE,
                    AttendanceStatus.ABSENT
                )
            } ?: AttendanceStatus.ABSENT
        )
    }
    var note by rememberSaveable { mutableStateOf(record.evidence?.note.orEmpty()) }
    val initial = record.evidence?.confirmedAt?.toLocalDateTime(TimeZone.of(record.plan.zoneId))?.time
    var arrival by rememberSaveable { mutableStateOf(initial?.toString()) }
    var pickTime by remember { mutableStateOf(false) }

    val hasNoteError = noteTooLong || exceedsReportTextLimit(note)

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = str(Res.string.edit_record),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = DisplayFormats.date(record.plan.key.date, language),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = str(Res.string.correction_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Status Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        AttendanceStatus.PRESENT,
                        AttendanceStatus.LATE,
                        AttendanceStatus.ABSENT
                    ).forEach { option ->
                        FilterChip(
                            selected = status == option,
                            onClick = { status = option },
                            label = { Text(text = str(option.label())) },
                            leadingIcon = if (status == option) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors()
                        )
                    }
                }

                // Time picker button (only for Present/Late)
                if (status != AttendanceStatus.ABSENT) {
                    OutlinedButton(
                        onClick = { pickTime = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("correction-time")
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
                                text = arrival?.let {
                                    DisplayFormats.time(
                                        dev.daymark.plan.resolveLocalArrival(
                                            record.plan.key.date,
                                            LocalTime.parse(it),
                                            record.plan.zoneId
                                        ),
                                        record.plan.zoneId,
                                        language
                                    )
                                } ?: str(Res.string.correction_time)
                            )
                        }
                    }
                }

                // Note text field
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(str(Res.string.correction_note)) },
                    supportingText = {
                        Text(
                            text = "${note.length} / $REPORT_TEXT_LIMIT",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    isError = hasNoteError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("correction-note"),
                    maxLines = 4
                )

                // Secondary action: Edit time off
                TextButton(
                    onClick = {
                        onDismiss()
                        onEditTimeOff(record.plan.key.date)
                    }
                ) {
                    Text(str(Res.string.edit_time_off))
                }

                // Error messages with AnimatedVisibility
                AnimatedVisibility(
                    visible = hasNoteError,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Text(
                        text = str(
                            Res.string.note_too_long,
                            DisplayFormats.number(REPORT_TEXT_LIMIT, language)
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                AnimatedVisibility(
                    visible = invalidCorrection && !hasNoteError,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Text(
                        text = str(Res.string.invalid_correction),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                AnimatedVisibility(
                    visible = storageError,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Text(
                        text = str(Res.string.local_save_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                modifier = Modifier.testTag("correction-save"),
                onClick = {
                    onSaveCorrection(
                        Correction(
                            record.plan.key,
                            status,
                            arrival?.let(LocalTime::parse),
                            note.takeIf { it.isNotEmpty() }
                        )
                    )
                }
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(str(Res.string.save))
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = onDismiss
            ) {
                Text(str(Res.string.cancel))
            }
        }
    )

    if (pickTime) {
        val timeState = rememberTimePickerState(
            initialHour = initial?.hour ?: 9,
            initialMinute = initial?.minute ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text(str(Res.string.correction_time)) },
            text = { TimePicker(timeState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        arrival = LocalTime(timeState.hour, timeState.minute).toString()
                        pickTime = false
                    }
                ) {
                    Text(str(Res.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { pickTime = false }) {
                    Text(str(Res.string.cancel))
                }
            }
        )
    }
}
