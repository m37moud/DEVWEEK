package dev.daymark.presentation.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.daymark.location.LocationPermissionPort
import dev.daymark.logging.AppLogger
import dev.daymark.models.DeletionResult
import dev.daymark.models.reporting.ConnectionResult
import dev.daymark.reminders.ReminderPort
import dev.daymark.repository.Repository
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.ThemePreference
import dev.daymark.widgets.UnsupportedWidgetSetupPort
import dev.daymark.widgets.WidgetSetupPort
import dev.daymark.widgets.WidgetSetupStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class SettingsViewModel(
    private val repository: Repository,
    private val permissions: LocationPermissionPort,
    private val reminders: ReminderPort,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val widgetSetup: WidgetSetupPort = UnsupportedWidgetSetupPort
) : ViewModel(scope) {
    private val state = MutableStateFlow(SettingsUiState())
    val uiState = state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.reporting.health,
                repository.dataLifecycle.deleting
            ) { report, deleting ->
                state.update { it.copy(report = report, deleting = deleting) }
            }.collect()
        }

        viewModelScope.launch {
            combine(
                repository.settings.observe(),
                repository.settings.observeTracker()
            ) { preferences, tracker ->
                state.update {
                    it.copy(
                        settings = preferences,
                        automaticEnabled = tracker?.automaticEnabled == true,
                        capabilities = permissions.current(),
                        ready = true
                    )
                }
            }.catch { error ->
                if (error is CancellationException) throw error
                state.update { it.copy(ready = true, storageError = true) }
            }.collect()
        }
    }

    private var widgetQueryGeneration = 0

    fun refreshWidgetSetup() {
        val generation = ++widgetQueryGeneration
        state.update { it.copy(widgetCanPin = widgetSetup.canRequestPin) }
        widgetSetup.query { status ->
            viewModelScope.launch {
                if (generation == widgetQueryGeneration) AppLogger.d(
                    "SettingsViewModel",
                    "Widget setup refreshed: ${status.name}"
                )
                if (generation == widgetQueryGeneration) state.update {
                    it.copy(
                        widgetStatus = status,
                        widgetPinPending = false
                    )
                }
            }
        }
    }

    fun addWidget() {
        if (state.value.widgetPinPending || state.value.widgetStatus == WidgetSetupStatus.ADDED) return
        val accepted = widgetSetup.requestPin(::refreshWidgetSetup)
        state.update {
            it.copy(
                widgetPinPending = accepted,
                widgetStatus = if (accepted) it.widgetStatus else WidgetSetupStatus.UNKNOWN
            )
        }
        AppLogger.i(
            "SettingsViewModel",
            if (accepted) "Widget pin request accepted" else "Widget pin request unavailable"
        )
    }

    override fun onCleared() {
        widgetQueryGeneration++
        widgetSetup.close()
        super.onCleared()
    }

    fun refreshPermissions() {
        state.update { it.copy(capabilities = permissions.current()) }
    }

    fun openSystemSettings() = permissions.openSystemSettings()
    fun setLanguage(language: LanguagePreference) =
        save { repository.settings.update(language = language) }

    fun setTheme(theme: ThemePreference) = save { repository.settings.update(theme = theme) }
    fun setDisplayName(name: String) {
        if (name.isBlank() || dev.daymark.reporting.exceedsReportTextLimit(name)) {
            state.update { it.copy(invalidName = true) }; return
        }
        save { repository.settings.update(displayName = name); state.update { it.copy(invalidName = false) } }
    }

    fun requestReminders() {
        if (state.value.busy || state.value.requestingReminderPermission) return
        state.update { it.copy(requestingReminderPermission = true) }
        viewModelScope.launch {
            try {
                val allowed = suspendCancellableCoroutine { continuation ->
                    reminders.requestPermission { granted ->
                        if (continuation.isActive) continuation.resume(granted)
                    }
                }
                state.update { it.copy(reminderPermissionGranted = allowed) }
                if (allowed) setRemindersEnabled(true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                AppLogger.e("SettingsViewModel", "Reminder permission request failed", failure)
                state.update { it.copy(storageError = true) }
            } finally {
                state.update { it.copy(requestingReminderPermission = false) }
            }
        }
    }

    fun setRemindersEnabled(enabled: Boolean) =
        save { repository.settings.update(remindersEnabled = enabled) }

    fun setAutomation(enabled: Boolean) = save {
        val expectedTracker = repository.settings.trackerIdentity() ?: return@save
        if (enabled) {
            var capability = permissions.current()
            if (!capability.foregroundGranted || !capability.preciseGranted) capability =
                permissions.requestForeground()
            if (capability.foregroundGranted && capability.preciseGranted && !capability.backgroundGranted) capability =
                permissions.requestBackground()
            if (!capability.foregroundGranted || !capability.preciseGranted || !capability.backgroundGranted || !capability.servicesEnabled) {
                state.update {
                    it.copy(
                        capabilities = capability,
                        permissionDenied = true
                    )
                }; return@save
            }
        }
        repository.settings.setAutomation(expectedTracker, enabled)
        state.update { it.copy(permissionDenied = false, capabilities = permissions.current()) }
    }

    fun connectReport() = reportAction { repository.reporting.connect() }
    fun reconnectReport() = reportAction { repository.reporting.reconnect() }
    fun selectReport(reportId: String) =
        reportAction { repository.reporting.selectReport(reportId) }

    fun rebuildReport() = reportAction { repository.reporting.rebuildReport() }
    fun createReplacementReport() = reportAction { repository.reporting.createReplacementReport() }
    fun disconnectReport() = reportAction { repository.reporting.disconnect(); null }
    fun retryReport() = repository.reporting.retryReport()

    fun deleteLocalData() {
        if (state.value.reportBusy || state.value.deleting) return
        viewModelScope.launch {
            val failed = repository.dataLifecycle.deleteLocalData() == DeletionResult.Failed
            state.update { it.copy(deletionFailed = failed) }
        }
    }

    private fun reportAction(action: suspend () -> ConnectionResult?) {
        if (state.value.reportBusy || state.value.deleting) return
        state.update { it.copy(reportBusy = true, reportActionFailed = false) }
        viewModelScope.launch {
            try {
                repository.reporting.refreshConnection(action())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                AppLogger.e("SettingsViewModel", "Report action failed", failure)
                state.update { it.copy(reportActionFailed = true) }
            } finally {
                state.update { it.copy(reportBusy = false) }
            }
        }
    }

    private fun save(action: suspend () -> Unit) {
        if (state.value.busy) return
        state.update { it.copy(busy = true, storageError = false) }
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.update { it.copy(storageError = true) }
            } finally {
                state.update { it.copy(busy = false) }
            }
        }
    }
}
