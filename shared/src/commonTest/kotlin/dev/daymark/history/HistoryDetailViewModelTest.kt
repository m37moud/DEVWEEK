package dev.daymark.history

import dev.daymark.attendance.AttendanceRepository
import dev.daymark.models.attendance.*
import dev.daymark.presentation.features.historydetail.HistoryDetailViewModel
import dev.daymark.testRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HistoryDetailViewModelTest {
    @Test
    fun inclusive_range_validates_start_and_order_before_querying() = runTest {
        val start = LocalDate(2026, 10, 10)
        val requests = mutableListOf<Pair<LocalDate, LocalDate>>()
        val source = object : AttendanceRepository {
            override fun observeRange(from: LocalDate, through: LocalDate) = flowOf(
                HistoryRange(emptyList(), start, LocalDate(2026, 10, 27))
            ).also { requests += from to through }

            override fun observeMonth(year: Int, month: Int) = error("Unused")
            override fun observeDay(key: DayKey) = error("Unused")
            override suspend fun loadCurrentDay(now: Instant) = error("Unused")
            override suspend fun loadDay(key: DayKey) = error("Unused")
            override suspend fun claimAutomatic(key: DayKey, attemptId: String, now: Instant) =
                error("Unused")

            override suspend fun completeCheck(request: CheckCommit) = error("Unused")
            override suspend fun correct(command: Correction) = error("Unused")
            override suspend fun reconcile(now: Instant) = Unit
        }
        val model = HistoryDetailViewModel(
            testRepository(attendanceSource = source), HistoryMetric.LATE,
            LocalDate(2026, 10, 1), backgroundScope,
            periodSettings = flowOf(AttendancePeriodSettings(20))
        )
        val period = model.uiState.first { it.ready }
        assertEquals(LocalDate(2026, 9, 21), period.from)
        assertEquals(LocalDate(2026, 10, 20), period.through)
        assertEquals(period.from to period.through, requests.single())
        model.selectRange(LocalDate(2026, 10, 9), start)
        assertTrue(model.uiState.value.invalidRange)
        model.selectRange(start, LocalDate(2026, 10, 9))
        assertTrue(model.uiState.value.invalidRange)
        model.selectRange(start, start)
        runCurrent()
        assertEquals(start to start, requests.last())
    }
}
