package dev.daymark.presentation.features.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.models.attendance.Correction
import dev.daymark.repository.Repository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val repository: Repository,
    initialDate: LocalDate,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val today: suspend () -> LocalDate = { initialDate },
    private val clock: Clock? = null,
    private val refreshBoundary: suspend () -> Instant? = { null }
) : ViewModel(scope) {
    private val active = MutableStateFlow(true)
    private val refresh = MutableStateFlow(0)
    private val selected = MutableStateFlow(initialDate.year to initialDate.month.number)
    private val state = MutableStateFlow(HistoryUiState(initialDate.year, initialDate.month.number))
    val uiState = state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                selected,
                active,
                refresh
            ) { month, enabled, _ -> month to enabled }.flatMapLatest { (monthKey, enabled) ->
                val (year, month) = monthKey
                if (!enabled) return@flatMapLatest emptyFlow()
                val currentClock = clock
                val histories = if (currentClock == null) repository.attendance.observeMonth(
                    year,
                    month
                ) else channelFlow {
                    repository.attendance.reconcile(currentClock.now())
                    refreshBoundary()?.let { boundary ->
                        launch {
                            delay((boundary - currentClock.now()).coerceAtLeast(1.milliseconds))
                            refresh.update { it + 1 }
                        }
                    }
                    repository.attendance.observeMonth(year, month).collect { send(it) }
                }
                histories.onEach { history ->
                    val currentDate = today(); state.update {
                    it.copy(
                        year = year, month = month,
                        history = history, ready = true, storageError = false, today = currentDate
                    )
                }
                }
                    .catch { error ->
                        if (error is CancellationException) throw error
                        state.update { it.copy(ready = true, storageError = true) }
                    }
            }.collect()
        }
    }

    fun setActive(value: Boolean) {
        active.value = value
    }

    fun selectMonth(year: Int, month: Int) {
        LocalDate(year, month, 1)
        if (selected.value == year to month) return
        state.update { it.copy(year = year, month = month, ready = false, savedKey = null) }
        selected.value = year to month
    }

    fun saveCorrection(command: Correction) {
        if (state.value.busy) return
        if (dev.daymark.reporting.exceedsReportTextLimit(command.note)) {
            state.update { it.copy(noteTooLong = true, invalidCorrection = true) }; return
        }
        val invalid = command.status !in listOf(
            AttendanceStatus.PRESENT,
            AttendanceStatus.LATE,
            AttendanceStatus.ABSENT
        ) ||
                (command.status != AttendanceStatus.ABSENT && command.arrivalTime == null)
        if (invalid) {
            state.update { it.copy(invalidCorrection = true) }; return
        }
        state.update {
            it.copy(
                busy = true,
                savedKey = null,
                invalidCorrection = false,
                noteTooLong = false,
                storageError = false
            )
        }
        viewModelScope.launch {
            try {
                repository.attendance.correct(command)
                state.update { it.copy(savedKey = command.key) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IllegalArgumentException) {
                state.update { it.copy(invalidCorrection = true) }
            } catch (_: Exception) {
                state.update { it.copy(storageError = true) }
            } finally {
                state.update { it.copy(busy = false) }
            }
        }
    }
}
