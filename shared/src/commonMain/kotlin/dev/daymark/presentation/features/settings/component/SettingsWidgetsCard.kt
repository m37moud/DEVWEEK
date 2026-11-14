package dev.daymark.presentation.features.settings.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.daymark.app.HostPlatform
import dev.daymark.presentation.features.settings.SettingsUiState
import dev.daymark.resources.*
import dev.daymark.settings.str
import dev.daymark.widgets.WidgetSetupStatus

internal data class WidgetSetupActions(val add: () -> Unit, val refresh: () -> Unit)

@Composable
internal fun SettingsWidgetsCard(
    state: SettingsUiState,
    actions: WidgetSetupActions,
    platform: HostPlatform,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                str(Res.string.onboarding_widget_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                str(
                    when {
                        state.widgetPinPending -> Res.string.widget_pin_requested
                        state.widgetStatus == WidgetSetupStatus.ADDED -> Res.string.widget_added
                        state.widgetStatus == WidgetSetupStatus.CHECKING -> Res.string.widget_checking
                        state.widgetStatus == WidgetSetupStatus.UNKNOWN -> Res.string.widget_setup_unknown
                        platform == HostPlatform.IOS -> Res.string.widget_help_ios
                        platform == HostPlatform.ANDROID -> Res.string.widget_help_android
                        else -> Res.string.widget_help_desktop
                    }
                ), modifier = Modifier.testTag("settings-widget-guidance")
            )
            if (state.widgetStatus == WidgetSetupStatus.UNKNOWN || state.widgetPinPending) {
                Button(onClick = actions.refresh) { Text(str(Res.string.widget_refresh_status)) }
            } else if (state.widgetStatus == WidgetSetupStatus.NOT_ADDED && state.widgetCanPin) {
                Button(
                    onClick = actions.add,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(str(Res.string.add_widget_to_home)) }
            }
        }
    }
}
