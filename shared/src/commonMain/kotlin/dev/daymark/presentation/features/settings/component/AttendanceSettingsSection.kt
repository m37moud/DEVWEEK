package dev.daymark.presentation.features.settings.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.models.plan.PlanReviewToken
import dev.daymark.models.plan.PlanState
import dev.daymark.presentation.component.ArrivalTimeDialog
import dev.daymark.presentation.component.PlanMutationFeedback
import dev.daymark.presentation.component.PlanPreviewDialog
import dev.daymark.presentation.component.ZonePicker
import dev.daymark.presentation.component.label
import dev.daymark.presentation.component.localTimeLabel
import dev.daymark.presentation.features.settings.AttendanceSettingsUiState
import dev.daymark.presentation.features.settings.AttendanceSettingsViewModel
import dev.daymark.resources.Res
import dev.daymark.resources.arrival
import dev.daymark.resources.cancel
import dev.daymark.resources.load_failed
import dev.daymark.resources.place_ready
import dev.daymark.resources.plan_effective
import dev.daymark.resources.plan_invalid
import dev.daymark.resources.plan_review
import dev.daymark.resources.radius_metres
import dev.daymark.resources.settings_routine_and_place
import dev.daymark.resources.settings_summary_value
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.isolated
import dev.daymark.settings.str
import kotlinx.datetime.DayOfWeek
import org.koin.compose.viewmodel.koinViewModel

data class AttendanceSettingsActions(
    val edit: ((PlanChange) -> PlanChange) -> Unit,
    val preview: (PlanChange) -> Unit,
    val confirm: (PlanReviewToken) -> Unit,
    val cancelPreview: () -> Unit,
    val registerCurrentPlace: () -> Unit,
    val openLocationSettings: () -> Unit,
    val resetDraft: () -> Unit
)

@Composable
internal fun AttendanceSettingsSection(
    attendanceSettingsViewModel: AttendanceSettingsViewModel = koinViewModel()
) {
    val state by attendanceSettingsViewModel.uiState.collectAsStateWithLifecycle()
    val language = LocalAppLanguage.current
    val place = state.draft?.place
    LaunchedEffect(
        attendanceSettingsViewModel,
        place?.latitude,
        place?.longitude,
        language,
        state.placeRegistrationRevision
    ) {
        if (place != null) attendanceSettingsViewModel.showPlaceAddress(place, language)
    }
    val actions = remember(attendanceSettingsViewModel) {
        AttendanceSettingsActions(
            edit = attendanceSettingsViewModel::edit,
            preview = attendanceSettingsViewModel::preview,
            confirm = attendanceSettingsViewModel::confirm,
            cancelPreview = attendanceSettingsViewModel::cancelPreview,
            registerCurrentPlace = attendanceSettingsViewModel::registerCurrentPlace,
            openLocationSettings = attendanceSettingsViewModel::openLocationSettings,
            resetDraft = attendanceSettingsViewModel::resetDraft
        )
    }
    AttendanceSettingsContent(state = state, actions = actions)
}

@Composable
fun AttendanceSettingsContent(
    state: AttendanceSettingsUiState,
    actions: AttendanceSettingsActions
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.result) {
        if (state.result == PlanMutationResult.Applied) editing = false
    }

    if (!state.ready) CircularProgressIndicator()
    if (state.storageError) Text(
        str(Res.string.load_failed),
        color = MaterialTheme.colorScheme.error
    )
    val plan = state.plan ?: return
    SettingsEditorCard(
        SettingsEditorHeader(
            Res.string.settings_routine_and_place,
            "settings-routine-edit",
            Res.string.cancel,
            editing,
            !state.busy
        ),
        onToggle = {
            actions.resetDraft()
            editing = !editing
        }
    ) {
        val draft = state.draft
        if (editing && draft != null) AttendancePlanEditor(state, draft, actions)
        else AttendancePlanSummary(plan)
    }
}

@Composable
private fun AttendancePlanSummary(plan: PlanState) {
    val saved = plan.pending ?: plan.current
    val language = LocalAppLanguage.current
    val weekdays = saved.schedule.days.sorted().map { str(it.label()) }.joinToString(" · ")
    SettingsSummaryText(
        str(
            Res.string.settings_summary_value,
            str(saved.category.label()),
            weekdays
        )
    )
    SettingsSummaryText(
        str(
            Res.string.settings_summary_value,
            str(Res.string.arrival),
            isolated(localTimeLabel(saved.schedule.arrival, language))
        )
    )
    val radius = isolated(DisplayFormats.number(saved.place.radiusMetres, language))
    val place = if (plan.placeName.isBlank()) str(Res.string.place_ready, radius)
    else str(
        Res.string.settings_summary_value,
        isolated(plan.placeName),
        str(Res.string.radius_metres, radius)
    )
    SettingsSummaryText(place)
    AttendancePlanEffectiveDate(plan)
}

@Composable
private fun AttendancePlanEditor(
    state: AttendanceSettingsUiState,
    draft: PlanChange,
    actions: AttendanceSettingsActions
) {
    var chooseZone by rememberSaveable { mutableStateOf(false) }
    var timeDay by rememberSaveable { mutableStateOf<String?>(null) }
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

    timeDay?.let { day -> AttendancePlanTimePicker(draft, day, actions.edit) { timeDay = null } }
}

@Composable
private fun AttendancePlanTimePicker(
    draft: PlanChange,
    selection: String,
    edit: ((PlanChange) -> PlanChange) -> Unit,
    close: () -> Unit
) {
    val day = selection.takeIf { it != "default" }?.let(DayOfWeek::valueOf)
    ArrivalTimeDialog(
        initial = day?.let { draft.schedule.overrides[it] } ?: draft.schedule.arrival,
        onConfirm = { selected ->
            edit { plan ->
                val schedule = if (day == null) plan.schedule.copy(arrival = selected)
                else plan.schedule.copy(overrides = plan.schedule.overrides + (day to selected))
                plan.copy(schedule = schedule)
            }
            close()
        },
        onDismiss = close
    )
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
    PlanRoutineContent(
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

    HorizontalDivider()
    PlanPlaceEditor(
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
    state.plan?.let { AttendancePlanEffectiveDate(it) }
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
        PlanPreviewDialog(
            preview,
            draft.category,
            state.busy,
            actions.confirm,
            actions.cancelPreview
        )
    }
}

@Composable
private fun AttendancePlanEffectiveDate(plan: PlanState) {
    plan.effectiveFrom?.let { date ->
        SettingsSummaryText(
            str(
                Res.string.plan_effective,
                DisplayFormats.date(date, LocalAppLanguage.current)
            )
        )
    }
}
