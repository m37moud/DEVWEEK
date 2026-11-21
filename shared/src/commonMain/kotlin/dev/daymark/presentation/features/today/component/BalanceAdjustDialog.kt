package dev.daymark.presentation.features.today.component

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
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.plan.parseWholeDays
import dev.daymark.presentation.component.insufficientVacationYearMessage
import dev.daymark.resources.Res
import dev.daymark.resources.adjust_balance
import dev.daymark.resources.adjust_balance_year
import dev.daymark.resources.balance_help
import dev.daymark.resources.balance_note
import dev.daymark.resources.cancel
import dev.daymark.resources.local_save_failed
import dev.daymark.resources.save
import dev.daymark.resources.vacation_balance_remaining
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.isolated
import dev.daymark.settings.str

internal data class BalanceAdjustUiState(
    val initialBalance: Int,
    val year: Int?,
    val busy: Boolean,
    val saveFailure: PlanMutationResult?
)

@Composable
internal fun BalanceAdjustDialog(
    state: BalanceAdjustUiState,
    onSave: (String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val language = LocalAppLanguage.current
    var text by rememberSaveable {
        mutableStateOf(DisplayFormats.number(state.initialBalance, language))
    }
    var note by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!state.busy) onDismiss() },
        title = {
            Text(
                text = state.year?.let { year ->
                    str(Res.string.adjust_balance_year, isolated(DisplayFormats.number(year, language)))
                } ?: str(Res.string.adjust_balance),
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
                    label = { Text(str(Res.string.vacation_balance_remaining)) },
                    enabled = !state.busy,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(str(Res.string.balance_note)) },
                    enabled = !state.busy,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                state.saveFailure?.let { failure ->
                    Text(
                        text = if (failure is PlanMutationResult.InsufficientYear) {
                            insufficientVacationYearMessage(failure.year)
                        } else {
                            str(Res.string.local_save_failed)
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    text = str(Res.string.balance_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !state.busy && parseWholeDays(text) != null,
                onClick = { onSave(text, note.takeIf { it.isNotBlank() }) }
            ) {
                Text(str(Res.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.busy) {
                Text(str(Res.string.cancel))
            }
        }
    )
}
