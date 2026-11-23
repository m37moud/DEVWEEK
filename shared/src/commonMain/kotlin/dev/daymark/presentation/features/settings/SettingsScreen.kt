package dev.daymark.presentation.features.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.daymark.app.HostPlatform
import dev.daymark.history.AttendancePeriodSettings
import dev.daymark.presentation.features.settings.component.AttendanceSettingsSection
import dev.daymark.presentation.features.settings.component.GoogleConnectionSection
import dev.daymark.presentation.features.settings.component.ReportActions
import dev.daymark.presentation.features.settings.component.SettingsAnnualVacationCard
import dev.daymark.presentation.features.settings.component.SettingsAttendancePeriodCard
import dev.daymark.presentation.features.settings.component.SettingsPreferencesSection
import dev.daymark.presentation.features.settings.component.SettingsProfileCard
import dev.daymark.presentation.features.settings.component.SettingsResultNotificationsCard
import dev.daymark.presentation.features.settings.component.SettingsTrackingCard
import dev.daymark.presentation.features.settings.component.SettingsWidgetsCard
import dev.daymark.presentation.features.settings.component.WidgetSetupActions
import dev.daymark.resources.Res
import dev.daymark.resources.local_save_failed
import dev.daymark.resources.permission_request_failed
import dev.daymark.resources.settings
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.str
import org.koin.compose.viewmodel.koinViewModel

data class SettingsActions(
    val addWidget: () -> Unit,
    val refreshWidget: () -> Unit,
    val setDisplayName: (String) -> Unit,
    val setLanguage: (LanguagePreference) -> Unit,
    val setTheme: (ThemePreference) -> Unit,
    val saveAnnualVacationPolicy: (Int, Boolean, Int) -> Unit,
    val setAutomation: (Boolean) -> Unit,
    val setRemindersEnabled: (Boolean) -> Unit,
    val requestReminders: () -> Unit,
    val setResultNotificationsEnabled: (Boolean) -> Unit,
    val openSystemSettings: () -> Unit,
    val reports: ReportActions,
    val previewPeriod: (AttendancePeriodSettings) -> Unit,
    val confirmPeriodChange: () -> Unit,
    val dismissPeriodReview: () -> Unit
)

@Composable
fun SettingsScreen(
    platform: HostPlatform,
    settingsViewModel: SettingsViewModel = koinViewModel(),
) {
    val state by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val attendanceDate by settingsViewModel.attendanceDate.collectAsStateWithLifecycle(initialValue = null)
    LifecycleStartEffect(settingsViewModel) {
        settingsViewModel.refreshPermissions()
        settingsViewModel.refreshWidgetSetup()
        onStopOrDispose {}
    }
    val actions = remember(settingsViewModel) {
        SettingsActions(
            addWidget = settingsViewModel::addWidget,
            refreshWidget = settingsViewModel::refreshWidgetSetup,
            setDisplayName = settingsViewModel::setDisplayName,
            setLanguage = settingsViewModel::setLanguage,
            setTheme = settingsViewModel::setTheme,
            saveAnnualVacationPolicy = settingsViewModel::saveAnnualVacationPolicy,
            setAutomation = settingsViewModel::setAutomation,
            setRemindersEnabled = settingsViewModel::setRemindersEnabled,
            requestReminders = settingsViewModel::requestReminders,
            setResultNotificationsEnabled = settingsViewModel::setResultNotificationsEnabled,
            openSystemSettings = settingsViewModel::openSystemSettings,
            previewPeriod = settingsViewModel::previewPeriod,
            confirmPeriodChange = settingsViewModel::confirmPeriodChange,
            dismissPeriodReview = settingsViewModel::dismissPeriodReview,
            reports = ReportActions(
                connectReport = settingsViewModel::connectReport,
                reconnectReport = settingsViewModel::reconnectReport,
                selectReport = settingsViewModel::selectReport,
                rebuildReport = settingsViewModel::rebuildReport,
                createReplacementReport = settingsViewModel::createReplacementReport,
                disconnectReport = settingsViewModel::disconnectReport,
                retryReport = settingsViewModel::retryReport,
                deleteLocalData = settingsViewModel::deleteLocalData
            )
        )
    }
    SettingsContent(state = state.copy(attendanceDate = attendanceDate), platform = platform, actions = actions) {
        AttendanceSettingsSection()
    }
}

@Composable
fun SettingsContent(
    state: SettingsUiState,
    platform: HostPlatform,
    actions: SettingsActions,
    attendanceContent: @Composable () -> Unit = {}
) {
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
            visible = state.storageError || state.permissionRequestFailed,
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
                        text = str(
                            if (state.storageError) Res.string.local_save_failed
                            else Res.string.permission_request_failed
                        ),
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
                    SettingsProfileCard(state = state, onSave = actions.setDisplayName)
                    SettingsPreferencesSection(state = state, actions = actions)
                    SettingsTrackingCard(platform = platform, state = state, actions = actions)
                    attendanceContent()
                    SettingsAttendancePeriodCard(state = state, actions = actions)

                    SettingsAnnualVacationCard(
                        state = state.annualVacation,
                        busy = state.busy || state.deleting,
                        onSave = actions.saveAnnualVacationPolicy
                    )

                    if (state.resultNotificationsSupported) {
                        SettingsResultNotificationsCard(
                            state,
                            actions.setResultNotificationsEnabled
                        )
                    }

                    if (platform != HostPlatform.TEST) {
                        SettingsWidgetsCard(
                            state,
                            WidgetSetupActions(actions.addWidget, actions.refreshWidget),
                            platform
                        )
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
