package dev.daymark.presentation.features.historydetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.daymark.history.AttendancePeriodSettings
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HistoryDetailViewModel(
    private val repository: Repository,
    metric: HistoryMetric,
    initialMonth: LocalDate,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    periodSettings: Flow<AttendancePeriodSettings> = flowOf(AttendancePeriodSettings())
) : ViewModel(scope) {
    private val initialPeriod = AttendancePeriodSettings().period(initialMonth)
    private val selected = MutableStateFlow<Pair<LocalDate, LocalDate>?>(null)
    private val active = MutableStateFlow(true)
    private val state = MutableStateFlow(HistoryDetailUiState(metric, initialPeriod.from, initialPeriod.through))
    val uiState = state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(selected, active, periodSettings) { dates, enabled, settings ->
                val range = dates ?: settings.period(initialMonth).let { it.from to it.through }
                range to enabled
            }
                .flatMapLatest { (dates, enabled) ->
                    if (!enabled) emptyFlow() else repository.attendance.observeRange(
                        dates.first,
                        dates.second
                    )
                        .onEach { range ->
                            state.update {
                                it.copy(
                                    from = dates.first,
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

    fun beginCorrection() {
        AppLogger.d("HistoryDetails", "Correction editor opened")
        state.update {
            it.copy(invalidCorrection = false, noteTooLong = false, savedKey = null)
        }
    }

    fun selectRange(from: LocalDate, through: LocalDate) {
        val current = state.value
        val startsTooEarly = current.range.firstDate?.let { from < it } == true
        val invalid = from > through || (current.metric != HistoryMetric.LEAVE_REST && startsTooEarly)
        state.update { it.copy(invalidRange = invalid) }
        if (invalid) return
        if (selected.value == from to through) return
        AppLogger.d("HistoryDetails", "Date range selected")
        state.update {
            it.copy(
                from = from,
                through = through,
                ready = false,
                range = it.range.copy(records = emptyList(), earlierVacationDates = emptyList())
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
