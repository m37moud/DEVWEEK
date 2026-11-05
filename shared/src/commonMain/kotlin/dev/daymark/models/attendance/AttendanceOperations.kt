package dev.daymark.models.attendance

import dev.daymark.attendance.outcome
import dev.daymark.plan.TimeOffKind
import kotlin.time.Instant
import kotlinx.datetime.LocalTime

data class HistoryRecord(
    val plan: DayPlan,
    val evidence: AttendanceEvidence?,
    val outcome: DayOutcome
)

data class HistoryMonth(
    val records: List<HistoryRecord>,
    val present: Int,
    val late: Int,
    val absent: Int
)

enum class AutomaticState { UNCLAIMED, CLAIMED, FINISHED, INTERRUPTED }
data class StoredDay(
    val plan: DayPlan, val evidence: AttendanceEvidence?, val off: TimeOffKind?,
    val automaticState: AutomaticState, val revision: Long, val userEditRevision: Long = 0
)

sealed interface ClaimResult {
    data class Claimed(val day: StoredDay, val attemptId: String) : ClaimResult
    data object AlreadyConsumed : ClaimResult
    data object Ineligible : ClaimResult
}

data class Correction(
    val key: DayKey,
    val status: AttendanceStatus,
    val arrivalTime: LocalTime?,
    val note: String?
) {
    override fun toString(): String = "Correction(status=$status, note=<redacted>)"
}

data class CheckCommit(
    val key: DayKey,
    val attemptId: String?,
    val expectedScheduleVersion: Long,
    val expectedPlaceVersion: Long,
    val verdict: FixVerdict,
    val mode: CheckMode,
    val updatedAt: Instant,
    val expectedUserEditRevision: Long = 0
)

sealed interface CheckOutcome {
    val currentDay: DayOutcome?
    val reason: CheckRejection? get() = null

    data class Recorded(override val currentDay: DayOutcome) : CheckOutcome
    data class Rejected(override val currentDay: DayOutcome?, override val reason: CheckRejection) :
        CheckOutcome

    data class AlreadyRecorded(override val currentDay: DayOutcome?) : CheckOutcome
    data class StateChanged(override val currentDay: DayOutcome?) : CheckOutcome {
        override val reason = CheckRejection.STATE_CHANGED
    }

    data class Busy(override val currentDay: DayOutcome?) : CheckOutcome
}
