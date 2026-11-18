package dev.daymark.presentation.features.today

import dev.daymark.models.TodayDashboard
import dev.daymark.models.attendance.CheckRejection
import dev.daymark.models.attendance.DayOutcome
import dev.daymark.models.attendance.DayPlan
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.models.plan.VacationBalanceSummary
import dev.daymark.models.reporting.ReportHealth
import kotlinx.datetime.LocalDate

data class TodayUiState(
    val date: LocalDate? = null,
    val plan: DayPlan? = null,
    val outcome: DayOutcome? = null,
    val busy: Boolean = false,
    val canCheck: Boolean = false,
    val error: CheckRejection? = null,
    val ready: Boolean = false,
    val active: Boolean = false,
    val activeCategory: TrackerCategory? = null,
    val dashboard: TodayDashboard = TodayDashboard(),
    val report: ReportHealth = ReportHealth(),
    val vacationBalance: TodayVacationBalanceUiState = TodayVacationBalanceUiState(),
)

data class TodayVacationBalanceUiState(
    val summary: VacationBalanceSummary? = null,
    val loadFailed: Boolean = false,
    val saving: Boolean = false,
    val saveResult: PlanMutationResult? = null,
    val saveRevision: Long = 0
)
