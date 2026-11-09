package dev.daymark.presentation.features.setup

import dev.daymark.app.SetupStep
import dev.daymark.models.setup.SetupDraft
import dev.daymark.models.settings.AppSettings
import dev.daymark.presentation.component.PlaceAddressUiState

enum class SetupIssue {
    INVALID_INPUT, LOCATION_DENIED, LOCATION_DISABLED, LOCATION_TIMEOUT, LOCATION_UNAVAILABLE,
    LOCATION_STALE, LOCATION_INACCURATE, LOCAL_SAVE_FAILED
}

data class SetupUiState(
    val draft: SetupDraft = SetupDraft(), val ready: Boolean = false, val busy: Boolean = false,
    val completed: Boolean = false, val issue: SetupIssue? = null, val draftSaved: Boolean = false,
    val unreadable: Boolean = false, val reviewEditing: Boolean = false,
    val pendingStep: SetupStep? = null,
    val placeAddress: PlaceAddressUiState = PlaceAddressUiState(),
    val placeRegistrationRevision: Long = 0,
    val preferences: AppSettings = AppSettings(),
    val preferencesReady: Boolean = false,
    val preferencesSaving: Boolean = false,
    val preferencesError: Boolean = false
) {
    val interactionBusy: Boolean get() = busy || preferencesSaving || !preferencesReady
}
