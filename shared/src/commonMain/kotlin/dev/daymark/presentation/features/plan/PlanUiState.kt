package dev.daymark.presentation.features.plan

import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.models.plan.PlanState
import dev.daymark.models.plan.TimeOffEntry
import dev.daymark.models.plan.TimeOffPreview

data class PlanUiState(
    val plan: PlanState? = null,
    val entries: List<TimeOffEntry> = emptyList(),
    val ready: Boolean = false,
    val busy: Boolean = false,
    val timeOffPreview: TimeOffPreview? = null,
    val result: PlanMutationResult? = null,
    val storageError: Boolean = false
)
