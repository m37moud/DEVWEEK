package dev.daymark.presentation.features.plan

import dev.daymark.models.plan.PlanChange
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.models.plan.PlanState
import dev.daymark.models.plan.TimeOffEntry
import dev.daymark.models.plan.TimeOffPreview
import dev.daymark.presentation.features.setup.SetupIssue

data class PlanUiState(
    val plan: PlanState? = null,
    val draft: PlanChange? = null,
    val entries: List<TimeOffEntry> = emptyList(),
    val availableBalance: Int? = null,
    val ready: Boolean = false,
    val busy: Boolean = false,
    val preview: TimeOffPreview? = null,
    val timeOffPreview: TimeOffPreview? = null,
    val result: PlanMutationResult? = null,
    val invalidInput: Boolean = false,
    val locationIssue: SetupIssue? = null,
    val storageError: Boolean = false,
)
