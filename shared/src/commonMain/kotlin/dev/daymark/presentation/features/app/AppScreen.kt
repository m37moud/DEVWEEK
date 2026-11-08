package dev.daymark.presentation.features.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.daymark.app.HostPlatform
import dev.daymark.app.StartupSplash
import dev.daymark.models.BootstrapSnapshot
import dev.daymark.models.settings.AppSettings
import dev.daymark.navigation.AppNavigation
import dev.daymark.presentation.features.app.AppViewModel
import dev.daymark.reminders.ReminderCopy
import dev.daymark.resources.Res
import dev.daymark.resources.app_name
import dev.daymark.resources.reminder_body
import dev.daymark.resources.reminder_title
import dev.daymark.resources.retry
import dev.daymark.resources.storage_unavailable
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.WithPlatformLocale
import dev.daymark.settings.applyAppLocale
import dev.daymark.settings.effectiveLanguage
import dev.daymark.settings.str
import dev.daymark.settings.systemLanguageTags
import dev.daymark.ui.theme.DaymarkTheme
import dev.daymark.ui.theme.LocalConfigurationHealth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppScreen(model: AppViewModel, platform: HostPlatform) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val deleting = state.deleting
    var active by remember { mutableStateOf(false) }
    val health = state.configuration
    LifecycleStartEffect(model) {
        active = true; model.setActive(true); onStopOrDispose {
        active = false; model.setActive(false)
    }
    }
    DisposableEffect(model) { onDispose { model.setActive(false) } }
    val system = systemLanguageTags()
    val ready =
        state.bootstrap ?: if (state.failed) BootstrapSnapshot(AppSettings(), null) else null
    val language = ready?.let { effectiveLanguage(it.settings.language, system) }
    var applied by remember { mutableStateOf<AppLanguage?>(null) }
    LaunchedEffect(language) {
        if (language != null) {
            applyAppLocale(language); applied = language
        }
    }
    val shown = applied
    if (ready == null || shown == null) {
        StartupSplash(active)
        return
    }
    WithPlatformLocale(shown) {
        CompositionLocalProvider(
            LocalAppLanguage provides shown, LocalConfigurationHealth provides health,
            LocalLayoutDirection provides if (shown.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
        ) {
            val reminderCopy = ReminderCopy(
                shown.tag,
                str(Res.string.reminder_title),
                str(Res.string.reminder_body)
            )
            LaunchedEffect(
                active,
                deleting,
                shown,
                ready.settings,
                ready.tracker?.id,
                ready.tracker?.changeRevision
            ) {
                if (active && !deleting && !state.failed && language == shown) {
                    model.refreshConfiguration(ready.settings, shown, reminderCopy)
                }
            }
            DaymarkTheme(ready.settings.theme) {
                Scaffold(
                    topBar = {
                        if (ready.tracker != null) CenterAlignedTopAppBar(
                            title = { Text(str(Res.string.app_name)) }
                        )
                    }
                ) { contentPadding ->
                    Surface(
                        Modifier.fillMaxSize().padding(contentPadding)
                            .consumeWindowInsets(contentPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box(
                            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                        ) {
                            if (state.failed) {
                                Column(
                                    Modifier.padding(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(20.dp)
                                ) {
                                    Text(
                                        str(Res.string.storage_unavailable),
                                        modifier = Modifier.testTag("app-storage-error")
                                    )
                                    Button(onClick = model::retryLoad) { Text(str(Res.string.retry)) }
                                }
                            } else {
                                AppNavigation(model, platform, ready.tracker)
                            }
                        }
                    }
                }
            }
        }
    }
}
