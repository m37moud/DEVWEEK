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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Bell
import dev.daymark.app.reducedMotionEnabled
import dev.daymark.presentation.features.settings.SettingsUiState
import dev.daymark.notifications.ResultNotificationChannel
import dev.daymark.notifications.ResultNotificationStatus
import dev.daymark.presentation.component.AttendanceOption
import dev.daymark.presentation.component.AttendanceOptionState
import dev.daymark.resources.result_failure_channel_blocked
import dev.daymark.resources.result_failure_channel_enable
import dev.daymark.resources.result_failure_sound
import dev.daymark.resources.result_failure_sound_system_off
import dev.daymark.resources.result_notifications_android_help
import dev.daymark.resources.result_notifications_system_settings
import dev.daymark.resources.result_success_channel_blocked
import dev.daymark.resources.Res
import dev.daymark.resources.result_notifications
import dev.daymark.resources.result_notifications_denied
import dev.daymark.resources.result_notifications_help
import dev.daymark.resources.result_notifications_hide_help
import dev.daymark.resources.result_notifications_show_help
import dev.daymark.resources.settings_done
import dev.daymark.resources.settings_off
import dev.daymark.resources.settings_on
import dev.daymark.settings.str

data class ResultNotificationActions(
    val setSound: (Boolean) -> Unit = {},
    val enableFailureChannel: () -> Unit = {},
    val openSettings: (ResultNotificationChannel?) -> Unit = {}
)

enum class ResultNotificationsEditorState {
    SUMMARY,
    EDITING,
    EDITING_WITH_HELP
}

private data class NotificationControlState(
    val checked: Boolean,
    val enabled: Boolean,
    val isInfoExpanded: Boolean
)

@Composable
fun SettingsResultNotificationsCard(
    state: SettingsUiState,
    onToggleNotifications: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    initialEditorState: ResultNotificationsEditorState = ResultNotificationsEditorState.SUMMARY,
    androidActions: ResultNotificationActions = ResultNotificationActions()
) {
    val notificationsEnabled = state.settings.resultNotificationsEnabled
    var editing by rememberSaveable {
        mutableStateOf(value = initialEditorState != ResultNotificationsEditorState.SUMMARY)
    }
    var isInfoExpanded by rememberSaveable {
        mutableStateOf(value = initialEditorState == ResultNotificationsEditorState.EDITING_WITH_HELP)
    }
    val reducedMotion = reducedMotionEnabled()

    SettingsEditorCard(
        header = SettingsEditorHeader(
            title = Res.string.result_notifications,
            editTag = "settings-result-notifications-edit",
            closeLabel = Res.string.settings_done,
            editing = editing,
            enabled = !state.busy
        ),
        onToggle = {
            if (!editing) isInfoExpanded = false
            editing = !editing
        },
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
            label = "result-notifications-mode-transition"
        ) { isEditing ->
            if (isEditing) {
                val android = state.androidResultStatus
                if (android != null) {
                    AndroidResultEditor(
                        state = state.copy(busy = state.busy || !editing),
                        status = android,
                        actions = androidActions,
                        onToggle = onToggleNotifications
                    )
                } else {
                    ResultNotificationControlCard(
                        state = NotificationControlState(
                            checked = notificationsEnabled,
                            enabled = editing && !state.busy,
                            isInfoExpanded = isInfoExpanded
                        ),
                        onToggle = { enabled ->
                            if (editing) onToggleNotifications(enabled)
                        },
                        onToggleInfo = {
                            if (editing) isInfoExpanded = !isInfoExpanded
                        },
                        reducedMotion = reducedMotion
                    )
                }
            } else {
                ResultNotificationSummary(
                    checked = notificationsEnabled,
                    reducedMotion = reducedMotion
                )
            }
        }

        val android = state.androidResultStatus
        if (android == null) {
            ResultNotificationPermissionBanner(
                visible = state.resultNotificationPermissionDenied,
                reducedMotion = reducedMotion
            )
        } else if (notificationsEnabled) {
            AndroidResultSettingsLinks(status = android, enabled = !state.busy, actions = androidActions)
        }
    }
}

@Composable
private fun AndroidResultEditor(
    state: SettingsUiState,
    status: ResultNotificationStatus,
    actions: ResultNotificationActions,
    onToggle: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AttendanceOption(
            state = AttendanceOptionState(
                label = str(Res.string.result_notifications),
                selected = state.settings.resultNotificationsEnabled,
                description = str(Res.string.result_notifications_android_help),
                enabled = !state.busy
            ),
            onChange = onToggle
        )
        if (state.settings.resultNotificationsEnabled && status.appAllowed) {
            AndroidFailureControls(state = state, status = status, actions = actions)
        }
    }
}

@Composable
private fun AndroidFailureControls(
    state: SettingsUiState,
    status: ResultNotificationStatus,
    actions: ResultNotificationActions
) {
    if (status.legacyChannelOnly) {
        TextButton(
            onClick = actions.enableFailureChannel,
            enabled = !state.busy && !status.successBlocked
        ) {
            Text(text = str(Res.string.result_failure_channel_enable))
        }
    } else {
        AttendanceOption(
            state = AttendanceOptionState(
                label = str(Res.string.result_failure_sound),
                selected = state.settings.resultFailureSoundEnabled,
                enabled = !state.busy
            ),
            onChange = actions.setSound
        )
    }
}

@Composable
private fun AndroidResultSettingsLinks(
    status: ResultNotificationStatus,
    enabled: Boolean,
    actions: ResultNotificationActions
) {
    if (!status.appAllowed) {
        ResultSettingsLink(str(Res.string.result_notifications_denied), enabled) { actions.openSettings(null) }
        return
    }
    if (status.successBlocked) {
        ResultSettingsLink(str(Res.string.result_success_channel_blocked), enabled) {
            actions.openSettings(ResultNotificationChannel.SUCCESS)
        }
    }
    if (status.failureBlocked || status.failureChannelSilent) {
        val message = if (status.failureBlocked) Res.string.result_failure_channel_blocked
        else Res.string.result_failure_sound_system_off
        ResultSettingsLink(str(message), enabled) { actions.openSettings(ResultNotificationChannel.FAILURE) }
    }
}

@Composable
private fun ResultSettingsLink(message: String, enabled: Boolean, openSettings: () -> Unit) {
    Text(text = message, style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = openSettings, enabled = enabled) {
        Text(text = str(Res.string.result_notifications_system_settings))
    }
}

@Composable
private fun ResultNotificationSummary(
    checked: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val bellTint by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 200),
        label = "result-notification-summary-bell-tint"
    )

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
                imageVector = FontAwesomeIcons.Solid.Bell,
                contentDescription = null,
                tint = bellTint,
                modifier = Modifier.size(size = 18.dp)
            )
            Text(
                text = str(resource = Res.string.result_notifications),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        ResultNotificationStatusBadge(
            checked = checked,
            reducedMotion = reducedMotion
        )
    }
}

@Composable
private fun ResultNotificationStatusBadge(
    checked: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val badgeBg by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = tween(durationMillis = 200),
        label = "result-notification-badge-bg"
    )
    val badgeContentColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 200),
        label = "result-notification-badge-content"
    )
    val statusText = str(resource = if (checked) Res.string.settings_on else Res.string.settings_off)

    Surface(
        color = badgeBg,
        contentColor = badgeContentColor,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = statusText,
            transitionSpec = {
                if (reducedMotion) {
                    (EnterTransition.None togetherWith ExitTransition.None) using null
                } else {
                    (fadeIn(animationSpec = tween(durationMillis = 150)) + scaleIn(initialScale = 0.92f)) togetherWith
                        (fadeOut(animationSpec = tween(durationMillis = 100)) + scaleOut(targetScale = 0.92f))
                }
            },
            label = "result-notification-badge-text"
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
private fun ResultNotificationControlCard(
    state: NotificationControlState,
    onToggle: (Boolean) -> Unit,
    onToggleInfo: () -> Unit,
    reducedMotion: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(all = 16.dp),
            verticalArrangement = Arrangement.spacedBy(space = 12.dp)
        ) {
            ResultNotificationControlRow(
                state = state,
                onToggle = onToggle,
                onToggleInfo = onToggleInfo,
                reducedMotion = reducedMotion
            )
            ResultNotificationInfoCallout(
                visible = state.isInfoExpanded,
                reducedMotion = reducedMotion
            )
        }
    }
}

@Composable
private fun ResultNotificationControlRow(
    state: NotificationControlState,
    onToggle: (Boolean) -> Unit,
    onToggleInfo: () -> Unit,
    reducedMotion: Boolean
) {
    val label = str(resource = Res.string.result_notifications)
    val helpActionLabel = str(
        resource = if (state.isInfoExpanded) {
            Res.string.result_notifications_hide_help
        } else {
            Res.string.result_notifications_show_help
        }
    )
    val bellTint by animateColorAsState(
        targetValue = if (state.checked) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 200),
        label = "result-notification-control-bell-tint"
    )
    val infoRotation by animateFloatAsState(
        targetValue = if (state.isInfoExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "result-notification-info-rotation"
    )
    val infoTint by animateColorAsState(
        targetValue = if (state.isInfoExpanded) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 200),
        label = "result-notification-info-tint"
    )

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
                imageVector = FontAwesomeIcons.Solid.Bell,
                contentDescription = null,
                tint = bellTint,
                modifier = Modifier.size(size = 18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 4.dp)
        ) {
            IconButton(
                onClick = onToggleInfo,
                enabled = state.enabled
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = helpActionLabel,
                    tint = infoTint,
                    modifier = Modifier
                        .size(size = 18.dp)
                        .rotate(degrees = if (reducedMotion) 0f else infoRotation)
                )
            }
            Switch(
                checked = state.checked,
                enabled = state.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier
                    .testTag(tag = "settings-result-notifications")
                    .semantics { contentDescription = label }
            )
        }
    }
}

@Composable
private fun ResultNotificationInfoCallout(
    visible: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = if (reducedMotion) {
            EnterTransition.None
        } else {
            fadeIn(animationSpec = tween(durationMillis = 200)) + expandVertically()
        },
        exit = if (reducedMotion) {
            ExitTransition.None
        } else {
            fadeOut(animationSpec = tween(durationMillis = 150)) + shrinkVertically()
        },
        label = "result-notifications-info-expansion",
        modifier = modifier
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(all = 12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(space = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(size = 16.dp)
                        .padding(top = 2.dp)
                )
                Text(
                    text = str(resource = Res.string.result_notifications_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ResultNotificationPermissionBanner(
    visible: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = if (reducedMotion) {
            EnterTransition.None
        } else {
            fadeIn(animationSpec = tween(durationMillis = 200)) + expandVertically()
        },
        exit = if (reducedMotion) {
            ExitTransition.None
        } else {
            fadeOut(animationSpec = tween(durationMillis = 150)) + shrinkVertically()
        },
        label = "result-notifications-permission-denied",
        modifier = modifier
    ) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(all = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(size = 18.dp)
                )
                Spacer(modifier = Modifier.width(width = 10.dp))
                Text(
                    text = str(resource = Res.string.result_notifications_denied),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
