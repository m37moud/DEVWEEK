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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.plan.TimeOffCommand
import dev.daymark.models.plan.TimeOffEntry
import dev.daymark.plan.TimeOffKind
import dev.daymark.resources.Res
import dev.daymark.resources.cancel
import dev.daymark.resources.plan_review
import dev.daymark.resources.remove_time_off
import dev.daymark.resources.save
import dev.daymark.resources.status_day_off
import dev.daymark.resources.status_holiday
import dev.daymark.resources.status_vacation
import dev.daymark.resources.time_off
import dev.daymark.resources.time_off_from
import dev.daymark.resources.time_off_through
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.str
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeOffEditorDialog(
    entry: TimeOffEntry?,
    today: LocalDate,
    category: TrackerCategory,
    busy: Boolean,
    onDismiss: () -> Unit,
    onPreview: (TimeOffCommand) -> Unit
) {
    val language = LocalAppLanguage.current
    var from by remember { mutableStateOf(entry?.from ?: today) }
    var through by remember { mutableStateOf(entry?.through ?: today) }
    var kind by remember { mutableStateOf(entry?.kind ?: TimeOffKind.VACATION) }
    var picker by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = str(Res.string.time_off),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (option in TimeOffKind.entries) {
                        FilterChip(
                            selected = kind == option,
                            onClick = { kind = option },
                            label = {
                                Text(
                                    str(
                                        if (option == TimeOffKind.HOLIDAY) Res.string.status_holiday
                                        else if (category == TrackerCategory.WORK) Res.string.status_vacation
                                        else Res.string.status_day_off
                                    )
                                )
                            }
                        )
                    }
                }

                OutlinedButton(
                    onClick = { picker = "from" },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        str(
                            Res.string.time_off_from,
                            DisplayFormats.date(from, language)
                        )
                    )
                }

                OutlinedButton(
                    onClick = { picker = "through" },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        str(
                            Res.string.time_off_through,
                            DisplayFormats.date(through, language)
                        )
                    )
                }

                if (entry != null) {
                    TextButton(
                        enabled = !busy,
                        onClick = { onPreview(TimeOffCommand.Remove(entry.id)) },
                        modifier = Modifier.testTag("time-off-remove")
                    ) {
                        Text(
                            text = str(Res.string.remove_time_off),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                modifier = Modifier.testTag("time-off-preview"),
                onClick = {
                    onPreview(
                        if (entry == null) {
                            TimeOffCommand.Add(kind, from, through)
                        } else {
                            TimeOffCommand.Replace(entry.id, kind, from, through)
                        }
                    )
                }
            ) {
                Text(str(Res.string.plan_review))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(str(Res.string.cancel))
            }
        }
    )

    if (picker != null) {
        val initial = if (picker == "from") from else through
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        )
        DatePickerDialog(
            onDismissRequest = { picker = null },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { millis ->
                        val selected = Instant.fromEpochMilliseconds(millis)
                            .toLocalDateTime(TimeZone.UTC).date
                        if (picker == "from") {
                            from = selected
                            if (through < selected) through = selected
                        } else {
                            through = selected
                            if (from > selected) from = selected
                        }
                    }
                    picker = null
                }) {
                    Text(str(Res.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { picker = null }) {
                    Text(str(Res.string.cancel))
                }
            }
        ) {
            DatePicker(dateState)
        }
    }
}
