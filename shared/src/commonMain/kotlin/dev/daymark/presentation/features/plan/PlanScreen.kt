package dev.daymark.presentation.features.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.models.plan.PlanChange
import dev.daymark.models.plan.TimeOffCommand
import dev.daymark.plan.TimeOffKind
import dev.daymark.presentation.component.ArrivalTimeDialog
import dev.daymark.presentation.component.ZonePicker
import dev.daymark.presentation.features.plan.component.BalanceAdjustUiState
import dev.daymark.presentation.features.plan.component.BalanceAdjustDialog
import dev.daymark.presentation.features.plan.component.PlanPlaceActions
import dev.daymark.presentation.features.plan.component.PlanPlaceCard
import dev.daymark.presentation.features.plan.component.PlanPlaceUiState
import dev.daymark.presentation.features.plan.component.PlanPreviewDialog
import dev.daymark.presentation.features.plan.component.PlanRoutineActions
import dev.daymark.presentation.features.plan.component.PlanRoutineCard
import dev.daymark.presentation.features.plan.component.PlanRoutineUiState
import dev.daymark.presentation.features.plan.component.PlanTimeOffCard
import dev.daymark.presentation.features.plan.component.TimeOffEditorDialog
import dev.daymark.resources.Res
import dev.daymark.resources.load_failed
import dev.daymark.resources.plan_effective
import dev.daymark.resources.plan_title
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.str
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

data class PlanActions(
    val edit: ((PlanChange) -> PlanChange) -> Unit,
    val preview: (PlanChange) -> Unit,
    val confirm: (Long) -> Unit,
    val previewTimeOff: (TimeOffCommand) -> Unit,
    val confirmTimeOff: (Long) -> Unit,
    val cancelPreview: () -> Unit,
    val setAvailableBalance: (String, String?) -> Unit,
    val registerCurrentPlace: () -> Unit,
    val openLocationSettings: () -> Unit
)

@Composable
fun PlanScreen(model: PlanViewModel, clock: Clock, initialTimeOffDate: LocalDate? = null) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val language = LocalAppLanguage.current
    val place = state.draft?.place
    LaunchedEffect(
        model,
        place?.latitude,
        place?.longitude,
        language,
        state.placeRegistrationRevision
    ) {
        if (place != null) model.showPlaceAddress(place, language)
    }
    val actions = remember(model) {
        PlanActions(
            edit = model::edit,
            preview = model::preview,
            confirm = model::confirm,
            previewTimeOff = model::previewTimeOff,
            confirmTimeOff = model::confirmTimeOff,
            cancelPreview = model::cancelPreview,
            setAvailableBalance = model::setAvailableBalance,
            registerCurrentPlace = model::registerCurrentPlace,
            openLocationSettings = model::openLocationSettings
        )
    }
    PlanContent(state, clock, initialTimeOffDate, actions)
}

@Composable
fun PlanContent(
    state: PlanUiState,
    clock: Clock,
    initialTimeOffDate: LocalDate?,
    actions: PlanActions,
    appLanguage: AppLanguage = LocalAppLanguage.current
) {
    val draft = state.draft
    val savedCategory = state.plan?.current?.category ?: draft?.category
    var chooseZone by rememberSaveable { mutableStateOf(false) }
    var timeDay by rememberSaveable { mutableStateOf<String?>(null) }
    var editOffId by rememberSaveable { mutableStateOf<String?>(null) }
    val editOff = state.entries.firstOrNull { it.id == editOffId }
    var timeOffStartDate by rememberSaveable { mutableStateOf<String?>(null) }
    var addingOff by rememberSaveable { mutableStateOf(false) }
    var adjusting by rememberSaveable { mutableStateOf(false) }
    var handledInitialDate by rememberSaveable(initialTimeOffDate) { mutableStateOf(false) }

    var balanceSubmitted by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.balanceSaveRevision) {
        if (balanceSubmitted && state.result == PlanMutationResult.Applied) adjusting = false
    }

    LaunchedEffect(state.ready, initialTimeOffDate) {
        if (state.ready && initialTimeOffDate != null && !handledInitialDate) {
            handledInitialDate = true
            editOffId = state.entries.sortedBy { if (it.kind == TimeOffKind.HOLIDAY) 0 else 1 }
                .firstOrNull { initialTimeOffDate in it.from..it.through }?.id
            timeOffStartDate = initialTimeOffDate.toString()
            addingOff = editOffId == null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("plan-root"),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = str(Res.string.plan_title),
            style = MaterialTheme.typography.headlineLarge
        )

        if (!state.ready) {
            CircularProgressIndicator()
        }

        if (state.storageError) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = str(Res.string.load_failed),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        if (draft != null) {
            state.plan?.effectiveFrom?.let { effectiveDate ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = str(
                            Res.string.plan_effective,
                            DisplayFormats.date(effectiveDate, appLanguage)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            PlanRoutineCard(
                state = PlanRoutineUiState(
                    category = draft.category,
                    schedule = draft.schedule,
                    busy = state.busy,
                    invalidInput = state.invalidInput,
                    result = state.result
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
                    pickArrival = { timeDay = "default" },
                    changeGrace = { minutes ->
                        actions.edit {
                            it.copy(schedule = it.schedule.copy(graceMinutes = minutes))
                        }
                    },
                    pickDayOverride = { day -> timeDay = day.name },
                    resetDayOverride = { day ->
                        actions.edit {
                            it.copy(schedule = it.schedule.copy(overrides = it.schedule.overrides - day))
                        }
                    },
                    pickZone = { chooseZone = true },
                    reviewChanges = { actions.preview(draft) },
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

            PlanTimeOffCard(
                category = savedCategory ?: draft.category,
                availableBalance = state.availableBalance,
                entries = state.entries,
                busy = state.busy,
                onAdjustBalance = {
                    balanceSubmitted = false
                    adjusting = true
                },
                onAddTimeOff = {
                    timeOffStartDate = null
                    addingOff = true
                },
                onEditEntry = { editOffId = it.id }
            )
        }
    }

    if (chooseZone && draft != null) {
        ZonePicker(
            current = draft.schedule.zoneId,
            onSelect = { zone ->
                actions.edit { it.copy(schedule = it.schedule.copy(zoneId = zone)) }
                chooseZone = false
            },
            onDismiss = { chooseZone = false }
        )
    }

    if (timeDay != null && draft != null) {
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

    if ((addingOff || editOff != null) && draft != null &&
        state.preview == null && state.timeOffPreview == null
    ) {
        val today = clock.now().toLocalDateTime(
            TimeZone.of(state.plan?.current?.schedule?.zoneId ?: draft.schedule.zoneId)
        ).date
        TimeOffEditorDialog(
            entry = editOff,
            today = timeOffStartDate?.let(LocalDate::parse) ?: today,
            category = savedCategory ?: draft.category,
            busy = state.busy,
            onDismiss = {
                addingOff = false
                editOffId = null
            },
            onPreview = { command ->
                addingOff = false
                editOffId = null
                actions.previewTimeOff(command)
            }
        )
    }

    val preview = state.preview ?: state.timeOffPreview
    if (preview != null) {
        PlanPreviewDialog(
            preview = preview,
            category = if (state.timeOffPreview != null) savedCategory else draft?.category,
            busy = state.busy,
            onConfirm = { revision ->
                if (state.timeOffPreview != null) {
                    actions.confirmTimeOff(revision)
                } else {
                    actions.confirm(revision)
                }
            },
            onDismiss = actions.cancelPreview
        )
    }

    if (adjusting) {
        BalanceAdjustDialog(
            state = BalanceAdjustUiState(
                initialBalance = state.availableBalance,
                busy = state.busy,
                saveFailed = balanceSubmitted && !state.busy &&
                        (state.storageError || state.result != null && state.result != PlanMutationResult.Applied)
            ),
            onSave = { text, note ->
                balanceSubmitted = true
                actions.setAvailableBalance(text, note)
            },
            onDismiss = { if (!state.busy) adjusting = false }
        )
    }
}
