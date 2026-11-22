package dev.daymark.presentation.features.settings.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.daymark.app.HostPlatform
import dev.daymark.location.LocationCapabilities
import dev.daymark.presentation.features.settings.SettingsActions
import dev.daymark.presentation.features.settings.SettingsUiState
import dev.daymark.resources.Res
import dev.daymark.resources.automation
import dev.daymark.resources.automation_denied
import dev.daymark.resources.automation_help
import dev.daymark.resources.automation_off
import dev.daymark.resources.automation_on
import dev.daymark.resources.disable_automation
import dev.daymark.resources.disable_reminders
import dev.daymark.resources.disabled
import dev.daymark.resources.effects_failed
import dev.daymark.resources.enable_automation
import dev.daymark.resources.enabled
import dev.daymark.resources.horizon_through
import dev.daymark.resources.info
import dev.daymark.resources.ios_manual_help
import dev.daymark.resources.location_health
import dev.daymark.resources.open_settings
import dev.daymark.resources.reminder_horizon
import dev.daymark.resources.reminders_denied
import dev.daymark.resources.reminders_off
import dev.daymark.resources.reminders_optional
import dev.daymark.resources.settings_done
import dev.daymark.resources.settings_reminders_on
import dev.daymark.resources.settings_summary_value
import dev.daymark.resources.settings_tracking
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.str
import dev.daymark.ui.theme.LocalConfigurationHealth

@Composable
internal fun SettingsTrackingCard(platform: HostPlatform, state: SettingsUiState, actions: SettingsActions) {
    val health = LocalConfigurationHealth.current
    var editing by rememberSaveable { mutableStateOf(false) }
    val enabled = !state.busy && !state.requestingReminderPermission
    SettingsEditorCard(
        SettingsEditorHeader(
            Res.string.settings_tracking,
            "settings-tracking-edit",
            Res.string.settings_done,
            editing,
            enabled
        ),
        onToggle = { editing = !editing }
    ) {
        if (editing) TrackingControls(platform, state, actions)
        else TrackingSummary(platform, state)
        TrackingWarnings(platform, state)
        if (health.needsAttention) ConfigurationAttentionCallout()
    }
}

@Composable
private fun TrackingSummary(platform: HostPlatform, state: SettingsUiState) {
    val status = str(if (state.capabilities.preciseLocationAvailable()) Res.string.enabled else Res.string.disabled)
    SettingsSummaryText(str(Res.string.settings_summary_value, str(Res.string.location_health), status))
    when (platform) {
        HostPlatform.ANDROID ->
            SettingsSummaryText(str(if (state.automaticEnabled) Res.string.automation_on else Res.string.automation_off))

        HostPlatform.IOS ->
            SettingsSummaryText(str(if (state.settings.remindersEnabled) Res.string.settings_reminders_on else Res.string.reminders_off))

        else -> Unit
    }
}

@Composable
private fun TrackingControls(platform: HostPlatform, state: SettingsUiState, actions: SettingsActions) {
    LocationStatusSection(state.capabilities, actions.openSystemSettings)
    when (platform) {
        HostPlatform.ANDROID -> AndroidAutomationSection(state.automaticEnabled, state.busy, actions.setAutomation)
        HostPlatform.IOS -> IosRemindersSection(state, actions)
        else -> Unit
    }
}

@Composable
private fun TrackingWarnings(platform: HostPlatform, state: SettingsUiState) {
    val warning = when {
        platform == HostPlatform.ANDROID && state.permissionDenied -> Res.string.automation_denied
        platform == HostPlatform.IOS && state.reminderPermissionGranted == false -> Res.string.reminders_denied
        else -> null
    }
    warning?.let { Text(str(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    if (platform == HostPlatform.IOS) {
        val throughDate = LocalConfigurationHealth.current.reminderThrough
        SettingsSummaryText(
            if (throughDate == null) str(Res.string.reminder_horizon)
            else str(Res.string.horizon_through, DisplayFormats.date(throughDate, LocalAppLanguage.current))
        )
    }
}

private fun LocationCapabilities?.preciseLocationAvailable(): Boolean =
    this != null && foregroundGranted && preciseGranted && servicesEnabled

@Composable
private fun LocationStatusSection(
    capabilities: LocationCapabilities?,
    onOpenSystemSettings: () -> Unit
) {
    val isEnabled = capabilities.preciseLocationAvailable()

    val badgeBg by animateColorAsState(
        targetValue = if (isEnabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        }
    )
    val badgeContentColor by animateColorAsState(
        targetValue = if (isEnabled) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        }
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = str(
                        Res.string.location_health,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Surface(
                color = badgeBg,
                contentColor = badgeContentColor,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = str(if (isEnabled) Res.string.enabled else Res.string.disabled),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        OutlinedButton(
            onClick = onOpenSystemSettings,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = str(Res.string.open_settings))
        }
    }
}

@Composable
private fun AndroidAutomationSection(
    automaticEnabled: Boolean,
    busy: Boolean,
    onToggleAutomation: (Boolean) -> Unit
) {
    val badgeBg by animateColorAsState(
        targetValue = if (automaticEnabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        }
    )
    val badgeContentColor by animateColorAsState(
        targetValue = if (automaticEnabled) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        }
    )
    var isInfoShow by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(

            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = { isInfoShow = !isInfoShow }
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = str(Res.string.info),
                        //                    tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = str(Res.string.automation),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )


            }
            Surface(
                color = badgeBg,
                contentColor = badgeContentColor,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = str(if (automaticEnabled) Res.string.automation_on else Res.string.automation_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
        AnimatedVisibility(visible = isInfoShow, label = str(Res.string.automation)) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {

                Text(
                    text = str(Res.string.automation_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)

                )
            }
        }

        Button(
            enabled = !busy,
            onClick = { onToggleAutomation(!automaticEnabled) },
            colors = if (automaticEnabled) {
                ButtonDefaults.filledTonalButtonColors()
            } else {
                ButtonDefaults.buttonColors()
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings-automation")
        ) {
            Text(
                text = str(
                    if (automaticEnabled) {
                        Res.string.disable_automation
                    } else {
                        Res.string.enable_automation
                    }
                )
            )
        }

    }
}

@Composable
private fun IosRemindersSection(state: SettingsUiState, actions: SettingsActions) {
    val remindersEnabled = state.settings.remindersEnabled
    SettingsSummaryText(str(Res.string.ios_manual_help))
    if (!remindersEnabled) SettingsSummaryText(str(Res.string.reminders_off))
    Button(
        enabled = !state.busy && !state.requestingReminderPermission,
        onClick = {
            if (remindersEnabled) actions.setRemindersEnabled(false)
            else actions.requestReminders()
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(str(if (remindersEnabled) Res.string.disable_reminders else Res.string.reminders_optional))
    }
}

@Composable
private fun ConfigurationAttentionCallout() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = str(Res.string.effects_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
