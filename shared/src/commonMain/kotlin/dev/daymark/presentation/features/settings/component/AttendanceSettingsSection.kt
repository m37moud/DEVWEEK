package dev.daymark.presentation.features.settings.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.daymark.models.plan.PlanChange
import dev.daymark.models.plan.PlanReviewToken
import dev.daymark.presentation.component.ArrivalTimeDialog
import dev.daymark.presentation.component.PlanMutationFeedback
import dev.daymark.presentation.component.PlanPreviewDialog
import dev.daymark.presentation.component.ZonePicker
import dev.daymark.presentation.features.settings.AttendanceSettingsUiState
import dev.daymark.presentation.features.settings.AttendanceSettingsViewModel
import dev.daymark.resources.Res
import dev.daymark.resources.load_failed
import dev.daymark.resources.plan_effective
import dev.daymark.resources.plan_invalid
import dev.daymark.resources.plan_review
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.str
import kotlinx.datetime.DayOfWeek

data class AttendanceSettingsActions(
    val edit: ((PlanChange) -> PlanChange) -> Unit,
    val preview: (PlanChange) -> Unit,
    val confirm: (PlanReviewToken) -> Unit,
    val cancelPreview: () -> Unit,
    val registerCurrentPlace: () -> Unit,
    val openLocationSettings: () -> Unit
)

@Composable
internal fun AttendanceSettingsSection(model: AttendanceSettingsViewModel) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val language = LocalAppLanguage.current
    val place = state.draft?.place
    LaunchedEffect(model, place?.latitude, place?.longitude, language, state.placeRegistrationRevision) {
        if (place != null) model.showPlaceAddress(place, language)
    }
    val actions = remember(model) {
        AttendanceSettingsActions(
            edit = model::edit,
            preview = model::preview,
            confirm = model::confirm,
            cancelPreview = model::cancelPreview,
            registerCurrentPlace = model::registerCurrentPlace,
            openLocationSettings = model::openLocationSettings
        )
    }
    AttendanceSettingsContent(state, actions)
}

@Composable
fun AttendanceSettingsContent(
    state: AttendanceSettingsUiState,
    actions: AttendanceSettingsActions
) {
    val draft = state.draft
    var chooseZone by rememberSaveable { mutableStateOf(false) }
    var timeDay by rememberSaveable { mutableStateOf<String?>(null) }

    if (!state.ready) CircularProgressIndicator()
    if (state.storageError) {
        Text(str(Res.string.load_failed), color = MaterialTheme.colorScheme.error)
    }
    if (draft == null) return

    AttendanceSettingsCards(
        state = state,
        draft = draft,
        actions = actions,
        pickers = AttendanceSettingsPickers(
            pickZone = { chooseZone = true },
            pickTime = { day -> timeDay = day }
        )
    )
    AttendanceSettingsReview(state, draft, actions)

    if (chooseZone) {
        ZonePicker(
            current = draft.schedule.zoneId,
            onSelect = { zone ->
                actions.edit { it.copy(schedule = it.schedule.copy(zoneId = zone)) }
                chooseZone = false
            },
            onDismiss = { chooseZone = false }
        )
    }

    if (timeDay != null) {
        val day = timeDay?.takeIf { it != "default" }?.let(DayOfWeek::valueOf)
        val initial = day?.let { draft.schedule.overrides[it] } ?: draft.schedule.arrival
        ArrivalTimeDialog(
            initial = initial,
            onConfirm = { selected ->
                actions.edit {
                    it.copy(
                        schedule = if (day == null) {
                            it.schedule.copy(arrival = selected)
                        } else {
                            it.schedule.copy(overrides = it.schedule.overrides + (day to selected))
                        }
                    )
                }
                timeDay = null
            },
            onDismiss = { timeDay = null }
        )
    }
}

private data class AttendanceSettingsPickers(
    val pickZone: () -> Unit,
    val pickTime: (String) -> Unit
)

@Composable
private fun AttendanceSettingsCards(
    state: AttendanceSettingsUiState,
    draft: PlanChange,
    actions: AttendanceSettingsActions,
    pickers: AttendanceSettingsPickers
) {
    PlanRoutineCard(
        state = PlanRoutineUiState(
            category = draft.category,
            schedule = draft.schedule,
            busy = state.busy
        ),
        actions = PlanRoutineActions(
            changeCategory = { newCategory ->
                actions.edit { it.copy(category = newCategory) }
            },
            toggleDay = { day ->
                actions.edit {
                    val days =
                        if (day in it.schedule.days) it.schedule.days - day else it.schedule.days + day
                    it.copy(
                        schedule = it.schedule.copy(
                            days = days,
                            overrides = it.schedule.overrides.filterKeys { selected -> selected in days },
                            departureOverrides = it.schedule.departureOverrides.filterKeys { selected -> selected in days }
                        )
                    )
                }
            },
            pickArrival = { pickers.pickTime("default") },
            changeGrace = { minutes ->
                actions.edit {
                    it.copy(schedule = it.schedule.copy(graceMinutes = minutes))
                }
            },
            pickDayOverride = { day -> pickers.pickTime(day.name) },
            resetDayOverride = { day ->
                actions.edit {
                    it.copy(schedule = it.schedule.copy(overrides = it.schedule.overrides - day))
                }
            },
            pickZone = pickers.pickZone,
            changeSchedule = { schedule -> actions.edit { it.copy(schedule = schedule) } }
        )
    )

    PlanPlaceCard(
        state = PlanPlaceUiState(
            draft.place, state.placeAddress, state.plan?.placeName.orEmpty(),
            state.busy, state.locationIssue
        ),
        actions = PlanPlaceActions(
            changeRadius = { radius ->
                actions.edit { it.copy(place = it.place.copy(radiusMetres = radius)) }
            },
            registerCurrentPlace = actions.registerCurrentPlace,
            openLocationSettings = actions.openLocationSettings
        )
    )
}

@Composable
private fun AttendanceSettingsReview(
    state: AttendanceSettingsUiState,
    draft: PlanChange,
    actions: AttendanceSettingsActions
) {
    state.plan?.effectiveFrom?.let { effectiveDate ->
        Text(
            text = str(
                Res.string.plan_effective,
                DisplayFormats.date(effectiveDate, LocalAppLanguage.current)
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (state.invalidInput) {
        Text(str(Res.string.plan_invalid), color = MaterialTheme.colorScheme.error)
    }
    PlanMutationFeedback(state.result)
    Button(
        onClick = { actions.preview(draft) },
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth().testTag("plan-review")
    ) {
        Text(str(Res.string.plan_review))
    }
    state.preview?.let { preview ->
        PlanPreviewDialog(preview, draft.category, state.busy, actions.confirm, actions.cancelPreview)
    }
}
