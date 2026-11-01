package dev.daymark.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.daymark.resources.*
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import dev.daymark.reminders.ReminderCopy
import kotlinx.coroutines.CancellationException
import dev.daymark.data.TrackerEntity
import dev.daymark.settings.*
import dev.daymark.ui.*
import kotlinx.coroutines.flow.*

private data class BootState(val settings: AppSettings, val tracker: TrackerEntity?, val failed: Boolean = false)
@Composable fun App(graph: AppGraph) {
    var retry by remember { mutableIntStateOf(0) }
    var active by remember { mutableStateOf(false) }
    var health by remember { mutableStateOf(ConfigurationHealth()) }
    LifecycleStartEffect(graph) { active = true; onStopOrDispose { active = false } }
    val bootFlow = remember(graph, retry) {
        flow { emitAll(combine(graph.settings.observe(), graph.db.dao().observeTracker()) { settings, tracker -> BootState(settings, tracker) }) }
            .catch { emit(BootState(AppSettings(), null, failed = true)) }
    }
    val boot by bootFlow.collectAsStateWithLifecycle(initialValue = null)
    val system = systemLanguageTags()
    val ready = boot
    val language = ready?.let { effectiveLanguage(it.settings.language, system) }
    var applied by remember { mutableStateOf<AppLanguage?>(null) }
    LaunchedEffect(language) { if (language != null) { applyAppLocale(language); applied = language } }
    val shown = applied
    if (ready == null || shown == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    WithPlatformLocale(shown) {
        CompositionLocalProvider(LocalAppLanguage provides shown, LocalConfigurationHealth provides health,
            LocalLayoutDirection provides if (shown.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            val reminderCopy = ReminderCopy(shown.tag,str(Res.string.reminder_title),str(Res.string.reminder_body))
            LaunchedEffect(active,shown,ready.settings,ready.tracker?.id,ready.tracker?.changeRevision) {
                if (active && !ready.failed && language == shown) {
                    try {
                        graph.repository.reconcile(graph.clock.now())
                        health = graph.configuration.refresh(ready.settings,shown,reminderCopy,graph.platform)
                    }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { health = ConfigurationHealth(scheduleOk = false) }
                }
            }
            DaymarkTheme(ready.settings.theme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        if (ready.failed) {
                            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                Text(str(Res.string.storage_unavailable), modifier = Modifier.testTag("app-storage-error"))
                                Button(onClick = { retry++ }) { Text(str(Res.string.retry)) }
                            }
                        } else AppNavigation(graph, ready.tracker, ready.settings)
                    }
                }
            }
        }
    }
}
