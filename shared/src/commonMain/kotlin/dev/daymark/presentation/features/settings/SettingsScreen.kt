package dev.daymark.presentation.features.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.daymark.app.HostPlatform
import dev.daymark.models.settings.AppSettings
import dev.daymark.presentation.features.settings.component.GoogleConnectionSection
import dev.daymark.presentation.features.settings.component.ReportActions
import dev.daymark.presentation.features.settings.component.SettingsPreferencesCard
import dev.daymark.presentation.features.settings.component.SettingsProfileCard
import dev.daymark.presentation.features.settings.component.SettingsTrackingCard
import dev.daymark.presentation.features.settings.component.SettingsWidgetsCard
import dev.daymark.resources.Res
import dev.daymark.resources.local_save_failed
import dev.daymark.resources.settings
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
    LifecycleStartEffect(model) {
        model.refreshPermissions()
        onStopOrDispose {}
    }
    val actions = remember(model) {
        SettingsActions(
            setDisplayName = model::setDisplayName,
            setLanguage = model::setLanguage,
            setTheme = model::setTheme,
            setAutomation = model::setAutomation,
            setRemindersEnabled = model::setRemindersEnabled,
            requestReminders = model::requestReminders,
            openSystemSettings = model::openSystemSettings,
            reports = ReportActions(
                connectReport = model::connectReport,
                reconnectReport = model::reconnectReport,
                selectReport = model::selectReport,
                rebuildReport = model::rebuildReport,
                createReplacementReport = model::createReplacementReport,
                disconnectReport = model::disconnectReport,
                retryReport = model::retryReport,
                deleteLocalData = model::deleteLocalData
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
    var nameInput by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("settings-root"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = str(Res.string.settings),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        AnimatedVisibility(
            visible = state.storageError,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = str(Res.string.local_save_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Crossfade(targetState = state.ready) { ready ->
            if (!ready) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SettingsProfileCard(
                        savedName = state.settings.displayName,
                        currentInput = nameInput,
                        onNameChange = { nameInput = it },
                        onSaveName = {
                            actions.setDisplayName(nameInput ?: state.settings.displayName)
                        },
                        busy = state.busy,
                        invalidName = state.invalidName,
                        language = language
                    )

                    SettingsPreferencesCard(
                        languagePreference = state.settings.language,
                        themePreference = state.settings.theme,
                        onLanguageChange = actions.setLanguage,
                        onThemeChange = actions.setTheme,
                        busy = state.busy
                    )

                    SettingsTrackingCard(
                        platform = platform,
                        capabilities = state.capabilities,
                        automaticEnabled = state.automaticEnabled,
                        permissionDenied = state.permissionDenied,
                        busy = state.busy,
                        remindersEnabled = state.settings.remindersEnabled,
                        requestingReminderPermission = state.requestingReminderPermission,
                        reminderPermissionGranted = state.reminderPermissionGranted,
                        reminderThrough = health.reminderThrough,
                        needsAttention = health.needsAttention,
                        language = language,
                        onOpenSystemSettings = actions.openSystemSettings,
                        onToggleAutomation = actions.setAutomation,
                        onSetRemindersEnabled = actions.setRemindersEnabled,
                        onRequestReminders = actions.requestReminders
                    )

                    if (platform != HostPlatform.TEST) {
                        SettingsWidgetsCard(platform = platform)
                    }
                    GoogleConnectionSection(
                        state = state,
                        actions = actions.reports,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun SettingsScreenAndroidPreview() {
    val sampleActions = SettingsActions(
        setDisplayName = {},
        setLanguage = {},
        setTheme = {},
        setAutomation = {},
        setRemindersEnabled = {},
        requestReminders = {},
        openSystemSettings = {},
        reports = ReportActions({}, {}, {}, {}, {}, {}, {}, {})
    )
    DaymarkTheme(ThemePreference.LIGHT) {
        SettingsContent(
            state = SettingsUiState(
                ready = true,
                settings = AppSettings(displayName = "Alex Lee")
            ),
            platform = HostPlatform.ANDROID,
            actions = sampleActions
        )
    }
}

@Preview
@Composable
private fun SettingsScreenIosDarkPreview() {
    val sampleActions = SettingsActions(
        setDisplayName = {},
        setLanguage = {},
        setTheme = {},
        setAutomation = {},
        setRemindersEnabled = {},
        requestReminders = {},
        openSystemSettings = {},
        reports = ReportActions({}, {}, {}, {}, {}, {}, {}, {})
    )
    DaymarkTheme(ThemePreference.DARK) {
        SettingsContent(
            state = SettingsUiState(
                ready = true,
                settings = AppSettings(displayName = "Alex Lee", theme = ThemePreference.DARK),
                reportBusy = true
            ),
            platform = HostPlatform.IOS,
            actions = sampleActions
        )
    }
}
