package dev.daymark.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Language
import compose.icons.fontawesomeicons.solid.Sun
import dev.daymark.data.TrackerEntity
import dev.daymark.reminders.ReminderCopy
import dev.daymark.resources.Res
import dev.daymark.resources.app_name
import dev.daymark.resources.reminder_body
import dev.daymark.resources.reminder_title
import dev.daymark.resources.retry
import dev.daymark.resources.storage_unavailable
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.AppSettings
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.WithPlatformLocale
import dev.daymark.settings.applyAppLocale
import dev.daymark.settings.effectiveLanguage
import dev.daymark.settings.str
import dev.daymark.settings.systemLanguageTags
import dev.daymark.ui.AppNavigation
import dev.daymark.ui.DaymarkTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

private data class BootState(
    val settings: AppSettings,
    val tracker: TrackerEntity?,
    val failed: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(graph: AppGraph) {
    var retry by remember { mutableIntStateOf(0) }
    val deleting by graph.dataLifecycle.deleting.collectAsStateWithLifecycle()
    var active by remember { mutableStateOf(false) }
    var health by remember { mutableStateOf(ConfigurationHealth()) }
    LifecycleStartEffect(graph) { active = true; onStopOrDispose { active = false } }
    LaunchedEffect(active, deleting) { graph.lifecycle.onActive(active && !deleting) }
    DisposableEffect(graph) { onDispose { graph.lifecycle.onActive(false) } }
    val bootFlow = remember(graph, retry) {
        flow {
            emitAll(
                combine(
                    graph.settings.observe(),
                    graph.db.dao().observeTracker()
                ) { settings, tracker -> BootState(settings, tracker) })
        }
            .catch { emit(BootState(AppSettings(), null, failed = true)) }
    }
    val boot by bootFlow.collectAsStateWithLifecycle(initialValue = null)
    val system = systemLanguageTags()
    val ready = boot
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
                if (active && !deleting && !ready.failed && language == shown) {
                    try {
                        graph.repository.reconcile(graph.clock.now())
                        health = graph.configuration.refresh(
                            ready.settings,
                            shown,
                            reminderCopy,
                            graph.platform
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        health = ConfigurationHealth(scheduleOk = false)
                    }
                }
            }
            DaymarkTheme(ready.settings.theme) {
                Scaffold(
                    topBar = {
                        if (ready.tracker != null) TopAppBar(
                            title = { Text(str(Res.string.app_name)) },
                            actions = {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(
                                        onClick = {},
                                        content = {
                                            Icon(
                                                imageVector = FontAwesomeIcons.Solid.Sun,
                                                contentDescription = str(Res.string.app_name)
                                            )
                                        })

                                    IconButton(
                                        onClick = {},
                                        content = {
                                            Icon(
                                                imageVector = FontAwesomeIcons.Solid.Language,
                                                contentDescription = str(Res.string.app_name)
                                            )
                                        })
                                }
                            }
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
                            if (ready.failed) {
                                Column(
                                    Modifier.padding(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(20.dp)
                                ) {
                                    Text(
                                        str(Res.string.storage_unavailable),
                                        modifier = Modifier.testTag("app-storage-error")
                                    )
                                    Button(onClick = { retry++ }) { Text(str(Res.string.retry)) }
                                }
                            } else AppNavigation(graph, ready.tracker, ready.settings)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainApp() {

}
