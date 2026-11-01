package dev.daymark.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.daymark.app.AppGraph
import dev.daymark.app.HostPlatform
import dev.daymark.attendance.TrackerCategory
import dev.daymark.plan.*
import dev.daymark.resources.*
import dev.daymark.settings.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.*
import org.jetbrains.compose.resources.StringResource
import kotlin.math.roundToInt

@Composable fun PreferenceControls(graph: AppGraph, settings: AppSettings) {
    val scope = rememberCoroutineScope()
    var failed by remember { mutableStateOf(false) }
    fun update(language: LanguagePreference? = null, theme: ThemePreference? = null) { scope.launch {
        try { graph.settings.update(language = language, theme = theme); failed = false }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    } }
    if (failed) Text(str(Res.string.local_save_failed), color = MaterialTheme.colorScheme.error)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(LanguagePreference.SYSTEM to Res.string.language_system, LanguagePreference.EN to Res.string.language_english,
            LanguagePreference.AR to Res.string.language_arabic).forEach { (choice, label) ->
            FilterChip(selected = settings.language == choice, onClick = { update(language = choice) },
                label = { Text(str(label)) }, modifier = Modifier.testTag("language-${choice.name.lowercase()}"))
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(ThemePreference.SYSTEM to Res.string.theme_system, ThemePreference.LIGHT to Res.string.theme_light,
            ThemePreference.DARK to Res.string.theme_dark).forEach { (choice, label) ->
            FilterChip(selected = settings.theme == choice, onClick = { update(theme = choice) },
                label = { Text(str(label)) }, modifier = Modifier.testTag("theme-${choice.name.lowercase()}"))
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SetupScreen(model: SetupViewModel, graph: AppGraph, settings: AppSettings) {
    val state by model.uiState.collectAsStateWithLifecycle()
    var chooseZone by remember { mutableStateOf(false) }
    val draft = state.draft
    val language = LocalAppLanguage.current
    val scope = rememberCoroutineScope()
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    var reminderAllowed by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val schedule = draft.schedule
    val keyboard = LocalSoftwareKeyboardController.current
    val errorPosition = remember { BringIntoViewRequester() }
    LaunchedEffect(state.issue) {
        if (state.issue != null) { withFrameNanos { }; errorPosition.bringIntoView() }
    }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp).testTag("setup-form"),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(str(Res.string.app_name), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        PreferenceControls(graph, settings)
        Text(str(Res.string.setup_title), style = MaterialTheme.typography.headlineLarge)
        Text(str(Res.string.setup_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!state.ready) { CircularProgressIndicator(); return@Column }
        OutlinedTextField(draft.displayName, { model.updateDraft(draft.copy(displayName = it)) }, label = { Text(str(Res.string.setup_name)) },
            singleLine = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("setup-name"))
        if (dev.daymark.reporting.exceedsReportTextLimit(draft.displayName)) Text(str(Res.string.profile_too_long,DisplayFormats.number(dev.daymark.reporting.REPORT_TEXT_LIMIT,LocalAppLanguage.current)),color=MaterialTheme.colorScheme.error)
        Text(str(Res.string.setup_type), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrackerCategory.entries.forEach { category -> FilterChip(selected = category == draft.category, enabled = !state.busy,
                onClick = { model.updateDraft(draft.copy(category = category)) }, label = { Text(str(category.label())) }) }
        }
        OutlinedTextField(draft.placeName, { model.updateDraft(draft.copy(placeName = it)) }, label = { Text(str(Res.string.setup_place_name)) },
            singleLine = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("setup-place-name"))
        Text(str(Res.string.location_rationale), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { keyboard?.hide(); model.useCurrentLocation() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("setup-register-place")) {
            Text(str(if (state.busy) Res.string.checking_location else Res.string.register_place))
        }
        state.issue?.takeIf { it.isLocationIssue() }?.let { issue ->
            Text(str(issue.label()), color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("setup-error").bringIntoViewRequester(errorPosition))
            if (issue == SetupIssue.LOCATION_DENIED) TextButton(onClick = graph.permissions::openSystemSettings) { Text(str(Res.string.open_settings)) }
        }
        draft.place?.let { place ->
            Text(str(Res.string.place_ready, isolated(DisplayFormats.number(place.radiusMetres, language))),
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("setup-place-ready"))
            Text(str(Res.string.radius), style = MaterialTheme.typography.titleSmall)
            Slider(place.radiusMetres.toFloat(), { model.updateDraft(draft.copy(place = place.copy(radiusMetres = it.roundToInt()))) },
                enabled = !state.busy, valueRange = 100f..500f, steps = 7)
        }
        if (schedule != null) {
            HorizontalDivider()
            Text(str(Res.string.schedule), style = MaterialTheme.typography.titleLarge)
            Text(str(Res.string.attendance_days), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { day -> FilterChip(selected = day in schedule.days, enabled = !state.busy,
                    onClick = {
                        val days = if (day in schedule.days) schedule.days - day else schedule.days + day
                        if (days.isNotEmpty()) model.updateDraft(draft.copy(schedule = schedule.copy(days = days, overrides = schedule.overrides.filterKeys { it in days })))
                    }, label = { Text(str(day.label())) }) }
            }
            OutlinedButton(onClick = { picker = "default" }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(str(Res.string.arrival) + " · " + localTimeLabel(schedule.arrival, language))
            }
            Text(str(Res.string.grace) + " · " + plural(Res.plurals.grace_minutes, schedule.graceMinutes, isolated(DisplayFormats.number(schedule.graceMinutes, language))))
            Slider(schedule.graceMinutes.toFloat(), { model.updateDraft(draft.copy(schedule = schedule.copy(graceMinutes = it.roundToInt()))) },
                enabled = !state.busy, valueRange = 0f..120f, steps = 119)
            Text(str(Res.string.weekday_overrides), style = MaterialTheme.typography.titleSmall)
            schedule.days.sortedBy { it.ordinal }.forEach { day ->
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                    OutlinedButton(onClick = { picker = day.name }, enabled = !state.busy) {
                        Text(str(day.label()) + " · " + (schedule.overrides[day]?.let { localTimeLabel(it, language) } ?: str(Res.string.same_arrival)))
                    }
                    if (day in schedule.overrides) TextButton(onClick = { model.updateDraft(draft.copy(schedule = schedule.copy(overrides = schedule.overrides - day))) }, enabled = !state.busy) {
                        Text(str(Res.string.reset))
                    }
                }
            }
            OutlinedButton(enabled = !state.busy,onClick = { chooseZone = true }) { Text(str(Res.string.attendance_zone, isolated(schedule.zoneId))) }
        }
        if (draft.category == TrackerCategory.WORK) {
            OutlinedTextField(draft.openingBalanceText, { model.updateDraft(draft.copy(openingBalanceText = it)) },
                label = { Text(str(Res.string.opening_balance)) }, singleLine = true, enabled = !state.busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Text(str(Res.string.balance_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(str(Res.string.google_optional), style = MaterialTheme.typography.bodyMedium)
        if (graph.platform == HostPlatform.IOS) {
            TextButton(onClick = { graph.reminders.requestPermission { allowed -> scope.launch {
                reminderAllowed = allowed
                if (allowed) try { graph.settings.update(remindersEnabled = true) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { reminderAllowed = false }
            } } }, enabled = !state.busy) {
                Text(str(Res.string.reminders_optional))
            }
            reminderAllowed?.let { Text(str(if (it) Res.string.reminders_allowed else Res.string.reminders_denied), style = MaterialTheme.typography.bodySmall) }
        }
        if (state.draftSaved) Text(str(Res.string.draft_saved), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("setup-draft-saved"))
        state.issue?.takeUnless { it.isLocationIssue() }?.let { Text(str(it.label()), color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("setup-error").bringIntoViewRequester(errorPosition)) }
        Button(onClick = { keyboard?.hide(); model.submit() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("setup-start")) { Text(str(Res.string.start_tracking)) }
        Spacer(Modifier.height(16.dp))
    }
    if (chooseZone && schedule != null) ZonePicker(schedule.zoneId,onSelect = { zone ->
        model.updateDraft(draft.copy(schedule = schedule.copy(zoneId = zone))); chooseZone = false
    },onDismiss = { chooseZone = false })
    if (picker != null && schedule != null) {
        val day = picker?.takeIf { it != "default" }?.let(DayOfWeek::valueOf)
        val initial = day?.let { schedule.overrides[it] } ?: schedule.arrival
        key(picker) {
            val time = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
            AlertDialog(onDismissRequest = { picker = null }, title = { Text(str(Res.string.arrival)) }, text = { TimePicker(time) },
                confirmButton = { TextButton(onClick = {
                    val selected = LocalTime(time.hour, time.minute)
                    model.updateDraft(draft.copy(schedule = if(day == null) schedule.copy(arrival = selected) else schedule.copy(overrides = schedule.overrides + (day to selected))))
                    picker = null
                }) { Text(str(Res.string.save)) } }, dismissButton = { TextButton(onClick = { picker = null }) { Text(str(Res.string.cancel)) } })
        }
    }
}
internal fun TrackerCategory.label(): StringResource = when(this) { TrackerCategory.WORK -> Res.string.work; TrackerCategory.SCHOOL -> Res.string.school; TrackerCategory.COURSE -> Res.string.course }
internal fun DayOfWeek.label(): StringResource = when(this) {
    DayOfWeek.MONDAY -> Res.string.monday; DayOfWeek.TUESDAY -> Res.string.tuesday; DayOfWeek.WEDNESDAY -> Res.string.wednesday
    DayOfWeek.THURSDAY -> Res.string.thursday; DayOfWeek.FRIDAY -> Res.string.friday; DayOfWeek.SATURDAY -> Res.string.saturday; DayOfWeek.SUNDAY -> Res.string.sunday
}
internal fun SetupIssue.label(): StringResource = when(this) {
    SetupIssue.INVALID_INPUT -> Res.string.invalid_setup; SetupIssue.LOCATION_DENIED -> Res.string.location_denied
    SetupIssue.LOCATION_DISABLED -> Res.string.location_disabled; SetupIssue.LOCATION_TIMEOUT -> Res.string.location_timeout
    SetupIssue.LOCATION_UNAVAILABLE -> Res.string.location_unavailable; SetupIssue.LOCATION_STALE -> Res.string.location_stale
    SetupIssue.LOCATION_INACCURATE -> Res.string.location_inaccurate; SetupIssue.LOCAL_SAVE_FAILED -> Res.string.local_save_failed
}

internal fun localTimeLabel(time: LocalTime, language: AppLanguage): String {
    val zero = DisplayFormats.number(0, language).single()
    return isolated(DisplayFormats.number(time.hour, language).padStart(2, zero) + ":" + DisplayFormats.number(time.minute, language).padStart(2, zero))
}

private fun SetupIssue.isLocationIssue() = this != SetupIssue.INVALID_INPUT && this != SetupIssue.LOCAL_SAVE_FAILED
