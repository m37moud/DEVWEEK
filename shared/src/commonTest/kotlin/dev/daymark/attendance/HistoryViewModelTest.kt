package dev.daymark.attendance

import dev.daymark.*
import dev.daymark.ui.HistoryViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.*
import kotlin.time.Instant

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    @Test fun month_selection_and_explicit_corrections_use_local_repository() = runTest {
        val requests = mutableListOf<Pair<Int, Int>>()
        var correction: Correction? = null
        val months = MutableStateFlow(HistoryMonth(emptyList(), 0, 0, 0))
        val repo = object : AttendanceRepository {
            override fun observeMonth(year: Int, month: Int): Flow<HistoryMonth> { requests += year to month; return months }
            override suspend fun correct(command: Correction): DayOutcome {
                correction = command
                return DayOutcome(command.status, AttendanceSource.MANUAL_CORRECTION, at("09:18:00"), null)
            }
            override suspend fun reconcile(now: Instant) = Unit
            override fun observeDay(key: DayKey): Flow<DayOutcome?> = error("Unused")
            override suspend fun loadCurrentDay(now: Instant): StoredDay? = error("No GPS or current-day check for history")
            override suspend fun loadDay(key: DayKey): StoredDay? = error("Unused")
            override suspend fun claimAutomatic(key: DayKey, attemptId: String, now: Instant): ClaimResult = error("Unused")
            override suspend fun completeCheck(request: CheckCommit): CheckOutcome = error("Unused")
        }
        val model = HistoryViewModel(repo, LocalDate(2026,10,27), backgroundScope)
        model.uiState.first { it.ready }
        model.selectMonth(2025, 12)
        model.uiState.first { it.ready && it.year == 2025 && it.month == 12 }
        assertEquals(2025 to 12, requests.last())
        val command = Correction(fixturePlan().key, AttendanceStatus.PRESENT, LocalTime(9,18), "اجتماع / Meeting =1+2")
        model.saveCorrection(command)
        model.uiState.first { it.savedKey == command.key }
        assertEquals(command, correction)
        assertEquals(AttendanceStatus.PRESENT, correction?.status)
        model.saveCorrection(command.copy(arrivalTime = null))
        assertTrue(model.uiState.value.invalidCorrection)
        assertEquals(command, correction)
        model.setActive(false); runCurrent()
        assertEquals(0, months.subscriptionCount.value)
        model.setActive(true)
        model.uiState.first { it.ready }
        runCurrent()
        assertEquals(1, months.subscriptionCount.value)
    }
}
