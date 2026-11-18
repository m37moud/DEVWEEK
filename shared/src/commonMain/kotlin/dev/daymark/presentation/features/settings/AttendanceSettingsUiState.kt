package dev.daymark.presentation.features.settings

import dev.daymark.models.plan.PlanChange
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.models.plan.PlanState
import dev.daymark.models.plan.TimeOffPreview
import dev.daymark.presentation.component.PlaceAddressUiState
import dev.daymark.presentation.features.setup.SetupIssue

data class AttendanceSettingsUiState(
    val plan: PlanState? = null,
    val draft: PlanChange? = null,
    val ready: Boolean = false,
    val busy: Boolean = false,
    val preview: TimeOffPreview? = null,
    val result: PlanMutationResult? = null,
    val invalidInput: Boolean = false,
    val storageError: Boolean = false,
    val locationIssue: SetupIssue? = null,
    val placeAddress: PlaceAddressUiState = PlaceAddressUiState(),
    val placeRegistrationRevision: Long = 0
)
