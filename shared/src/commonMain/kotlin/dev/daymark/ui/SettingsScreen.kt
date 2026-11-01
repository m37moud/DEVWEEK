package dev.daymark.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.daymark.app.*
import dev.daymark.resources.*
import dev.daymark.settings.*
import kotlinx.coroutines.launch

@Composable fun SettingsScreen(model: SettingsViewModel,graph: AppGraph) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val language = LocalAppLanguage.current
    val health = LocalConfigurationHealth.current
    var name by rememberSaveable { mutableStateOf<String?>(null) }
    var reminderDenied by remember { mutableStateOf(false) }
    var requesting by remember { mutableStateOf(false) }
    LifecycleStartEffect(model) { model.refreshPermissions(); onStopOrDispose {} }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).testTag("settings-root"),verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(str(Res.string.settings),style = MaterialTheme.typography.headlineLarge)
        if (!state.ready) CircularProgressIndicator()
        if (state.storageError) Text(str(Res.string.local_save_failed),color = MaterialTheme.colorScheme.error)
        OutlinedTextField(value = name ?: state.settings.displayName,onValueChange = { name = it },enabled = !state.busy,
            label = { Text(str(Res.string.profile_name)) },modifier = Modifier.fillMaxWidth().testTag("settings-name"))
        if (state.invalidName) Text(str(Res.string.invalid_name),color = MaterialTheme.colorScheme.error)
        TextButton(enabled = !state.busy,onClick = { model.setDisplayName(name ?: state.settings.displayName) }) { Text(str(Res.string.save)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(LanguagePreference.SYSTEM to Res.string.language_system,LanguagePreference.EN to Res.string.language_english,LanguagePreference.AR to Res.string.language_arabic).forEach { (choice,label) ->
                FilterChip(selected = state.settings.language == choice,enabled = !state.busy,onClick = { model.setLanguage(choice) },label = { Text(str(label)) },modifier = Modifier.testTag("settings-language-${choice.name.lowercase()}"))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(ThemePreference.SYSTEM to Res.string.theme_system,ThemePreference.LIGHT to Res.string.theme_light,ThemePreference.DARK to Res.string.theme_dark).forEach { (choice,label) ->
                FilterChip(selected = state.settings.theme == choice,enabled = !state.busy,onClick = { model.setTheme(choice) },label = { Text(str(label)) },modifier = Modifier.testTag("settings-theme-${choice.name.lowercase()}"))
            }
        }
        Text(str(Res.string.location_health,str(if (state.capabilities?.let { it.foregroundGranted && it.preciseGranted && it.servicesEnabled } == true) Res.string.enabled else Res.string.disabled)))
        TextButton(onClick = model::openSystemSettings) { Text(str(Res.string.open_settings)) }
        if (graph.platform == HostPlatform.ANDROID) {
            Text(str(Res.string.automation),style = MaterialTheme.typography.titleLarge)
            Text(str(Res.string.automation_help))
            Text(str(if (state.automaticEnabled) Res.string.automation_on else Res.string.automation_off))
            Button(enabled = !state.busy,onClick = { model.setAutomation(!state.automaticEnabled) },modifier = Modifier.testTag("settings-automation")) {
                Text(str(if (state.automaticEnabled) Res.string.disable_automation else Res.string.enable_automation))
            }
            if (state.permissionDenied) Text(str(Res.string.automation_denied),color = MaterialTheme.colorScheme.error)
        } else if (graph.platform == HostPlatform.IOS) {
            Text(str(Res.string.ios_manual_help))
            Text(str(Res.string.reminder_horizon))
            if (!state.settings.remindersEnabled) Text(str(Res.string.reminders_off))
            Button(enabled = !state.busy && !requesting,onClick = {
                if (state.settings.remindersEnabled) model.setRemindersEnabled(false)
                else {
                    requesting = true
                    graph.reminders.requestPermission { allowed -> scope.launch {
                        requesting = false; reminderDenied = !allowed
                        if (allowed) model.setRemindersEnabled(true)
                    } }
                }
            }) { Text(str(if (state.settings.remindersEnabled) Res.string.disable_reminders else Res.string.reminders_optional)) }
            if (reminderDenied) Text(str(Res.string.reminders_denied))
            health.reminderThrough?.let { Text(str(Res.string.horizon_through,DisplayFormats.date(it,language))) }
        }
        if (health.needsAttention) Text(str(Res.string.effects_failed),color = MaterialTheme.colorScheme.error)
        GoogleConnectionSection(graph)
    }
}
