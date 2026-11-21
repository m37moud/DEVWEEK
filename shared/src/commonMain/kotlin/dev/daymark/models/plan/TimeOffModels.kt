package dev.daymark.models.plan

import dev.daymark.history.AttendancePeriod
import dev.daymark.history.AttendancePeriodSettings
import dev.daymark.models.attendance.AttendanceEvidence
import dev.daymark.models.attendance.DayKey
import dev.daymark.models.attendance.DayOutcome
import dev.daymark.models.attendance.RegisteredPlace
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.plan.TimeOffKind
import dev.daymark.plan.WeeklySchedule
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

data class VacationBalanceSummary(
    val originalDays: Int,
    val usedDays: Int,
    val remainingDays: Int,
    val year: Int? = null
)

data class AnnualVacationPolicy(
    val effectiveYear: Int,
    val annualAllowanceDays: Int,
    val carryOverUnusedDays: Boolean
)

data class AnnualVacationSettings(
    val active: AnnualVacationPolicy?,
    val pending: AnnualVacationPolicy?,
    val nextEffectiveYear: Int,
    val nextEffectiveDate: LocalDate = LocalDate(nextEffectiveYear, 1, 1)
)

data class VacationBalancePeriodChange(val year: Int?, val delta: Int, val remainingDays: Int)

data class AttendancePeriodReview(
    val settings: AttendancePeriodSettings,
    val period: AttendancePeriod,
    val token: PlanReviewToken,
    val balances: List<AttendancePeriodBalanceChange>,
    val rejection: PlanMutationResult?
)

data class AttendancePeriodBalanceChange(val year: Int?, val usedDelta: Int, val remainingDays: Int)

data class PlanReviewToken(val revision: Long, val attendanceDate: LocalDate?)

data class TimeOffEntry(
    val id: String,
    val kind: TimeOffKind,
    val from: LocalDate,
    val through: LocalDate,
    val updatedAt: Instant
) {
    init {
        require(from <= through)
    }
}

sealed interface TimeOffCommand {
    data class Add(val kind: TimeOffKind, val from: LocalDate, val through: LocalDate) :
        TimeOffCommand

    data class Replace(
        val entryId: String,
        val kind: TimeOffKind,
        val from: LocalDate,
        val through: LocalDate
    ) : TimeOffCommand

    data class Remove(val entryId: String) : TimeOffCommand
}

enum class PlanRejection { INVALID_RANGE, INVALID_VALUE, INSUFFICIENT_BALANCE, LOCAL_SAVE_FAILED }
data class TimeOffDayChange(
    val key: DayKey, val before: DayOutcome, val after: DayOutcome,
    val evidence: AttendanceEvidence?, val category: TrackerCategory, val zoneId: String
)

data class TimeOffPreview(
    val expectedRevision: Long,
    val affectedDays: List<DayKey>,
    val resultingOutcomes: List<DayOutcome>,
    val balanceDelta: Int,
    val availableAfter: Int,
    val rejection: PlanRejection?,
    val effectiveFrom: LocalDate? = null,
    val changes: List<TimeOffDayChange> = emptyList(),
    val balancePeriods: List<VacationBalancePeriodChange> = emptyList(),
    val shortfallYear: Int? = null,
    val reviewDate: LocalDate? = null,
    val preTrackingDates: List<LocalDate> = emptyList()
) {
    val reviewToken: PlanReviewToken get() = PlanReviewToken(expectedRevision, reviewDate)
}

sealed interface PlanMutationResult {
    data object Applied : PlanMutationResult
    data object StalePreview : PlanMutationResult
    data class Invalid(val reason: PlanRejection) : PlanMutationResult
    data object InsufficientBalance : PlanMutationResult
    data class InsufficientYear(val year: Int) : PlanMutationResult
    data object LocalSaveFailed : PlanMutationResult
}

data class PlanChange(
    val schedule: WeeklySchedule,
    val category: TrackerCategory,
    val place: RegisteredPlace
)

data class PlanState(
    val current: PlanChange,
    val pending: PlanChange?,
    val effectiveFrom: LocalDate?,
    val placeName: String = ""
) {
    override fun toString() = "PlanState(metadata=<redacted>)"
}
