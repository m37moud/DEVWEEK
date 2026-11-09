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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.daymark.models.attendance.RegisteredPlace
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.location.PlaceAddressRequest
import dev.daymark.presentation.component.PlaceAddressUiState
import dev.daymark.settings.AppLanguage
import dev.daymark.models.plan.PlanChange
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.models.plan.TimeOffCommand
import dev.daymark.models.plan.TimeOffEntry
import dev.daymark.plan.TimeOffKind
import dev.daymark.plan.WeeklySchedule
import dev.daymark.presentation.component.ZonePicker
import dev.daymark.presentation.features.plan.component.ArrivalTimeDialog
import dev.daymark.presentation.features.plan.component.BalanceAdjustDialog
import dev.daymark.presentation.features.plan.component.PlanPlaceActions
import dev.daymark.presentation.features.plan.component.PlanPlaceUiState
import dev.daymark.presentation.features.plan.component.PlanPlaceCard
import dev.daymark.presentation.features.plan.component.PlanPreviewDialog
import dev.daymark.presentation.features.plan.component.PlanRoutineCard
import dev.daymark.presentation.features.plan.component.PlanTimeOffCard
import dev.daymark.presentation.features.plan.component.TimeOffEditorDialog
import dev.daymark.resources.Res
import dev.daymark.resources.load_failed
import dev.daymark.resources.local_save_failed
import dev.daymark.resources.plan_effective
import dev.daymark.resources.plan_insufficient
import dev.daymark.resources.plan_invalid
import dev.daymark.resources.plan_saved
import dev.daymark.resources.plan_stale
import dev.daymark.resources.plan_title
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.str
import dev.daymark.ui.theme.DaymarkTheme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

private data class PlanActions(
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
    LaunchedEffect(model, place?.latitude, place?.longitude, language, state.placeRegistrationRevision) {
        if (place != null) model.showPlaceAddress(place, language)
    }
    val actions = remember(model) {
        PlanActions(
            model::edit,
            model::preview,
            model::confirm,
            model::previewTimeOff,
            model::confirmTimeOff,
            model::cancelPreview,
            model::setAvailableBalance,
            model::registerCurrentPlace,
            model::openLocationSettings
        )
    }
    PlanContent(state, clock, initialTimeOffDate, actions)
}

@Composable
private fun PlanContent(
    state: PlanUiState,
    clock: Clock,
    initialTimeOffDate: LocalDate?,
    actions: PlanActions
) {
    val language = LocalAppLanguage.current
    val draft = state.draft
    var chooseZone by remember { mutableStateOf(false) }
    var timeDay by remember { mutableStateOf<String?>(null) }
    var editOff by remember { mutableStateOf<TimeOffEntry?>(null) }
    var addingOff by remember { mutableStateOf(false) }
    var adjusting by remember { mutableStateOf(false) }
    var handledInitialDate by remember { mutableStateOf(false) }

    LaunchedEffect(state.ready, initialTimeOffDate) {
        if (state.ready && initialTimeOffDate != null && !handledInitialDate) {
            handledInitialDate = true
            editOff = state.entries.sortedBy { if (it.kind == TimeOffKind.HOLIDAY) 0 else 1 }
                .firstOrNull { initialTimeOffDate in it.from..it.through }
            addingOff = editOff == null
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
                            DisplayFormats.date(effectiveDate, language)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            PlanRoutineCard(
                category = draft.category,
                schedule = draft.schedule,
                busy = state.busy,
                invalidInput = state.invalidInput,
                result = state.result,
                onCategoryChange = { newCategory ->
                    actions.edit { it.copy(category = newCategory) }
                },
                onDayToggle = { day ->
                    actions.edit {
                        val days = if (day in it.schedule.days) it.schedule.days - day else it.schedule.days + day
                        it.copy(
                            schedule = it.schedule.copy(
                                days = days,
                                overrides = it.schedule.overrides.filterKeys { selected -> selected in days }
                            )
                        )
                    }
                },
                onPickArrival = { timeDay = "default" },
                onGraceChange = { minutes ->
                    actions.edit {
                        it.copy(schedule = it.schedule.copy(graceMinutes = minutes))
                    }
                },
                onPickDayOverride = { day -> timeDay = day.name },
                onResetOverride = { day ->
                    actions.edit {
                        it.copy(schedule = it.schedule.copy(overrides = it.schedule.overrides - day))
                    }
                },
                onPickZone = { chooseZone = true },
                onReviewChanges = { actions.preview(draft) }
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
                category = draft.category,
                availableBalance = state.availableBalance,
                entries = state.entries,
                busy = state.busy,
                onAdjustBalance = { adjusting = true },
                onAddTimeOff = { addingOff = true },
                onEditEntry = { editOff = it }
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

    if ((addingOff || editOff != null) && draft != null) {
        val today = clock.now().toLocalDateTime(
            TimeZone.of(state.plan?.current?.schedule?.zoneId ?: draft.schedule.zoneId)
        ).date
        TimeOffEditorDialog(
            entry = editOff,
            today = initialTimeOffDate ?: today,
            category = draft.category,
            busy = state.busy,
            onDismiss = {
                addingOff = false
                editOff = null
            },
            onPreview = { command ->
                addingOff = false
                editOff = null
                actions.previewTimeOff(command)
            }
        )
    }

    val preview = state.preview ?: state.timeOffPreview
    if (preview != null) {
        PlanPreviewDialog(
            preview = preview,
            category = draft?.category,
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
            initialBalance = state.availableBalance,
            busy = state.busy,
            onSave = { text, note ->
                actions.setAvailableBalance(text, note)
                adjusting = false
            },
            onDismiss = { adjusting = false }
        )
    }
}

@Composable
internal fun PlanResult(result: PlanMutationResult?) {
    result?.let {
        Text(
            text = str(
                when (it) {
                    PlanMutationResult.Applied -> Res.string.plan_saved
                    PlanMutationResult.StalePreview -> Res.string.plan_stale
                    PlanMutationResult.InsufficientBalance -> Res.string.plan_insufficient
                    PlanMutationResult.LocalSaveFailed -> Res.string.local_save_failed
                    is PlanMutationResult.Invalid -> Res.string.plan_invalid
                }
            ),
            color = if (it == PlanMutationResult.Applied)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.error
        )
    }
}

@Preview
@Preview(name = "Narrow large text", widthDp = 320, fontScale = 2f)
@Composable
private fun PlanScreenLightPreview() {
    val actions = PlanActions({ _ -> }, { _ -> }, { _ -> }, { _ -> }, { _ -> }, {}, { _, _ -> }, {}, {})
    DaymarkTheme(ThemePreference.LIGHT) {
        PlanContent(samplePlanState(), Clock.System, null, actions)
    }
}

@Preview
@Composable
private fun PlanScreenDarkPreview() {
    val actions = PlanActions({ _ -> }, { _ -> }, { _ -> }, { _ -> }, { _ -> }, {}, { _, _ -> }, {}, {})
    DaymarkTheme(ThemePreference.DARK) {
        PlanContent(samplePlanState(), Clock.System, null, actions)
    }
}

private fun samplePlanState(): PlanUiState {
    val draft = PlanChange(
        WeeklySchedule(
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            arrival = LocalTime(9, 0),
            overrides = mapOf(DayOfWeek.FRIDAY to LocalTime(8, 30)),
            graceMinutes = 10,
            zoneId = "UTC"
        ),
        TrackerCategory.WORK,
        RegisteredPlace(37.7749, -122.4194, 150, 1)
    )
    return PlanUiState(
        ready = true,
        draft = draft,
        placeAddress = PlaceAddressUiState(
            PlaceAddressRequest(draft.place.latitude, draft.place.longitude, AppLanguage.EN),
            "Market Street, San Francisco, California, United States"
        ),
        availableBalance = 12,
        entries = listOf(
            TimeOffEntry("sample-1", TimeOffKind.VACATION, LocalDate(2026, 11, 20), LocalDate(2026, 11, 25), Instant.fromEpochMilliseconds(0)),
            TimeOffEntry("sample-2", TimeOffKind.HOLIDAY, LocalDate(2026, 12, 25), LocalDate(2026, 12, 25), Instant.fromEpochMilliseconds(0))
        )
    )
}
