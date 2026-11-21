package dev.daymark.presentation.features.settings.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.plan.parseWholeDays
import dev.daymark.presentation.component.PlanMutationFeedback
import dev.daymark.presentation.features.settings.AnnualVacationSettingsUiState
import dev.daymark.resources.Res
import dev.daymark.resources.annual_vacation_allowance
import dev.daymark.resources.annual_vacation_carryover
import dev.daymark.resources.annual_vacation_effective
import dev.daymark.resources.annual_vacation_invalid
import dev.daymark.resources.annual_vacation_scheduled
import dev.daymark.resources.annual_vacation_settings
import dev.daymark.resources.annual_vacation_stale
import dev.daymark.resources.load_failed
import dev.daymark.resources.save
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.isolated
import dev.daymark.settings.str

@Composable
internal fun SettingsAnnualVacationCard(
    state: AnnualVacationSettingsUiState,
    busy: Boolean,
    onSave: (Int, Boolean, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.loadFailed) {
        AnnualVacationError(str(Res.string.load_failed))
        return
    }
    val settings = state.configuration ?: return
    val language = LocalAppLanguage.current
    val savedPolicy = settings.pending ?: settings.active
    val year = settings.nextEffectiveYear
    var allowanceInput by rememberSaveable(year, savedPolicy) { mutableStateOf<String?>(null) }
    var carryoverInput by rememberSaveable(year, savedPolicy) { mutableStateOf<Boolean?>(null) }
    var showSaveFeedback by rememberSaveable { mutableStateOf(false) }
    val allowanceText = allowanceInput ?: savedPolicy?.let {
        DisplayFormats.number(it.annualAllowanceDays, language)
    }.orEmpty()
    val allowanceDays = parseWholeDays(allowanceText)
    val carryOver = carryoverInput ?: savedPolicy?.carryOverUnusedDays ?: false
    val changed = allowanceDays != savedPolicy?.annualAllowanceDays ||
            carryOver != (savedPolicy?.carryOverUnusedDays ?: false)
    val startsOn = isolated(DisplayFormats.date(settings.nextEffectiveDate, language))

    Card(modifier.fillMaxWidth().testTag("settings-annual-vacation")) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                str(Res.string.annual_vacation_settings),
                style = MaterialTheme.typography.titleMedium
            )
            AnnualAllowanceField(
                allowanceText,
                enabled = !busy,
                invalid = allowanceText.isNotBlank() && allowanceDays == null
            ) {
                allowanceInput = it
                showSaveFeedback = false
            }
            CarryOverSwitch(carryOver, enabled = !busy) {
                carryoverInput = it
                showSaveFeedback = false
            }
            Text(
                str(
                    if (settings.pending?.effectiveYear == year && !changed) Res.string.annual_vacation_scheduled
                    else Res.string.annual_vacation_effective,
                    startsOn
                ),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            if (showSaveFeedback && state.saveResult != PlanMutationResult.Applied) {
                AnnualVacationSaveFeedback(state.saveResult)
            }
            Button(
                onClick = {
                    allowanceDays?.let { onSave(it, carryOver, year) }
                    showSaveFeedback = true
                },
                enabled = !busy && changed && allowanceDays != null,
                modifier = Modifier.testTag("settings-annual-vacation-save")
            ) {
                Text(str(Res.string.save))
            }
        }
    }
}

@Composable
private fun AnnualAllowanceField(
    allowanceText: String,
    enabled: Boolean,
    invalid: Boolean,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = allowanceText,
        onValueChange = onChange,
        label = { Text(str(Res.string.annual_vacation_allowance)) },
        supportingText = if (invalid) {
            { Text(str(Res.string.annual_vacation_invalid)) }
        } else null,
        enabled = enabled,
        isError = invalid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().testTag("settings-annual-vacation-allowance")
    )
}

@Composable
private fun CarryOverSwitch(carryOver: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(carryOver, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .testTag("settings-annual-vacation-carryover"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(str(Res.string.annual_vacation_carryover), Modifier.weight(1f))
        Switch(checked = carryOver, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun AnnualVacationSaveFeedback(mutation: PlanMutationResult?) {
    when (mutation) {
        PlanMutationResult.StalePreview -> AnnualVacationError(str(Res.string.annual_vacation_stale))
        else -> PlanMutationFeedback(mutation)
    }
}

@Composable
private fun AnnualVacationError(message: String) {
    Text(
        message,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.error
    )
}
