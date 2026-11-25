package dev.daymark.attendance

import dev.daymark.models.attendance.CheckCommit
import dev.daymark.models.attendance.CheckOutcome
import dev.daymark.models.attendance.ClaimResult
import dev.daymark.models.attendance.Correction
import dev.daymark.models.attendance.DayKey
import dev.daymark.models.attendance.DayOutcome
import dev.daymark.models.attendance.HistoryMonth
import dev.daymark.models.attendance.StoredDay
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

interface AttendanceRepository {
    fun observeDay(key: DayKey): Flow<DayOutcome?>
    fun observeMonth(year: Int, month: Int): Flow<HistoryMonth>
    fun observeRange(
        from: kotlinx.datetime.LocalDate,
        through: kotlinx.datetime.LocalDate
    ): Flow<dev.daymark.history.HistoryRange>

    /** Can project today's baseline without persisting an untracked absence. */
    suspend fun loadCurrentDay(now: Instant): StoredDay?
    suspend fun loadDay(key: DayKey): StoredDay?
    suspend fun claimAutomatic(key: DayKey, attemptId: String, now: Instant, ordinal: Int = 1): ClaimResult
    suspend fun abandonAutomatic(key: DayKey, attemptId: String) = Unit
    suspend fun completeCheck(request: CheckCommit): CheckOutcome
    suspend fun correct(command: Correction): DayOutcome
    suspend fun reconcile(now: Instant)
}

internal fun StoredDay.outcome(now: Instant) = evaluateDay(plan, evidence, off, now)
