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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.daymark.app.HostPlatform
import dev.daymark.history.AttendancePeriodSettings
import dev.daymark.history.VacationYearBasis
import dev.daymark.presentation.component.AttendancePeriodEditor
import dev.daymark.presentation.component.AttendancePeriodEditorState
import dev.daymark.presentation.component.PlanMutationFeedback
import dev.daymark.presentation.features.settings.component.AttendancePeriodReviewDialog
import dev.daymark.presentation.features.settings.component.AttendanceSettingsSection
import dev.daymark.presentation.features.settings.component.GoogleConnectionSection
import dev.daymark.presentation.features.settings.component.ReportActions
import dev.daymark.presentation.features.settings.component.SettingsAnnualVacationCard
import dev.daymark.presentation.features.settings.component.SettingsPreferencesCard
import dev.daymark.presentation.features.settings.component.SettingsProfileCard
import dev.daymark.presentation.features.settings.component.SettingsTrackingCard
import dev.daymark.presentation.features.settings.component.SettingsWidgetsCard
import dev.daymark.presentation.features.settings.component.WidgetSetupActions
import dev.daymark.resources.Res
import dev.daymark.resources.attendance_period
import dev.daymark.resources.plan_review
import dev.daymark.resources.local_save_failed
import dev.daymark.resources.permission_request_failed
import dev.daymark.resources.result_notifications
import dev.daymark.resources.result_notifications_denied
import dev.daymark.resources.result_notifications_help
import dev.daymark.resources.settings
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.str
import dev.daymark.ui.theme.LocalConfigurationHealth
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
    model: SettingsViewModel = koinViewModel(),
    attendance: AttendanceSettingsViewModel = koinViewModel()
) {
    val state by model.uiState.collectAsStateWithLifecycle()
    LifecycleStartEffect(model) {
        model.refreshPermissions()
        model.refreshWidgetSetup()
        onStopOrDispose {}
    }
    val actions = remember(model) {
        SettingsActions(
            addWidget = model::addWidget,
            refreshWidget = model::refreshWidgetSetup,
            setDisplayName = model::setDisplayName,
            setLanguage = model::setLanguage,
            setTheme = model::setTheme,
            saveAnnualVacationPolicy = model::saveAnnualVacationPolicy,
            setAutomation = model::setAutomation,
            setRemindersEnabled = model::setRemindersEnabled,
            requestReminders = model::requestReminders,
            setResultNotificationsEnabled = model::setResultNotificationsEnabled,
            openSystemSettings = model::openSystemSettings,
            previewPeriod = model::previewPeriod,
            confirmPeriodChange = model::confirmPeriodChange,
            dismissPeriodReview = model::dismissPeriodReview,
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
    SettingsContent(state = state, platform = platform, actions = actions) { AttendanceSettingsSection(attendance) }
}

@Composable
fun SettingsContent(
    state: SettingsUiState,
    platform: HostPlatform,
    actions: SettingsActions,
    attendanceContent: @Composable () -> Unit = {}
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

                    attendanceContent()

                    AttendancePeriodSection(state, actions)

                    SettingsAnnualVacationCard(
                        state = state.annualVacation,
                        busy = state.busy || state.deleting,
                        onSave = actions.saveAnnualVacationPolicy
                    )

                    if (state.resultNotificationsSupported) {
                        val notificationLabel = str(Res.string.result_notifications)
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        notificationLabel, Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Switch(
                                        checked = state.settings.resultNotificationsEnabled,
                                        enabled = !state.busy,
                                        onCheckedChange = actions.setResultNotificationsEnabled,
                                        modifier = Modifier.testTag("settings-result-notifications")
                                            .semantics { contentDescription = notificationLabel })
                                }
                                Text(
                                    str(Res.string.result_notifications_help),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (state.resultNotificationPermissionDenied) {
                                    Text(
                                        str(Res.string.result_notifications_denied),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
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


@Composable
private fun AttendancePeriodSection(state: SettingsUiState, actions: SettingsActions) {
    val saved = state.settings.attendancePeriod
    val enabled = !state.busy && !state.deleting
    var closingDay by rememberSaveable(saved.closingDay) { mutableStateOf(saved.closingDay) }
    var periodYear by rememberSaveable(saved.vacationYearBasis) {
        mutableStateOf(saved.vacationYearBasis == VacationYearBasis.ATTENDANCE_PERIOD)
    }
    val draft = AttendancePeriodSettings(
        closingDay,
        if (periodYear) VacationYearBasis.ATTENDANCE_PERIOD else VacationYearBasis.CALENDAR
    )

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(str(Res.string.attendance_period), style = MaterialTheme.typography.titleMedium)
            AttendancePeriodEditor(
                state = AttendancePeriodEditorState(draft, enabled, state.annualVacation.configuration != null),
                onChange = { updated ->
                    closingDay = updated.closingDay
                    periodYear = updated.vacationYearBasis == VacationYearBasis.ATTENDANCE_PERIOD
                }
            )
            PlanMutationFeedback(state.periodSaveResult)
            Button(
                onClick = { actions.previewPeriod(draft) },
                enabled = enabled && draft != saved,
                modifier = Modifier.testTag("attendance-period-review")
            ) { Text(str(Res.string.plan_review)) }
        }
    }
    state.periodReview?.let { AttendancePeriodReviewDialog(it, enabled, actions) }
}
