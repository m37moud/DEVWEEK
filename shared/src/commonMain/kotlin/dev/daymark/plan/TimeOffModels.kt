package dev.daymark.plan

import dev.daymark.attendance.*
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

data class TimeOffEntry(val id: String, val kind: TimeOffKind, val from: LocalDate, val through: LocalDate, val updatedAt: Instant) {
    init { require(from <= through) }
}
sealed interface TimeOffCommand {
    data class Add(val kind: TimeOffKind, val from: LocalDate, val through: LocalDate) : TimeOffCommand
    data class Replace(val entryId: String, val kind: TimeOffKind, val from: LocalDate, val through: LocalDate) : TimeOffCommand
    data class Remove(val entryId: String) : TimeOffCommand
}
enum class PlanRejection { INVALID_RANGE, INVALID_VALUE, OUTSIDE_TRACKED_PERIOD, INSUFFICIENT_BALANCE, LOCAL_SAVE_FAILED }
data class TimeOffDayChange(val key: DayKey,val before: DayOutcome,val after: DayOutcome,
    val evidence: AttendanceEvidence?,val category: TrackerCategory,val zoneId: String)
data class TimeOffPreview(val expectedRevision: Long, val affectedDays: List<DayKey>, val resultingOutcomes: List<DayOutcome>,
    val balanceDelta: Int, val availableAfter: Int, val rejection: PlanRejection?, val effectiveFrom: LocalDate? = null,val changes: List<TimeOffDayChange> = emptyList())
sealed interface PlanMutationResult {
    data object Applied : PlanMutationResult
    data object StalePreview : PlanMutationResult
    data class Invalid(val reason: PlanRejection) : PlanMutationResult
    data object InsufficientBalance : PlanMutationResult
    data object LocalSaveFailed : PlanMutationResult
}
data class PlanChange(val schedule: WeeklySchedule, val category: TrackerCategory, val place: RegisteredPlace)
data class PlanState(val current: PlanChange, val pending: PlanChange?, val effectiveFrom: LocalDate?)
