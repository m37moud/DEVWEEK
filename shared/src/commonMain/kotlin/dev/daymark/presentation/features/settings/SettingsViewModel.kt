package dev.daymark.presentation.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.daymark.history.AttendancePeriodSettings
import dev.daymark.location.LocationPermissionPort
import dev.daymark.logging.AppLogger
import dev.daymark.models.DeletionResult
import dev.daymark.models.plan.PlanMutationResult
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dev.daymark.location.canRunAutomaticCheck
import dev.daymark.location.requestAutomation
import dev.daymark.notifications.ResultNotificationChannel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class SettingsViewModel(
    private val repository: Repository,
    private val permissions: LocationPermissionPort,
    private val reminders: ReminderPort,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val widgetSetup: WidgetSetupPort = UnsupportedWidgetSetupPort,
    private val resultNotifications: dev.daymark.notifications.AttendanceResultNotifier? = null
) : ViewModel(scope) {
    private val TAG = SettingsViewModel::class.simpleName
    private val state =
        MutableStateFlow(SettingsUiState(resultNotificationsSupported = resultNotifications?.supported == true))
    val uiState = state.asStateFlow()
    internal val attendanceDate = repository.dashboard.observe()
        .map { dashboard -> dashboard.date }
        .distinctUntilChanged()

    init {
        observeAnnualVacationSettings()
        resultNotifications?.androidControls?.let { controls ->
            viewModelScope.launch {
                controls.status.collect { status -> state.update { it.copy(androidResultStatus = status) } }
            }
        }
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

    private fun observeAnnualVacationSettings() {
        viewModelScope.launch {
            flow { emitAll(repository.timeOff.observeAnnualVacationSettings()) }
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    AppLogger.e(
                        "SettingsViewModel",
                        "Annual vacation policy observation failed",
                        failure
                    )
                    updateAnnualVacationState { it.copy(loadFailed = true) }
                }.collect { annualVacation ->
                    updateAnnualVacationState {
                        if (annualVacation == null) AnnualVacationSettingsUiState()
                        else it.copy(configuration = annualVacation)
                    }
                }
        }
    }

    private fun updateAnnualVacationState(
        change: (AnnualVacationSettingsUiState) -> AnnualVacationSettingsUiState
    ) {
        state.update { it.copy(annualVacation = change(it.annualVacation)) }
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

    fun previewPeriod(settings: AttendancePeriodSettings) {
        if (state.value.deleting) return
        save {
            AppLogger.d("SettingsViewModel", "Reviewing attendance period change")
            val review = repository.timeOff.previewAttendancePeriod(settings)
            state.update {
                it.copy(
                    periodReview = review,
                    periodSaveResult = if (review == null) PlanMutationResult.LocalSaveFailed else null
                )
            }
        }
    }

    fun dismissPeriodReview() {
        if (!state.value.busy) state.update { it.copy(periodReview = null) }
    }

    fun confirmPeriodChange() {
        val review = state.value.periodReview ?: return
        if (state.value.deleting || review.rejection != null) return
        save {
            val mutation = repository.timeOff.saveAttendancePeriod(review)
            state.update { it.copy(periodReview = null, periodSaveResult = mutation) }
            AppLogger.i(
                "SettingsViewModel",
                if (mutation == PlanMutationResult.Applied) "Attendance period saved locally"
                else "Attendance period change rejected"
            )
        }
    }

    fun saveAnnualVacationPolicy(
        annualAllowanceDays: Int,
        carryOverUnusedDays: Boolean,
        expectedEffectiveYear: Int
    ) {
        if (state.value.deleting) return
        save {
            updateAnnualVacationState { it.copy(saveResult = null) }
            AppLogger.d("SettingsViewModel", "Saving annual vacation policy")
            val mutation = repository.timeOff.updateAnnualVacationPolicy(
                annualAllowanceDays,
                carryOverUnusedDays,
                expectedEffectiveYear
            )
            updateAnnualVacationState { it.copy(saveResult = mutation) }
            AppLogger.i(
                "SettingsViewModel",
                if (mutation == PlanMutationResult.Applied) "Annual vacation policy saved"
                else "Annual vacation policy rejected"
            )
        }
    }

    fun setDisplayName(name: String) {
        if (name.isBlank() || dev.daymark.reporting.exceedsReportTextLimit(name)) {
            state.update { it.copy(invalidName = true) }; return
        }
        save { repository.settings.update(displayName = name); state.update { it.copy(invalidName = false) } }
    }

    fun requestReminders() {
        if (state.value.busy || state.value.requestingReminderPermission) return
        state.update { it.copy(requestingReminderPermission = true, storageError = false) }
        viewModelScope.launch {
            try {
                val allowed = requestPermission {
                    suspendCancellableCoroutine<Boolean> { continuation ->
                        reminders.requestPermission { granted ->
                            if (continuation.isActive) continuation.resume(granted)
                        }
                    }
                } ?: return@launch
                state.update { it.copy(reminderPermissionGranted = allowed) }
                if (allowed) setRemindersEnabled(true)
            } finally {
                state.update { it.copy(requestingReminderPermission = false) }
            }
        }
    }

    fun setResultNotificationsEnabled(enabled: Boolean) = save {
        val notifications = resultNotifications ?: return@save
        if (enabled) {
            val allowed = requestPermission(notifications::awaitPermission) ?: return@save
            state.update { it.copy(resultNotificationPermissionDenied = !allowed) }
            if (!allowed && notifications.androidControls == null) return@save
        }
        notifications.setEnabled(enabled)
        AppLogger.i("SettingsViewModel", "Result notification preference saved")
    }

    fun setResultFailureSound(enabled: Boolean) = save {
        repository.settings.update(resultFailureSoundEnabled = enabled)
    }

    fun enableResultFailureChannel() = save {
        resultNotifications?.androidControls?.enableFailureChannel()
    }

    fun openResultNotificationSettings(channel: ResultNotificationChannel?) {
        val controls = resultNotifications?.androidControls ?: return
        if (channel == null) controls.openAppSettings() else controls.openChannelSettings(channel = channel)
    }

    fun setRemindersEnabled(enabled: Boolean) =
        save { repository.settings.update(remindersEnabled = enabled) }

    fun setAutomaticAttemptLimit(limit: Int) = save {
        repository.settings.update(automaticAttemptLimit = limit)
        AppLogger.i("SettingsViewModel", "Automatic attempt limit saved")
    }

    fun setAutomation(enabled: Boolean) = save {
        val expectedTracker = repository.settings.trackerIdentity() ?: return@save
        AppLogger.d(TAG ?: "SettingsViewModel", "Setting automation to $enabled")
        if (enabled) {
            val capability = requestPermission(permissions::requestAutomation) ?: return@save
            state.update { it.copy(capabilities = capability) }
            if (!capability.canRunAutomaticCheck) {
                state.update {
                    it.copy(
                        capabilities = capability,
                        permissionDenied = true
                    )
                }; return@save
            }
        }
        repository.settings.setAutomation(expectedTrackerId = expectedTracker, enabled = enabled)
        state.update { it.copy(permissionDenied = false) }
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

    // A failed native request has no reply; callers must skip the dependent save.
    private suspend fun <T : Any> requestPermission(action: suspend () -> T): T? {
        state.update { it.copy(permissionRequestFailed = false) }
        return try {
            action()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            AppLogger.e("SettingsViewModel", "Permission request failed", failure)
            state.update { it.copy(permissionRequestFailed = true) }
            null
        }
    }

    private fun save(action: suspend () -> Unit) {
        if (state.value.busy) return
        state.update { it.copy(busy = true, storageError = false, permissionRequestFailed = false) }
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
