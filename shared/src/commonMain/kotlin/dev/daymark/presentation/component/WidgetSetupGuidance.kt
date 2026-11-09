package dev.daymark.presentation.component

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.daymark.app.HostPlatform
import dev.daymark.resources.*
import dev.daymark.settings.*
import dev.daymark.settings.str

@Composable
internal fun WidgetSetupGuidance(platform: HostPlatform = HostPlatform.ANDROID) {
    val helpText = when (platform) {
        HostPlatform.ANDROID -> Res.string.widget_help_android
        HostPlatform.IOS -> Res.string.widget_help_ios
        HostPlatform.TEST -> Res.string.widget_help_desktop
    }
    Text(str(Res.string.onboarding_widget_title), style = MaterialTheme.typography.titleLarge)
    Text(
        str(helpText),
        modifier = Modifier.testTag("settings-widget-guidance")
    )
}
