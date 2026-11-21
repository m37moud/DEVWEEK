package dev.daymark.presentation.features.historydetail

import dev.daymark.history.HistoryMetric
import dev.daymark.history.HistoryRange
import dev.daymark.history.historyMetrics
import dev.daymark.models.attendance.DayKey
import kotlinx.datetime.LocalDate

data class HistoryDetailUiState(
    val metric: HistoryMetric,
    val from: LocalDate,
    val through: LocalDate,
    val range: HistoryRange = HistoryRange(emptyList(), null, from),
    val ready: Boolean = false,
    val invalidRange: Boolean = false,
    val storageError: Boolean = false,
    val busy: Boolean = false,
    val invalidCorrection: Boolean = false,
    val noteTooLong: Boolean = false,
    val savedKey: DayKey? = null
) {
    val records
        get() = range.records.filter {
            metric.includes(it) && it.plan.key.date <= range.today
        }.sortedByDescending { it.plan.key.date }
    val earlierVacationDates
        get() = if (metric == HistoryMetric.LEAVE_REST) range.earlierVacationDates.asReversed() else emptyList()
    val metrics get() = historyMetrics(range.records, range.today, range.earlierVacationDates)
}
