package dev.daymark.presentation.component

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.daymark.resources.*
import dev.daymark.settings.*
import dev.daymark.settings.str

@Composable
internal fun WidgetSetupGuidance() {
    Text(str(Res.string.onboarding_widget_title), style = MaterialTheme.typography.titleLarge)
    Text(
        str(Res.string.onboarding_widget_help),
        modifier = Modifier.testTag("settings-widget-guidance")
    )
}
