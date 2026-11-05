package dev.daymark.presentation.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.daymark.app.HostPlatform
import dev.daymark.models.settings.AppSettings
import dev.daymark.presentation.component.WidgetSetupGuidance
import dev.daymark.presentation.component.label
import dev.daymark.presentation.features.settings.component.GoogleConnectionSection
import dev.daymark.presentation.features.settings.component.ReportActions
import dev.daymark.reporting.REPORT_TEXT_LIMIT
import dev.daymark.resources.Res
import dev.daymark.resources.automation
import dev.daymark.resources.automation_denied
import dev.daymark.resources.automation_help
import dev.daymark.resources.automation_off
import dev.daymark.resources.automation_on
import dev.daymark.resources.disable_automation
import dev.daymark.resources.disable_reminders
import dev.daymark.resources.disabled
import dev.daymark.resources.effects_failed
import dev.daymark.resources.enable_automation
import dev.daymark.resources.enabled
import dev.daymark.resources.horizon_through
import dev.daymark.resources.invalid_name
import dev.daymark.resources.ios_manual_help
import dev.daymark.resources.language_arabic
import dev.daymark.resources.language_english
import dev.daymark.resources.language_system
import dev.daymark.resources.local_save_failed
import dev.daymark.resources.location_health
import dev.daymark.resources.open_settings
import dev.daymark.resources.profile_name
import dev.daymark.resources.profile_too_long
import dev.daymark.resources.reminder_horizon
import dev.daymark.resources.reminders_denied
import dev.daymark.resources.reminders_off
import dev.daymark.resources.reminders_optional
import dev.daymark.resources.save
import dev.daymark.resources.settings
import dev.daymark.resources.theme_dark
import dev.daymark.resources.theme_light
import dev.daymark.resources.theme_system
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.str
import dev.daymark.ui.theme.DaymarkTheme
import dev.daymark.ui.theme.LocalConfigurationHealth

private data class SettingsActions(
    val setDisplayName: (String) -> Unit,
    val setLanguage: (LanguagePreference) -> Unit,
    val setTheme: (ThemePreference) -> Unit,
    val setAutomation: (Boolean) -> Unit,
    val setRemindersEnabled: (Boolean) -> Unit,
    val requestReminders: () -> Unit,
    val openSystemSettings: () -> Unit,
    val reports: ReportActions
)

@Composable
fun SettingsScreen(model: SettingsViewModel, platform: HostPlatform) {
    val state by model.uiState.collectAsStateWithLifecycle()
    LifecycleStartEffect(model) { model.refreshPermissions(); onStopOrDispose {} }
    val actions = remember(model) {
        SettingsActions(
            model::setDisplayName,
            model::setLanguage,
            model::setTheme,
            model::setAutomation,
            model::setRemindersEnabled,
            model::requestReminders,
            model::openSystemSettings,
            ReportActions(
                model::connectReport,
                model::reconnectReport,
                model::selectReport,
                model::rebuildReport,
                model::createReplacementReport,
                model::disconnectReport,
                model::retryReport,
                model::deleteLocalData
            )
        )
    }
    SettingsContent(state, platform, actions)
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    platform: HostPlatform,
    actions: SettingsActions
) {
    val language = LocalAppLanguage.current
    val health = LocalConfigurationHealth.current
    var name by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
            .testTag("settings-root"), verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(str(Res.string.settings), style = MaterialTheme.typography.headlineLarge)
        if (!state.ready) CircularProgressIndicator()
        if (state.storageError) Text(
            str(Res.string.local_save_failed),
            color = MaterialTheme.colorScheme.error
        )
        OutlinedTextField(
            value = name ?: state.settings.displayName,
            onValueChange = { name = it },
            enabled = !state.busy,
            label = { Text(str(Res.string.profile_name)) },
            modifier = Modifier.fillMaxWidth().testTag("settings-name")
        )
        if (dev.daymark.reporting.exceedsReportTextLimit(name ?: state.settings.displayName)) Text(
            str(
                Res.string.profile_too_long,
                DisplayFormats.number(REPORT_TEXT_LIMIT, language)
            ),
            color = MaterialTheme.colorScheme.error
        )
        if (state.invalidName) Text(
            str(Res.string.invalid_name),
            color = MaterialTheme.colorScheme.error
        )
        TextButton(
            enabled = !state.busy,
            onClick = {
                actions.setDisplayName(
                    name ?: state.settings.displayName
                )
            }) { Text(str(Res.string.save)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                LanguagePreference.SYSTEM to Res.string.language_system,
                LanguagePreference.EN to Res.string.language_english,
                LanguagePreference.AR to Res.string.language_arabic
            ).forEach { (choice, label) ->
                FilterChip(
                    selected = state.settings.language == choice,
                    enabled = !state.busy,
                    onClick = { actions.setLanguage(choice) },
                    label = { Text(str(label)) },
                    modifier = Modifier.testTag("settings-language-${choice.name.lowercase()}")
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                ThemePreference.SYSTEM to Res.string.theme_system,
                ThemePreference.LIGHT to Res.string.theme_light,
                ThemePreference.DARK to Res.string.theme_dark
            ).forEach { (choice, label) ->
                FilterChip(
                    selected = state.settings.theme == choice,
                    enabled = !state.busy,
                    onClick = { actions.setTheme(choice) },
                    label = { Text(str(label)) },
                    modifier = Modifier.testTag("settings-theme-${choice.name.lowercase()}")
                )
            }
        }
        Text(
            str(
                Res.string.location_health,
                str(if (state.capabilities?.let { it.foregroundGranted && it.preciseGranted && it.servicesEnabled } == true) Res.string.enabled else Res.string.disabled)))
        TextButton(onClick = actions.openSystemSettings) { Text(str(Res.string.open_settings)) }
        if (platform == HostPlatform.ANDROID) {
            Text(str(Res.string.automation), style = MaterialTheme.typography.titleLarge)
            Text(str(Res.string.automation_help))
            Text(str(if (state.automaticEnabled) Res.string.automation_on else Res.string.automation_off))
            Button(
                enabled = !state.busy,
                onClick = { actions.setAutomation(!state.automaticEnabled) },
                modifier = Modifier.testTag("settings-automation")
            ) {
                Text(str(if (state.automaticEnabled) Res.string.disable_automation else Res.string.enable_automation))
            }
            if (state.permissionDenied) Text(
                str(Res.string.automation_denied),
                color = MaterialTheme.colorScheme.error
            )
        } else if (platform == HostPlatform.IOS) {
            Text(str(Res.string.ios_manual_help))
            Text(str(Res.string.reminder_horizon))
            if (!state.settings.remindersEnabled) Text(str(Res.string.reminders_off))
            Button(enabled = !state.busy && !state.requestingReminderPermission, onClick = {
                if (state.settings.remindersEnabled) actions.setRemindersEnabled(false)
                else actions.requestReminders()
            }) { Text(str(if (state.settings.remindersEnabled) Res.string.disable_reminders else Res.string.reminders_optional)) }
            if (state.reminderPermissionGranted == false) Text(str(Res.string.reminders_denied))
            health.reminderThrough?.let {
                Text(
                    str(
                        Res.string.horizon_through,
                        DisplayFormats.date(it, language)
                    )
                )
            }
        }
        if (health.needsAttention) Text(
            str(Res.string.effects_failed),
            color = MaterialTheme.colorScheme.error
        )
        WidgetSetupGuidance()
        GoogleConnectionSection(state, actions.reports)
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    val actions = SettingsActions(
        { _ -> },
        { _ -> },
        { _ -> },
        { _ -> },
        { _ -> },
        {},
        {},
        ReportActions({}, {}, {}, {}, {}, {}, {}, {})
    )
    DaymarkTheme(ThemePreference.LIGHT) {
        SettingsContent(
            SettingsUiState(
                ready = true,
                settings = AppSettings(displayName = "Daymark")
            ), HostPlatform.ANDROID, actions
        )
    }
}
