package dev.daymark.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.daymark.attendance.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.*
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.milliseconds

data class HistoryUiState(
    val year: Int, val month: Int, val history: HistoryMonth = HistoryMonth(emptyList(), 0, 0, 0),
    val ready: Boolean = false, val busy: Boolean = false, val savedKey: DayKey? = null,
    val today: LocalDate = LocalDate(year,month,1),val invalidCorrection: Boolean = false,val noteTooLong: Boolean = false, val storageError: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val repository: AttendanceRepository, initialDate: LocalDate,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),private val today: suspend () -> LocalDate = { initialDate },private val clock: Clock? = null,
    private val refreshBoundary: suspend () -> Instant? = { null }) : ViewModel(scope) {
    private val active = MutableStateFlow(true)
    private val refresh = MutableStateFlow(0)
    private val selected = MutableStateFlow(initialDate.year to initialDate.month.number)
    private val state = MutableStateFlow(HistoryUiState(initialDate.year, initialDate.month.number))
    val uiState = state.asStateFlow()
    init {
        viewModelScope.launch {
            combine(selected, active,refresh) { month, enabled,_ -> month to enabled }.flatMapLatest { (monthKey, enabled) ->
                val (year, month) = monthKey
                if (!enabled) return@flatMapLatest emptyFlow()
                val currentClock=clock
                val histories=if (currentClock==null) repository.observeMonth(year,month) else channelFlow {
                    repository.reconcile(currentClock.now())
                    refreshBoundary()?.let { boundary -> launch {
                        delay((boundary-currentClock.now()).coerceAtLeast(1.milliseconds))
                        refresh.update { it+1 }
                    } }
                    repository.observeMonth(year,month).collect { send(it) }
                }
                histories.onEach { history -> val currentDate=today(); state.update { it.copy(year = year, month = month,
                    history = history, ready = true, storageError = false,today=currentDate) } }
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
        if (dev.daymark.reporting.exceedsReportTextLimit(command.note)) {
            state.update { it.copy(noteTooLong=true,invalidCorrection=true) }; return
        }
        val invalid = command.status !in listOf(AttendanceStatus.PRESENT, AttendanceStatus.LATE, AttendanceStatus.ABSENT) ||
            (command.status != AttendanceStatus.ABSENT && command.arrivalTime == null)
        if (invalid) { state.update { it.copy(invalidCorrection = true) }; return }
        state.update { it.copy(busy = true, savedKey = null, invalidCorrection = false,noteTooLong=false, storageError = false) }
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
