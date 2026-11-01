package dev.daymark.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room3.withWriteTransaction
import dev.daymark.data.*
import dev.daymark.location.*
import dev.daymark.settings.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class SettingsUiState(val settings: AppSettings = AppSettings(), val ready: Boolean = false,
    val busy: Boolean = false, val automaticEnabled: Boolean = false, val permissionDenied: Boolean = false,
    val capabilities: LocationCapabilities? = null, val invalidName: Boolean = false, val storageError: Boolean = false)
class SettingsViewModel(private val db: DaymarkDatabase, private val settings: SettingsRepository,
    private val permissions: LocationPermissionPort,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)) : ViewModel(scope) {
    private val state = MutableStateFlow(SettingsUiState())
    val uiState = state.asStateFlow()
    init {
        viewModelScope.launch {
            combine(settings.observe(),db.dao().observeTracker()) { preferences, tracker ->
                state.update { it.copy(settings = preferences, automaticEnabled = tracker?.automaticEnabled == true,
                    capabilities = permissions.current(), ready = true) }
            }.catch { error ->
                if (error is CancellationException) throw error
                state.update { it.copy(ready = true,storageError = true) }
            }.collect()
        }
    }
    fun refreshPermissions() { state.update { it.copy(capabilities = permissions.current()) } }
    fun openSystemSettings() = permissions.openSystemSettings()
    fun setLanguage(language: LanguagePreference) = save { settings.update(language = language) }
    fun setTheme(theme: ThemePreference) = save { settings.update(theme = theme) }
    fun setDisplayName(name: String) {
        if (name.isBlank()) { state.update { it.copy(invalidName = true) }; return }
        save { settings.update(displayName = name); state.update { it.copy(invalidName = false) } }
    }
    fun setRemindersEnabled(enabled: Boolean) = save { settings.update(remindersEnabled = enabled) }
    fun setAutomation(enabled: Boolean) = save {
        val expectedTracker = db.dao().tracker()?.id ?: return@save
        if (enabled) {
            var capability = permissions.current()
            if (!capability.foregroundGranted || !capability.preciseGranted) capability = permissions.requestForeground()
            if (capability.foregroundGranted && capability.preciseGranted && !capability.backgroundGranted) capability = permissions.requestBackground()
            if (!capability.foregroundGranted || !capability.preciseGranted || !capability.backgroundGranted || !capability.servicesEnabled) {
                state.update { it.copy(capabilities = capability,permissionDenied = true) }; return@save
            }
        }
        db.withWriteTransaction {
            val tracker = db.dao().tracker()?.takeIf { it.id == expectedTracker } ?: return@withWriteTransaction
            if (tracker.automaticEnabled != enabled) {
                db.dao().putTracker(tracker.copy(automaticEnabled = enabled,changeRevision = tracker.changeRevision + 1))
                db.dao().markDirty(tracker.id,"OVERVIEW")
            }
        }
        state.update { it.copy(permissionDenied = false,capabilities = permissions.current()) }
    }
    private fun save(action: suspend () -> Unit) {
        if (state.value.busy) return
        state.update { it.copy(busy = true,storageError = false) }
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.update { it.copy(storageError = true) } }
            finally { state.update { it.copy(busy = false) } }
        }
    }
}
