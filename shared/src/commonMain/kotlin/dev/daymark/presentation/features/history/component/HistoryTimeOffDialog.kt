package dev.daymark.presentation.features.history.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.daymark.resources.Res
import dev.daymark.resources.cancel
import dev.daymark.resources.edit_time_off
import dev.daymark.resources.time_off_edit_help
import dev.daymark.settings.str
import kotlinx.datetime.LocalDate

@Composable
internal fun HistoryTimeOffDialog(
    date: LocalDate,
    onEditTimeOff: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = str(Res.string.edit_time_off),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                text = str(Res.string.time_off_edit_help),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                modifier = Modifier.testTag("history-time-off-action"),
                onClick = {
                    onDismiss()
                    onEditTimeOff(date)
                }
            ) {
                Text(str(Res.string.edit_time_off))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(str(Res.string.cancel))
            }
        }
    )
}
