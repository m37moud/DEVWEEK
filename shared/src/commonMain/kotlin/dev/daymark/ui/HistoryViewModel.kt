package dev.daymark.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.daymark.attendance.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.*

data class HistoryUiState(
    val year: Int, val month: Int, val history: HistoryMonth = HistoryMonth(emptyList(), 0, 0, 0),
    val ready: Boolean = false, val busy: Boolean = false, val savedKey: DayKey? = null,
    val invalidCorrection: Boolean = false, val storageError: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val repository: AttendanceRepository, initialDate: LocalDate,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)) : ViewModel(scope) {
    private val active = MutableStateFlow(true)
    private val selected = MutableStateFlow(initialDate.year to initialDate.month.number)
    private val state = MutableStateFlow(HistoryUiState(initialDate.year, initialDate.month.number))
    val uiState = state.asStateFlow()
    init {
        viewModelScope.launch {
            combine(selected, active) { month, enabled -> month to enabled }.flatMapLatest { (monthKey, enabled) ->
                val (year, month) = monthKey
                if (!enabled) return@flatMapLatest emptyFlow()
                repository.observeMonth(year, month).onEach { history -> state.update { it.copy(year = year, month = month,
                    history = history, ready = true, storageError = false) } }
                    .catch { error ->
                        if (error is CancellationException) throw error
                        state.update { it.copy(ready = true, storageError = true) }
                    }
            }.collect()
        }
    }
    fun setActive(value: Boolean) { active.value = value }

    fun selectMonth(year: Int, month: Int) {
        LocalDate(year, month, 1)
        if (selected.value == year to month) return
        state.update { it.copy(year = year, month = month, ready = false, savedKey = null) }
        selected.value = year to month
    }
    fun saveCorrection(command: Correction) {
        if (state.value.busy) return
        val invalid = command.status !in listOf(AttendanceStatus.PRESENT, AttendanceStatus.LATE, AttendanceStatus.ABSENT) ||
            (command.status != AttendanceStatus.ABSENT && command.arrivalTime == null)
        if (invalid) { state.update { it.copy(invalidCorrection = true) }; return }
        state.update { it.copy(busy = true, savedKey = null, invalidCorrection = false, storageError = false) }
        viewModelScope.launch {
            try {
                repository.correct(command)
                state.update { it.copy(savedKey = command.key) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: IllegalArgumentException) { state.update { it.copy(invalidCorrection = true) } }
            catch (_: Exception) { state.update { it.copy(storageError = true) } }
            finally { state.update { it.copy(busy = false) } }
        }
    }
}
