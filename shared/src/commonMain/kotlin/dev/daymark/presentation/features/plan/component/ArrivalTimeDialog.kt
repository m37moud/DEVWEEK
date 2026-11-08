package dev.daymark.presentation.features.plan.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import dev.daymark.resources.Res
import dev.daymark.resources.arrival
import dev.daymark.resources.cancel
import dev.daymark.resources.save
import dev.daymark.settings.str
import kotlinx.datetime.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArrivalTimeDialog(
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
                onConfirm(LocalTime(time.hour, time.minute))
            }) {
                Text(str(Res.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(str(Res.string.cancel))
            }
        }
    )
}
