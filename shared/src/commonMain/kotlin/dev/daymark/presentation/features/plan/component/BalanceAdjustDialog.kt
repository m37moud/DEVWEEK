package dev.daymark.presentation.features.plan.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.daymark.plan.parseWholeDays
import dev.daymark.resources.Res
import dev.daymark.resources.adjust_balance
import dev.daymark.resources.balance_help
import dev.daymark.resources.balance_note
import dev.daymark.resources.cancel
import dev.daymark.resources.opening_balance
import dev.daymark.resources.save
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.str

@Composable
internal fun BalanceAdjustDialog(
    initialBalance: Int?,
    busy: Boolean,
    onSave: (String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val language = LocalAppLanguage.current
    var text by rememberSaveable {
        mutableStateOf(DisplayFormats.number(initialBalance ?: 0, language))
    }
    var note by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = str(Res.string.adjust_balance),
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
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(str(Res.string.opening_balance)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(str(Res.string.balance_note)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = str(Res.string.balance_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && parseWholeDays(text) != null,
                onClick = { onSave(text, note.takeIf { it.isNotBlank() }) }
            ) {
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
