package dev.daymark.presentation.features.history

import dev.daymark.models.attendance.DayKey
import dev.daymark.models.attendance.HistoryMonth
import kotlinx.datetime.LocalDate

data class HistoryUiState(
    val year: Int,
    val month: Int,
    val history: HistoryMonth = HistoryMonth(emptyList(), 0, 0, 0),
    val ready: Boolean = false,
    val busy: Boolean = false,
    val savedKey: DayKey? = null,
    val today: LocalDate = LocalDate(year, month, 1),
    val invalidCorrection: Boolean = false,
    val noteTooLong: Boolean = false,
    val storageError: Boolean = false,
)
