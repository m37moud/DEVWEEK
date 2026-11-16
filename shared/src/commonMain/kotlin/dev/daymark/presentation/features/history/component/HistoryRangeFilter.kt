package dev.daymark.presentation.features.history.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.daymark.presentation.features.history.HistoryDetailUiState
import dev.daymark.resources.*
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.str
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryRangeFilter(
    state: HistoryDetailUiState,
    onSelect: (LocalDate, LocalDate) -> Unit
) {
    var fromText by rememberSaveable(state.from) { mutableStateOf(state.from.toString()) }
    var throughText by rememberSaveable(state.through) { mutableStateOf(state.through.toString()) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    val from = LocalDate.parse(fromText)
    val through = LocalDate.parse(throughText)
    val language = LocalAppLanguage.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { picker = "from" }, modifier = Modifier.fillMaxWidth()) {
            Text(str(Res.string.time_off_from, DisplayFormats.date(from, language)))
        }
        OutlinedButton(onClick = { picker = "through" }, modifier = Modifier.fillMaxWidth()) {
            Text(str(Res.string.time_off_through, DisplayFormats.date(through, language)))
        }
        Button(onClick = { onSelect(from, through) }, enabled = state.ready && !state.busy) {
            Text(str(Res.string.apply_range))
        }
        if (state.invalidRange) Text(
            str(Res.string.history_range_invalid),
            color = MaterialTheme.colorScheme.error
        )
    }
    picker?.let { selected ->
        val initial = if (selected == "from") from else through
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        )
        DatePickerDialog(
            onDismissRequest = { picker = null },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { millis ->
                        val date =
                            Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
                        if (selected == "from") fromText = date.toString() else throughText =
                            date.toString()
                    }
                    picker = null
                }) { Text(str(Res.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    picker = null
                }) { Text(str(Res.string.cancel)) }
            }
        ) { DatePicker(dateState) }
    }
}
