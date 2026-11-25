package dev.daymark.presentation.features.settings.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
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
import dev.daymark.app.reducedMotionEnabled
import dev.daymark.models.plan.AnnualVacationPolicy
import dev.daymark.models.plan.AnnualVacationSettings
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.plan.parseWholeDays
import dev.daymark.presentation.component.PlanMutationFeedback
import dev.daymark.presentation.features.settings.AnnualVacationSettingsUiState
import dev.daymark.resources.Res
import dev.daymark.resources.annual_vacation_allowance
import dev.daymark.resources.annual_vacation_carryover
import dev.daymark.resources.annual_vacation_effective
import dev.daymark.resources.annual_vacation_help_label
import dev.daymark.resources.annual_vacation_help_message
import dev.daymark.resources.annual_vacation_invalid
import dev.daymark.resources.annual_vacation_scheduled
import dev.daymark.resources.annual_vacation_settings
import dev.daymark.resources.annual_vacation_stale
import dev.daymark.resources.cancel
import dev.daymark.resources.load_failed
import dev.daymark.resources.save
import dev.daymark.resources.settings_annual_vacation_unset
import dev.daymark.resources.settings_off
import dev.daymark.resources.settings_on
import dev.daymark.resources.settings_summary_value
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.isolated
import dev.daymark.settings.str
import kotlinx.datetime.LocalDate

@Composable
fun SettingsAnnualVacationCard(
    state: AnnualVacationSettingsUiState,
    busy: Boolean,
    onSave: (Int, Boolean, Int) -> Unit,
    modifier: Modifier = Modifier,
    initialEditorAllowance: String? = null,
    initiallyShowHelp: Boolean = false,
) {
    var editing by rememberSaveable { mutableStateOf(value = initialEditorAllowance != null) }
    val settings = state.configuration
    if (state.loadFailed) {
        AnnualVacationError(message = str(Res.string.load_failed))
    } else if (settings != null) {
        SettingsEditorCard(
            header = SettingsEditorHeader(
                title = Res.string.annual_vacation_settings,
                editTag = "settings-annual-vacation-edit",
                closeLabel = Res.string.cancel,
                editing = editing,
                enabled = !busy,
                help = SettingsEditorHelp(
                    label = Res.string.annual_vacation_help_label,
                    message = Res.string.annual_vacation_help_message,
                    initiallyVisible = initiallyShowHelp,
                ),
            ),
            onToggle = { editing = !editing },
            modifier = modifier.testTag("settings-annual-vacation"),
        ) {
            if (editing) {
                AnnualVacationEditor(
                    state = state,
                    busy = busy,
                    onSave = onSave,
                    onClose = { editing = false },
                    initialAllowanceInput = initialEditorAllowance,
                )
            } else {
                AnnualVacationSummary(settings = settings)
            }
        }
    }
}

@Composable
private fun AnnualVacationSummary(settings: AnnualVacationSettings) {
    val pending = settings.pending
    val active = settings.active
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(space = 12.dp)
    ) {
        if (active == null && pending == null) {
            AnnualVacationEmptyTile()
        }
        active?.let { policy ->
            AnnualVacationMetricTile(policy = policy)
        }
        pending?.let { scheduled ->
            AnnualVacationScheduledBanner(
                policy = scheduled,
                effectiveDate = settings.nextEffectiveDate
            )
        }
    }
}

@Composable
private fun AnnualVacationEmptyTile() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size = 20.dp)
            )
            Text(
                text = str(Res.string.settings_annual_vacation_unset),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AnnualVacationMetricTile(
    policy: AnnualVacationPolicy,
    modifier: Modifier = Modifier
) {
    val language = LocalAppLanguage.current
    val allowanceFormatted = isolated(DisplayFormats.number(policy.annualAllowanceDays, language))
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(all = 16.dp),
            verticalArrangement = Arrangement.spacedBy(space = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space = 12.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(size = 44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(size = 22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(weight = 1f)) {
                    Text(
                        text = str(Res.string.annual_vacation_allowance),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = allowanceFormatted,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            CarryOverBadge(carryOver = policy.carryOverUnusedDays)
        }
    }
}

@Composable
private fun CarryOverBadge(carryOver: Boolean) {
    val containerColor by animateColorAsState(
        targetValue = if (carryOver) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "carryover-badge-color"
    )
    val contentColor by animateColorAsState(
        targetValue = if (carryOver) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "carryover-badge-text-color"
    )
    Surface(
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp)
        ) {
            Icon(
                imageVector = if (carryOver) Icons.Default.Check else Icons.Default.Info,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(size = 16.dp)
            )
            Text(
                text = str(
                    Res.string.settings_summary_value,
                    str(Res.string.annual_vacation_carryover),
                    str(if (carryOver) Res.string.settings_on else Res.string.settings_off)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = contentColor
            )
        }
    }
}

@Composable
private fun AnnualVacationScheduledBanner(
    policy: AnnualVacationPolicy,
    effectiveDate: LocalDate
) {
    val language = LocalAppLanguage.current
    val scheduledDate = isolated(DisplayFormats.date(effectiveDate, language))
    val allowanceFormatted = isolated(DisplayFormats.number(policy.annualAllowanceDays, language))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(all = 14.dp),
            verticalArrangement = Arrangement.spacedBy(space = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(size = 18.dp)
                )
                Text(
                    text = str(Res.string.annual_vacation_scheduled, scheduledDate),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(space = 4.dp)) {
                Text(
                    text = str(
                        Res.string.settings_summary_value,
                        str(Res.string.annual_vacation_allowance),
                        allowanceFormatted
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = str(
                        Res.string.settings_summary_value,
                        str(Res.string.annual_vacation_carryover),
                        str(if (policy.carryOverUnusedDays) Res.string.settings_on else Res.string.settings_off)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
private fun AnnualVacationEditor(
    state: AnnualVacationSettingsUiState,
    busy: Boolean,
    onSave: (Int, Boolean, Int) -> Unit,
    onClose: () -> Unit,
    initialAllowanceInput: String?,
) {
    val settings = requireNotNull(state.configuration)
    val language = LocalAppLanguage.current
    val savedPolicy = settings.pending ?: settings.active
    val year = settings.nextEffectiveYear
    var allowanceInput by rememberSaveable { mutableStateOf(value = initialAllowanceInput) }
    var carryoverInput by rememberSaveable { mutableStateOf<Boolean?>(value = null) }
    var submittedYear by rememberSaveable { mutableStateOf<Int?>(value = null) }
    val allowanceText = allowanceInput ?: savedPolicy?.let {
        DisplayFormats.number(it.annualAllowanceDays, language)
    }.orEmpty()
    val allowanceDays = parseWholeDays(allowanceText)
    val carryOver = carryoverInput ?: savedPolicy?.carryOverUnusedDays ?: false
    val changed = allowanceDays != savedPolicy?.annualAllowanceDays ||
            carryOver != (savedPolicy?.carryOverUnusedDays ?: false)
    val requestedYear = submittedYear
    val submittedPolicy = if (requestedYear != null && allowanceDays != null) {
        AnnualVacationPolicy(requestedYear, allowanceDays, carryOver)
    } else null
    val applied = state.saveResult == PlanMutationResult.Applied && submittedPolicy != null &&
            (submittedPolicy == settings.pending || submittedPolicy == settings.active)
    val closeEditor by rememberUpdatedState(newValue = onClose)
    LaunchedEffect(applied) {
        if (applied) closeEditor()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(space = 14.dp)
    ) {
        AnnualAllowanceField(
            allowanceText = allowanceText,
            enabled = !busy,
            invalid = allowanceText.isNotBlank() && allowanceDays == null,
            onChange = {
                allowanceInput = it
                submittedYear = null
            }
        )
        CarryOverSwitchTile(
            carryOver = carryOver,
            enabled = !busy,
            onChange = {
                carryoverInput = it
                submittedYear = null
            }
        )
        AnnualVacationEffectiveBanner(settings = settings, changed = changed)
        AnnualVacationSaveSection(
            state = state,
            submittedYear = submittedYear,
            enabled = !busy && changed && allowanceDays != null,
            onSaveClick = {
                allowanceDays?.let { days ->
                    allowanceInput = allowanceText
                    carryoverInput = carryOver
                    submittedYear = year
                    onSave(days, carryOver, year)
                }
            }
        )
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
        label = { Text(text = str(Res.string.annual_vacation_allowance)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = if (invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        },
        supportingText = if (invalid) {
            { Text(text = str(Res.string.annual_vacation_invalid)) }
        } else null,
        enabled = enabled,
        isError = invalid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings-annual-vacation-allowance")
    )
}

@Composable
private fun CarryOverSwitchTile(
    carryOver: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (carryOver) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "carryover-tile-container"
    )
    val borderColor by animateColorAsState(
        targetValue = if (carryOver) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        label = "carryover-tile-border"
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(
                value = carryOver,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onChange
            )
            .testTag("settings-annual-vacation-carryover"),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        border = BorderStroke(width = 1.dp, color = borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (carryOver) Icons.Default.Check else Icons.Default.Info,
                contentDescription = null,
                tint = if (carryOver) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size = 20.dp)
            )
            Text(
                text = str(Res.string.annual_vacation_carryover),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(weight = 1f)
            )
            Switch(
                checked = carryOver,
                onCheckedChange = null,
                enabled = enabled
            )
        }
    }
}

@Composable
private fun AnnualVacationEffectiveBanner(
    settings: AnnualVacationSettings,
    changed: Boolean
) {
    val language = LocalAppLanguage.current
    val startsOn = isolated(DisplayFormats.date(settings.nextEffectiveDate, language))
    val message = str(
        if (settings.pending?.effectiveYear == settings.nextEffectiveYear && !changed) {
            Res.string.annual_vacation_scheduled
        } else {
            Res.string.annual_vacation_effective
        },
        startsOn
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(size = 18.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}

@Composable
private fun AnnualVacationSaveSection(
    state: AnnualVacationSettingsUiState,
    submittedYear: Int?,
    enabled: Boolean,
    onSaveClick: () -> Unit
) {
    val reducedMotion = reducedMotionEnabled()
    val showFeedback = submittedYear != null && state.saveResult != PlanMutationResult.Applied
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(space = 14.dp),
    ) {
        AnimatedVisibility(
            visible = showFeedback,
            enter = if (reducedMotion) EnterTransition.None else fadeIn() + expandVertically(),
            exit = if (reducedMotion) ExitTransition.None else fadeOut() + shrinkVertically(),
        ) {
            AnnualVacationSaveFeedback(mutation = state.saveResult)
        }
        Button(
            onClick = onSaveClick,
            enabled = enabled,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().testTag("settings-annual-vacation-save"),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(size = 18.dp),
                )
                Text(text = str(Res.string.save))
            }
        }
    }
}

@Composable
private fun AnnualVacationSaveFeedback(mutation: PlanMutationResult?) {
    when (mutation) {
        PlanMutationResult.StalePreview -> AnnualVacationError(message = str(Res.string.annual_vacation_stale))
        else -> PlanMutationFeedback(mutation = mutation)
    }
}

@Composable
private fun AnnualVacationError(message: String) {
    Text(
        text = message,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.error
    )
}
