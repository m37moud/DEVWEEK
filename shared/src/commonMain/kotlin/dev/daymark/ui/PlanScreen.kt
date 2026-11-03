package dev.daymark.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.daymark.attendance.TrackerCategory
import dev.daymark.plan.PlanMutationResult
import dev.daymark.plan.PlanRejection
import dev.daymark.plan.TimeOffCommand
import dev.daymark.plan.TimeOffEntry
import dev.daymark.plan.TimeOffKind
import dev.daymark.plan.parseWholeDays
import dev.daymark.resources.Res
import dev.daymark.resources.add_time_off
import dev.daymark.resources.adjust_balance
import dev.daymark.resources.arrival
import dev.daymark.resources.attendance_days
import dev.daymark.resources.attendance_zone
import dev.daymark.resources.available_balance
import dev.daymark.resources.balance_help
import dev.daymark.resources.balance_note
import dev.daymark.resources.cancel
import dev.daymark.resources.edit
import dev.daymark.resources.grace
import dev.daymark.resources.load_failed
import dev.daymark.resources.local_save_failed
import dev.daymark.resources.location_rationale
import dev.daymark.resources.no_confirmed_time
import dev.daymark.resources.open_settings
import dev.daymark.resources.opening_balance
import dev.daymark.resources.plan_effective
import dev.daymark.resources.plan_insufficient
import dev.daymark.resources.plan_invalid
import dev.daymark.resources.plan_review
import dev.daymark.resources.plan_saved
import dev.daymark.resources.plan_stale
import dev.daymark.resources.plan_title
import dev.daymark.resources.preview_after
import dev.daymark.resources.preview_balance
import dev.daymark.resources.preview_before
import dev.daymark.resources.preview_evidence
import dev.daymark.resources.preview_no_dates
import dev.daymark.resources.preview_title
import dev.daymark.resources.radius
import dev.daymark.resources.register_place
import dev.daymark.resources.remove_time_off
import dev.daymark.resources.reset
import dev.daymark.resources.same_arrival
import dev.daymark.resources.save
import dev.daymark.resources.setup_type
import dev.daymark.resources.status_day_off
import dev.daymark.resources.status_holiday
import dev.daymark.resources.status_vacation
import dev.daymark.resources.time_off
import dev.daymark.resources.time_off_from
import dev.daymark.resources.time_off_through
import dev.daymark.resources.weekday_overrides
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.isolated
import dev.daymark.settings.str
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(model: PlanViewModel, clock: Clock, initialTimeOffDate: LocalDate? = null) {
    val state by model.uiState.collectAsStateWithLifecycle()
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
            .testTag("plan-root"), verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(str(Res.string.plan_title), style = MaterialTheme.typography.headlineLarge)
        if (!state.ready) CircularProgressIndicator()
        if (state.storageError) Text(
            str(Res.string.load_failed),
            color = MaterialTheme.colorScheme.error
        )
        if (draft != null) {
            state.plan?.effectiveFrom?.let {
                Text(
                    str(
                        Res.string.plan_effective,
                        DisplayFormats.date(it, language)
                    )
                )
            }
            Text(str(Res.string.setup_type), style = MaterialTheme.typography.titleMedium)
            for (category in TrackerCategory.entries) FilterChip(
                selected = draft.category == category,
                enabled = !state.busy,
                onClick = { model.edit { it.copy(category = category) } },
                label = { Text(str(category.label())) })
            Text(str(Res.string.attendance_days), style = MaterialTheme.typography.titleMedium)
            for (day in DayOfWeek.entries) FilterChip(
                selected = day in draft.schedule.days,
                enabled = !state.busy,
                modifier = Modifier.testTag("plan-day-${day.name}"),
                onClick = {
                    model.edit {
                        val days =
                            if (day in it.schedule.days) it.schedule.days - day else it.schedule.days + day
                        it.copy(
                            schedule = it.schedule.copy(
                                days = days,
                                overrides = it.schedule.overrides.filterKeys { selected -> selected in days })
                        )
                    }
                },
                label = { Text(str(day.label())) })
            OutlinedButton(
                enabled = !state.busy,
                onClick = { timeDay = "default" },
                modifier = Modifier.testTag("plan-arrival")
            ) {
                Text(
                    str(Res.string.arrival) + " · " + localTimeLabel(
                        draft.schedule.arrival,
                        language
                    )
                )
            }
            Text(
                str(Res.string.grace) + " · " + DisplayFormats.number(
                    draft.schedule.graceMinutes,
                    language
                )
            )
            Slider(
                value = draft.schedule.graceMinutes.toFloat(),
                onValueChange = { value ->
                    model.edit {
                        it.copy(
                            schedule = it.schedule.copy(graceMinutes = value.roundToInt())
                        )
                    }
                },
                valueRange = 0f..120f,
                steps = 119,
                enabled = !state.busy,
                modifier = Modifier.testTag("plan-grace")
            )
            Text(str(Res.string.weekday_overrides), style = MaterialTheme.typography.titleMedium)
            for (day in DayOfWeek.entries.filter { it in draft.schedule.days }) {
                OutlinedButton(enabled = !state.busy, onClick = { timeDay = day.name }) {
                    Text(str(day.label()) + " · " + (draft.schedule.overrides[day]?.let {
                        localTimeLabel(
                            it,
                            language
                        )
                    } ?: str(Res.string.same_arrival)))
                }
                if (day in draft.schedule.overrides) TextButton(
                    enabled = !state.busy,
                    onClick = { model.edit { it.copy(schedule = it.schedule.copy(overrides = it.schedule.overrides - day)) } }) {
                    Text(
                        str(Res.string.reset)
                    )
                }
            }
            OutlinedButton(
                enabled = !state.busy,
                onClick = { chooseZone = true },
                modifier = Modifier.testTag("plan-zone")
            ) { Text(str(Res.string.attendance_zone, isolated(draft.schedule.zoneId))) }
            Text(
                str(Res.string.radius) + " · " + DisplayFormats.number(
                    draft.place.radiusMetres,
                    language
                )
            )
            Slider(
                value = draft.place.radiusMetres.toFloat(),
                onValueChange = { value -> model.edit { it.copy(place = it.place.copy(radiusMetres = value.roundToInt())) } },
                valueRange = 100f..500f,
                steps = 7,
                enabled = !state.busy
            )
            OutlinedButton(enabled = !state.busy, onClick = model::registerCurrentPlace) {
                Text(
                    str(
                        Res.string.register_place
                    )
                )
            }
            Text(str(Res.string.location_rationale), style = MaterialTheme.typography.bodySmall)
            state.locationIssue?.let {
                Text(
                    str(it.label()),
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (state.locationIssue == SetupIssue.LOCATION_DENIED || state.locationIssue == SetupIssue.LOCATION_DISABLED) TextButton(
                onClick = model::openLocationSettings
            ) { Text(str(Res.string.open_settings)) }
            if (state.invalidInput) Text(
                str(Res.string.plan_invalid),
                color = MaterialTheme.colorScheme.error
            )
            Button(
                enabled = !state.busy,
                onClick = { model.preview(draft) },
                modifier = Modifier.fillMaxWidth().testTag("plan-review")
            ) { Text(str(Res.string.plan_review)) }
            PlanResult(state.result)
            if (draft.category == TrackerCategory.WORK) {
                Text(
                    str(
                        Res.string.available_balance,
                        DisplayFormats.number(state.availableBalance ?: 0, language)
                    ), style = MaterialTheme.typography.titleMedium
                )
                TextButton(
                    enabled = !state.busy,
                    onClick = { adjusting = true }) { Text(str(Res.string.adjust_balance)) }
            }
            Text(str(Res.string.time_off), style = MaterialTheme.typography.headlineSmall)
            Button(
                enabled = !state.busy,
                onClick = { addingOff = true },
                modifier = Modifier.testTag("plan-add-time-off")
            ) { Text(str(Res.string.add_time_off)) }
            for (entry in state.entries.sortedBy { it.from }) Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(str(if (entry.kind == TimeOffKind.HOLIDAY) Res.string.status_holiday else if (draft.category == TrackerCategory.WORK) Res.string.status_vacation else Res.string.status_day_off))
                    Text(str(Res.string.time_off_from, DisplayFormats.date(entry.from, language)))
                    Text(
                        str(
                            Res.string.time_off_through,
                            DisplayFormats.date(entry.through, language)
                        )
                    )
                    TextButton(
                        onClick = { editOff = entry },
                        enabled = !state.busy,
                        modifier = Modifier.testTag("time-off-entry-edit")
                    ) { Text(str(Res.string.edit)) }
                }
            }
        }
    }
    if (chooseZone && draft != null) ZonePicker(draft.schedule.zoneId, onSelect = { zone ->
        model.edit { it.copy(schedule = it.schedule.copy(zoneId = zone)) }; chooseZone = false
    }, onDismiss = { chooseZone = false })
    if (timeDay != null && draft != null) {
        val day = timeDay?.takeIf { it != "default" }?.let(DayOfWeek::valueOf)
        val initial = day?.let { draft.schedule.overrides[it] } ?: draft.schedule.arrival
        val time = rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { timeDay = null },
            title = { Text(str(Res.string.arrival)) },
            text = { TimePicker(time) },
            confirmButton = {
                TextButton(onClick = {
                    val selected = LocalTime(time.hour, time.minute)
                    model.edit {
                        it.copy(
                            schedule = if (day == null) it.schedule.copy(arrival = selected) else it.schedule.copy(
                                overrides = it.schedule.overrides + (day to selected)
                            )
                        )
                    }
                    timeDay = null
                }) { Text(str(Res.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    timeDay = null
                }) { Text(str(Res.string.cancel)) }
            })
    }
    if ((addingOff || editOff != null) && draft != null) {
        val today = clock.now().toLocalDateTime(
            TimeZone.of(
                state.plan?.current?.schedule?.zoneId ?: draft.schedule.zoneId
            )
        ).date
        TimeOffEditor(
            editOff,
            initialTimeOffDate ?: today,
            draft.category,
            state.busy,
            onDismiss = { addingOff = false; editOff = null },
            onPreview = { command ->
                addingOff = false; editOff = null; model.previewTimeOff(command)
            })
    }
    val preview = state.preview ?: state.timeOffPreview
    if (preview != null) {
        AlertDialog(
            onDismissRequest = model::cancelPreview,
            title = { Text(str(Res.string.preview_title)) },
            text = {
                LazyColumn(
                    Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        preview.effectiveFrom?.let {
                            Text(
                                str(
                                    Res.string.plan_effective,
                                    DisplayFormats.date(it, language)
                                )
                            )
                        }
                    }
                    if (draft?.category == TrackerCategory.WORK) item {
                        Text(
                            str(
                                Res.string.preview_balance,
                                DisplayFormats.number(preview.balanceDelta, language),
                                DisplayFormats.number(preview.availableAfter, language)
                            )
                        )
                    }
                    if (preview.rejection != null) item {
                        Text(
                            str(if (preview.rejection == PlanRejection.INSUFFICIENT_BALANCE) Res.string.plan_insufficient else Res.string.plan_invalid),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    items(preview.affectedDays.indices.toList()) { index ->
                        val key = preview.affectedDays[index]
                        val change = preview.changes.getOrNull(index)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                DisplayFormats.date(key.date, language),
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (change == null) Text(str(preview.resultingOutcomes[index].status.label())) else {
                                Text(
                                    str(
                                        Res.string.preview_before,
                                        str(change.before.status.label()),
                                        str(change.before.source.label())
                                    ), modifier = Modifier.testTag("preview-before-${key.date}")
                                )
                                Text(
                                    str(
                                        Res.string.preview_after,
                                        str(change.after.status.label()),
                                        str(change.after.source.label())
                                    ), modifier = Modifier.testTag("preview-after-${key.date}")
                                )
                                change.evidence?.let { evidence ->
                                    val time = evidence.confirmedAt?.let {
                                        DisplayFormats.time(
                                            it,
                                            change.zoneId,
                                            language
                                        )
                                    } ?: str(Res.string.no_confirmed_time)
                                    Text(
                                        str(
                                            Res.string.preview_evidence,
                                            str(evidence.status.label()),
                                            str(evidence.source.label()),
                                            time
                                        ),
                                        modifier = Modifier.testTag("preview-evidence-${key.date}"),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                    if (preview.affectedDays.isEmpty()) item { Text(str(Res.string.preview_no_dates)) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !state.busy && preview.rejection == null,
                    modifier = Modifier.testTag("preview-confirm"),
                    onClick = {
                        if (state.timeOffPreview != null) model.confirmTimeOff(preview.expectedRevision) else model.confirm(
                            preview.expectedRevision
                        )
                    }) { Text(str(Res.string.save)) }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.busy,
                    modifier = Modifier.testTag("preview-cancel"),
                    onClick = model::cancelPreview
                ) { Text(str(Res.string.cancel)) }
            })
    }
    if (adjusting) {
        var text by rememberSaveable {
            mutableStateOf(
                DisplayFormats.number(
                    state.availableBalance ?: 0, language
                )
            )
        }
        var note by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adjusting = false },
            title = { Text(str(Res.string.adjust_balance)) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        text,
                        { text = it },
                        label = { Text(str(Res.string.opening_balance)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        note,
                        { note = it },
                        label = { Text(str(Res.string.balance_note)) })
                    Text(str(Res.string.balance_help))
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !state.busy && parseWholeDays(text) != null,
                    onClick = {
                        model.setAvailableBalance(
                            text,
                            note.takeIf { it.isNotEmpty() }); adjusting = false
                    }) { Text(str(Res.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    adjusting = false
                }) { Text(str(Res.string.cancel)) }
            })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeOffEditor(
    entry: TimeOffEntry?, today: LocalDate, category: TrackerCategory, busy: Boolean,
    onDismiss: () -> Unit, onPreview: (TimeOffCommand) -> Unit
) {
    val language = LocalAppLanguage.current
    var from by remember { mutableStateOf(entry?.from ?: today) }
    var through by remember { mutableStateOf(entry?.through ?: today) }
    var kind by remember { mutableStateOf(entry?.kind ?: TimeOffKind.VACATION) }
    var picker by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(str(Res.string.time_off)) }, text = {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            for (option in TimeOffKind.entries) FilterChip(
                selected = kind == option,
                onClick = { kind = option },
                label = { Text(str(if (option == TimeOffKind.HOLIDAY) Res.string.status_holiday else if (category == TrackerCategory.WORK) Res.string.status_vacation else Res.string.status_day_off)) })
            OutlinedButton(onClick = { picker = "from" }) {
                Text(
                    str(
                        Res.string.time_off_from,
                        DisplayFormats.date(from, language)
                    )
                )
            }
            OutlinedButton(onClick = { picker = "through" }) {
                Text(
                    str(
                        Res.string.time_off_through,
                        DisplayFormats.date(through, language)
                    )
                )
            }
            if (entry != null) TextButton(
                enabled = !busy,
                onClick = { onPreview(TimeOffCommand.Remove(entry.id)) },
                modifier = Modifier.testTag("time-off-remove")
            ) { Text(str(Res.string.remove_time_off)) }
        }
    }, confirmButton = {
        TextButton(enabled = !busy, modifier = Modifier.testTag("time-off-preview"), onClick = {
            onPreview(
                if (entry == null) TimeOffCommand.Add(
                    kind,
                    from,
                    through
                ) else TimeOffCommand.Replace(entry.id, kind, from, through)
            )
        }) { Text(str(Res.string.plan_review)) }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(str(Res.string.cancel)) } })
    if (picker != null) {
        val initial = if (picker == "from") from else through
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        )
        DatePickerDialog(
            onDismissRequest = { picker = null },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { millis ->
                        val selected =
                            Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
                        if (picker == "from") from = selected else through = selected
                    }; picker = null
                }) { Text(str(Res.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    picker = null
                }) { Text(str(Res.string.cancel)) }
            }) { DatePicker(dateState) }
    }
}

@Composable
internal fun PlanResult(result: PlanMutationResult?) {
    result?.let {
        Text(
            str(
                when (it) {
                    PlanMutationResult.Applied -> Res.string.plan_saved
                    PlanMutationResult.StalePreview -> Res.string.plan_stale
                    PlanMutationResult.InsufficientBalance -> Res.string.plan_insufficient
                    PlanMutationResult.LocalSaveFailed -> Res.string.local_save_failed
                    is PlanMutationResult.Invalid -> Res.string.plan_invalid
                }
            ),
            color = if (it == PlanMutationResult.Applied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}
