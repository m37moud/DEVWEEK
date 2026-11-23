package dev.daymark.presentation.features.settings.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Clock
import dev.daymark.app.HostPlatform
import dev.daymark.app.reducedMotionEnabled
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
import dev.daymark.resources.settings_tracking
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.str
import dev.daymark.ui.theme.LocalConfigurationHealth

@Composable
fun SettingsTrackingCard(
    platform: HostPlatform,
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier
) {
    val health = LocalConfigurationHealth.current
    var editing by rememberSaveable { mutableStateOf(value = false) }
    val enabled = !state.busy && !state.requestingReminderPermission
    val reducedMotion = reducedMotionEnabled()

    SettingsEditorCard(
        header = SettingsEditorHeader(
            title = Res.string.settings_tracking,
            editTag = "settings-tracking-edit",
            closeLabel = Res.string.settings_done,
            editing = editing,
            enabled = enabled
        ),
        onToggle = { editing = !editing },
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = editing,
            transitionSpec = {
                if (reducedMotion) {
                    (EnterTransition.None togetherWith ExitTransition.None) using null
                } else {
                    (fadeIn(animationSpec = tween(durationMillis = 200)) + expandVertically()) togetherWith
                            (fadeOut(animationSpec = tween(durationMillis = 150)) + shrinkVertically())
                }
            },
            label = "tracking-card-mode-transition"
        ) { isEditing ->
            if (isEditing) {
                TrackingControls(
                    platform = platform,
                    state = state,
                    actions = actions,
                    reducedMotion = reducedMotion
                )
            } else {
                TrackingSummary(
                    platform = platform,
                    state = state,
                    reducedMotion = reducedMotion
                )
            }
        }
        TrackingWarnings(
            platform = platform,
            state = state,
            reducedMotion = reducedMotion
        )
        if (health.needsAttention) {
            ConfigurationAttentionCallout(reducedMotion = reducedMotion)
        }
    }
}

@Composable
private fun TrackingSummary(
    platform: HostPlatform,
    state: SettingsUiState,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val isLocationEnabled = state.capabilities.preciseLocationAvailable()
    val locationStatusText = str(resource = if (isLocationEnabled) Res.string.enabled else Res.string.disabled)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(space = 10.dp)
    ) {
        TrackingSummaryRow(
            icon = Icons.Default.LocationOn,
            label = str(resource = Res.string.location_health),
            statusText = locationStatusText,
            isPositive = isLocationEnabled,
            reducedMotion = reducedMotion
        )
        when (platform) {
            HostPlatform.ANDROID -> {
                TrackingSummaryRow(
                    icon = FontAwesomeIcons.Solid.Clock,
                    label = str(resource = Res.string.automation),
                    statusText = str(
                        resource = if (state.automaticEnabled) Res.string.automation_on else Res.string.automation_off
                    ),
                    isPositive = state.automaticEnabled,
                    reducedMotion = reducedMotion
                )
            }
            HostPlatform.IOS -> {
                val remindersEnabled = state.settings.remindersEnabled
                TrackingSummaryRow(
                    icon = FontAwesomeIcons.Solid.Clock,
                    label = str(resource = Res.string.settings_tracking),
                    statusText = str(
                        resource = if (remindersEnabled) Res.string.settings_reminders_on else Res.string.reminders_off
                    ),
                    isPositive = remindersEnabled,
                    reducedMotion = reducedMotion
                )
            }
            else -> Unit
        }
    }
}

@Composable
private fun TrackingSummaryRow(
    icon: ImageVector,
    label: String,
    statusText: String,
    isPositive: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
            modifier = Modifier.weight(weight = 1f, fill = false)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(size = 20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        TrackingStatusBadge(
            text = statusText,
            isPositive = isPositive,
            reducedMotion = reducedMotion
        )
    }
}

@Composable
private fun TrackingStatusBadge(
    text: String,
    isPositive: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val badgeBg by animateColorAsState(
        targetValue = if (isPositive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        animationSpec = tween(durationMillis = 200),
        label = "status-badge-bg"
    )
    val badgeContentColor by animateColorAsState(
        targetValue = if (isPositive) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        },
        animationSpec = tween(durationMillis = 200),
        label = "status-badge-content"
    )

    Surface(
        color = badgeBg,
        contentColor = badgeContentColor,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                if (reducedMotion) {
                    (EnterTransition.None togetherWith ExitTransition.None) using null
                } else {
                    (fadeIn(animationSpec = tween(durationMillis = 150)) + scaleIn(initialScale = 0.92f)) togetherWith
                            (fadeOut(animationSpec = tween(durationMillis = 100)) + scaleOut(targetScale = 0.92f))
                }
            },
            label = "status-badge-text-transition"
        ) { animatedText ->
            Text(
                text = animatedText,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun TrackingControls(
    platform: HostPlatform,
    state: SettingsUiState,
    actions: SettingsActions,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(space = 16.dp)
    ) {
        LocationStatusSection(
            capabilities = state.capabilities,
            onOpenSystemSettings = actions.openSystemSettings,
            reducedMotion = reducedMotion
        )
        when (platform) {
            HostPlatform.ANDROID -> {
                AndroidAutomationSection(
                    automaticEnabled = state.automaticEnabled,
                    busy = state.busy,
                    onToggleAutomation = actions.setAutomation,
                    reducedMotion = reducedMotion
                )
            }
            HostPlatform.IOS -> {
                IosRemindersSection(
                    state = state,
                    actions = actions,
                    reducedMotion = reducedMotion
                )
            }
            else -> Unit
        }
    }
}

@Composable
private fun TrackingSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(all = 16.dp),
            verticalArrangement = Arrangement.spacedBy(space = 12.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun LocationStatusSection(
    capabilities: LocationCapabilities?,
    onOpenSystemSettings: () -> Unit,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val isEnabled = capabilities.preciseLocationAvailable()

    TrackingSectionCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
                modifier = Modifier.weight(weight = 1f, fill = false)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(size = 20.dp)
                )
                Text(
                    text = str(resource = Res.string.location_health),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            TrackingStatusBadge(
                text = str(resource = if (isEnabled) Res.string.enabled else Res.string.disabled),
                isPositive = isEnabled,
                reducedMotion = reducedMotion
            )
        }

        OutlinedButton(
            onClick = onOpenSystemSettings,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = str(resource = Res.string.open_settings))
        }
    }
}

@Composable
private fun AndroidAutomationSection(
    automaticEnabled: Boolean,
    busy: Boolean,
    onToggleAutomation: (Boolean) -> Unit,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    var isInfoShow by remember { mutableStateOf(value = false) }
    val infoRotation by animateFloatAsState(
        targetValue = if (isInfoShow) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "automation-info-rotation"
    )
    val infoTint by animateColorAsState(
        targetValue = if (isInfoShow) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 200),
        label = "automation-info-tint"
    )

    TrackingSectionCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
                modifier = Modifier.weight(weight = 1f, fill = false)
            ) {
                Icon(
                    imageVector = FontAwesomeIcons.Solid.Clock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(size = 18.dp)
                )
                Text(
                    text = str(resource = Res.string.automation),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space = 4.dp)
            ) {
                IconButton(
                    onClick = { isInfoShow = !isInfoShow },
                    modifier = Modifier.size(size = 32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = str(resource = Res.string.info),
                        tint = infoTint,
                        modifier = Modifier
                            .size(size = 18.dp)
                            .rotate(degrees = if (reducedMotion) 0f else infoRotation)
                    )
                }
                TrackingStatusBadge(
                    text = str(
                        resource = if (automaticEnabled) Res.string.automation_on else Res.string.automation_off
                    ),
                    isPositive = automaticEnabled,
                    reducedMotion = reducedMotion
                )
            }
        }

        AnimatedVisibility(
            visible = isInfoShow,
            enter = if (reducedMotion) EnterTransition.None else (fadeIn() + expandVertically()),
            exit = if (reducedMotion) ExitTransition.None else (fadeOut() + shrinkVertically()),
            label = "automation-help-visibility"
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = str(resource = Res.string.automation_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(all = 12.dp)
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
                .testTag(tag = "settings-automation")
        ) {
            Text(
                text = str(
                    resource = if (automaticEnabled) {
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
private fun IosRemindersSection(
    state: SettingsUiState,
    actions: SettingsActions,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val remindersEnabled = state.settings.remindersEnabled

    TrackingSectionCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
                modifier = Modifier.weight(weight = 1f, fill = false)
            ) {
                Icon(
                    imageVector = FontAwesomeIcons.Solid.Clock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(size = 18.dp)
                )
                Text(
                    text = str(resource = Res.string.settings_tracking),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            TrackingStatusBadge(
                text = str(
                    resource = if (remindersEnabled) Res.string.settings_reminders_on else Res.string.reminders_off
                ),
                isPositive = remindersEnabled,
                reducedMotion = reducedMotion
            )
        }

        Text(
            text = str(resource = Res.string.ios_manual_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            enabled = !state.busy && !state.requestingReminderPermission,
            onClick = {
                if (remindersEnabled) {
                    actions.setRemindersEnabled(false)
                } else {
                    actions.requestReminders()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = str(
                    resource = if (remindersEnabled) Res.string.disable_reminders else Res.string.reminders_optional
                )
            )
        }
    }
}

@Composable
private fun TrackingWarnings(
    platform: HostPlatform,
    state: SettingsUiState,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val warning = when {
        platform == HostPlatform.ANDROID && state.permissionDenied -> Res.string.automation_denied
        platform == HostPlatform.IOS && state.reminderPermissionGranted == false -> Res.string.reminders_denied
        else -> null
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(space = 8.dp)
    ) {
        AnimatedVisibility(
            visible = warning != null,
            enter = if (reducedMotion) EnterTransition.None else (fadeIn() + expandVertically()),
            exit = if (reducedMotion) ExitTransition.None else (fadeOut() + shrinkVertically()),
            label = "tracking-warning-visibility"
        ) {
            warning?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(all = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(size = 18.dp)
                        )
                        Spacer(modifier = Modifier.width(width = 8.dp))
                        Text(
                            text = str(resource = it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        if (platform == HostPlatform.IOS) {
            val throughDate = LocalConfigurationHealth.current.reminderThrough
            SettingsSummaryText(
                summary = if (throughDate == null) {
                    str(resource = Res.string.reminder_horizon)
                } else {
                    str(
                        resource = Res.string.horizon_through,
                        args = arrayOf(DisplayFormats.date(date = throughDate, language = LocalAppLanguage.current))
                    )
                }
            )
        }
    }
}

private fun LocationCapabilities?.preciseLocationAvailable(): Boolean =
    this != null && foregroundGranted && preciseGranted && servicesEnabled

@Composable
private fun ConfigurationAttentionCallout(
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = true,
        enter = if (reducedMotion) EnterTransition.None else (fadeIn() + expandVertically()),
        exit = if (reducedMotion) ExitTransition.None else (fadeOut() + shrinkVertically()),
        label = "attention-callout-visibility"
    ) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(all = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(size = 24.dp)
                )
                Spacer(modifier = Modifier.width(width = 12.dp))
                Text(
                    text = str(resource = Res.string.effects_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
