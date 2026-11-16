package dev.daymark.presentation.features.historydetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.daymark.history.HistoryMetric
import dev.daymark.logging.AppLogger
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.models.attendance.Correction
import dev.daymark.reporting.exceedsReportTextLimit
import dev.daymark.repository.Repository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HistoryDetailViewModel(
    private val repository: Repository,
    metric: HistoryMetric,
    initialMonth: LocalDate,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) : ViewModel(scope) {
    private val first = LocalDate(initialMonth.year, initialMonth.month, 1)
    private val last = first.plus(1, DateTimeUnit.MONTH).plus(-1, DateTimeUnit.DAY)
    private val selected = MutableStateFlow(first to last)
    private val active = MutableStateFlow(true)
    private val state = MutableStateFlow(HistoryDetailUiState(metric, first, last))
    val uiState = state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(selected, active) { dates, enabled -> dates to enabled }
                .flatMapLatest { (dates, enabled) ->
                    if (!enabled) emptyFlow() else repository.attendance.observeRange(
                        dates.first,
                        dates.second
                    )
                        .onEach { range ->
                            state.update {
                                it.copy(
                                    from = if (range.firstDate != null && range.firstDate <= dates.second)
                                        maxOf(dates.first, range.firstDate) else dates.first,
                                    through = dates.second,
                                    range = range,
                                    ready = true,
                                    storageError = false
                                )
                            }
                        }.catch { failure ->
                            if (failure is CancellationException) throw failure
                            AppLogger.e("HistoryDetails", "Range read failed", failure)
                            state.update { it.copy(ready = true, storageError = true) }
                        }
                }.collect()
        }
    }

    fun setActive(enabled: Boolean) {
        active.value = enabled
    }

    fun selectRange(from: LocalDate, through: LocalDate) {
        val invalid = from > through || state.value.range.firstDate?.let { from < it } == true
        state.update { it.copy(invalidRange = invalid) }
        if (invalid) return
        if (selected.value == from to through) return
        AppLogger.d("HistoryDetails", "Date range selected")
        state.update {
            it.copy(
                from = from,
                through = through,
                ready = false,
                range = it.range.copy(records = emptyList())
            )
        }
        selected.value = from to through
    }

    fun saveCorrection(command: Correction) {
        if (state.value.busy) return
        val noteTooLong = exceedsReportTextLimit(command.note)
        val invalid = command.status !in setOf(
            AttendanceStatus.PRESENT,
            AttendanceStatus.LATE,
            AttendanceStatus.ABSENT
        ) ||
                (command.status != AttendanceStatus.ABSENT && command.arrivalTime == null) || noteTooLong
        state.update { it.copy(invalidCorrection = invalid, noteTooLong = noteTooLong) }
        if (invalid) return
        state.update { it.copy(busy = true, savedKey = null, storageError = false) }
        viewModelScope.launch {
            try {
                repository.attendance.correct(command)
                AppLogger.i("HistoryDetails", "Correction saved locally")
                state.update { it.copy(savedKey = command.key) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IllegalArgumentException) {
                state.update { it.copy(invalidCorrection = true) }
            } catch (failure: Exception) {
                AppLogger.e("HistoryDetails", "Correction save failed", failure)
                state.update { it.copy(storageError = true) }
            } finally {
                state.update { it.copy(busy = false) }
            }
        }
    }
}
