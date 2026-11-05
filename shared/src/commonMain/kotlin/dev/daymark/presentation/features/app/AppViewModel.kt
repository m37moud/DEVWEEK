package dev.daymark.presentation.features.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.daymark.logging.AppLogger
import dev.daymark.models.ConfigurationHealth
import dev.daymark.models.settings.AppSettings
import dev.daymark.reminders.ReminderCopy
import dev.daymark.repository.Repository
import dev.daymark.settings.AppLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppViewModel(
    private val repository: Repository,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) : ViewModel(scope) {
    private val state = MutableStateFlow(AppUiState())
    val uiState = state.asStateFlow()
    private val events = Channel<AppUiEffect>(Channel.BUFFERED)
    val effects = events.receiveAsFlow()
    private var bootstrapJob: Job? = null
    private var active = false

    init {
        viewModelScope.launch {
            repository.dataLifecycle.deleting.collect { deleting ->
                state.update { it.copy(deleting = deleting) }
                repository.application.setActive(active && !deleting)
            }
        }
    }

    fun retryLoad() {
        if (!active) return
        state.update { it.copy(bootstrap = null, failed = false) }
        observeBootstrap()
    }

    private fun observeBootstrap() {
        bootstrapJob?.cancel()
        bootstrapJob = viewModelScope.launch {
            try {
                repository.application.observeBootstrap().collect { bootstrap ->
                    state.update { it.copy(bootstrap = bootstrap, failed = false) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                AppLogger.e("AppViewModel", "Bootstrap observation failed", failure)
                state.update { it.copy(bootstrap = null, failed = true) }
            }
        }
    }

    fun setActive(enabled: Boolean) {
        if (active == enabled) return
        active = enabled
        if (enabled) observeBootstrap() else bootstrapJob?.cancel()
        repository.application.setActive(enabled && !state.value.deleting)
    }

    suspend fun refreshConfiguration(
        settings: AppSettings,
        language: AppLanguage,
        copy: ReminderCopy
    ) {
        try {
            val health = repository.application.refresh(settings, language, copy)
            state.update { it.copy(configuration = health) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            AppLogger.e("AppViewModel", "Configuration refresh failed", failure)
            state.update { it.copy(configuration = ConfigurationHealth(scheduleOk = false)) }
        }
    }

    fun showHistory() {
        viewModelScope.launch {
            repository.application.historyDate()?.let { events.send(AppUiEffect.History(it)) }
        }
    }

    override fun onCleared() {
        repository.application.setActive(false)
    }
}
