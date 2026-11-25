package dev.daymark.presentation.features.setup.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.daymark.location.canRunAutomaticCheck
import dev.daymark.presentation.component.AutomaticAttemptPicker
import dev.daymark.presentation.component.AttendanceOption
import dev.daymark.presentation.component.AttendanceOptionState
import dev.daymark.presentation.features.setup.SetupUiState
import dev.daymark.resources.Res
import dev.daymark.resources.automation
import dev.daymark.resources.automation_denied
import dev.daymark.resources.onboarding_automation_help
import dev.daymark.resources.open_settings
import dev.daymark.resources.result_notifications
import dev.daymark.resources.result_notifications_android_help
import dev.daymark.resources.result_notifications_denied
import dev.daymark.resources.result_notifications_system_settings
import dev.daymark.settings.str

@Composable
internal fun SetupAutomation(state: SetupUiState, actions: SetupActions) {
    val available = !state.interactionBusy && !state.completed
    AttendanceOption(
        state = AttendanceOptionState(
            label = str(Res.string.automation),
            selected = state.draft.automaticEnabled,
            description = str(Res.string.onboarding_automation_help),
            enabled = available
        ),
        onChange = actions.setAutomaticCheck
    )
    if (state.draft.automaticEnabled) {
        AutomaticAttemptPicker(
            limit = state.draft.automaticAttemptLimit,
            enabled = available,
            onSelect = { limit -> actions.updateDraft(state.draft.copy(automaticAttemptLimit = limit)) }
        )
    }
    if (state.draft.automaticEnabled && state.automationCapabilities?.canRunAutomaticCheck == false) {
        Text(text = str(Res.string.automation_denied), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = actions.openSettings, enabled = available) {
            Text(text = str(Res.string.open_settings))
        }
    }
    AttendanceOption(
        state = AttendanceOptionState(
            label = str(Res.string.result_notifications),
            selected = state.draft.resultNotificationsEnabled,
            description = str(Res.string.result_notifications_android_help),
            enabled = available
        ),
        onChange = actions.setResultNotifications
    )
    if (state.draft.resultNotificationsEnabled && state.resultNotificationStatus?.appAllowed == false) {
        Text(
            text = str(Res.string.result_notifications_denied),
            style = MaterialTheme.typography.bodySmall
        )
        TextButton(onClick = actions.openNotificationSettings, enabled = available) {
            Text(text = str(Res.string.result_notifications_system_settings))
        }
    }
}
