package dev.daymark.presentation.features.settings.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.daymark.presentation.features.settings.SettingsUiState
import dev.daymark.resources.Res
import dev.daymark.resources.result_notifications
import dev.daymark.resources.result_notifications_denied
import dev.daymark.resources.result_notifications_help
import dev.daymark.resources.settings_done
import dev.daymark.resources.settings_off
import dev.daymark.resources.settings_on
import dev.daymark.settings.str

@Composable
internal fun SettingsResultNotificationsCard(state: SettingsUiState, onToggleNotifications: (Boolean) -> Unit) {
    val notificationsEnabled = state.settings.resultNotificationsEnabled
    var editing by rememberSaveable { mutableStateOf(false) }
    SettingsEditorCard(
        SettingsEditorHeader(
            Res.string.result_notifications,
            "settings-result-notifications-edit",
            Res.string.settings_done,
            editing,
            !state.busy
        ),
        onToggle = { editing = !editing }
    ) {
        if (editing) ResultNotificationControls(notificationsEnabled, !state.busy, onToggleNotifications)
        else SettingsSummaryText(str(if (notificationsEnabled) Res.string.settings_on else Res.string.settings_off))
        if (state.resultNotificationPermissionDenied) {
            Text(str(Res.string.result_notifications_denied), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ResultNotificationControls(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val label = str(Res.string.result_notifications)
    Switch(
        checked = checked,
        enabled = enabled,
        onCheckedChange = onChange,
        modifier = Modifier.testTag("settings-result-notifications").semantics { contentDescription = label }
    )
    SettingsSummaryText(str(Res.string.result_notifications_help))
}
