package dev.daymark.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.daymark.history.AttendancePeriod
import dev.daymark.history.AttendancePeriodSettings
import dev.daymark.resources.Res
import dev.daymark.resources.attendance_period_calendar
import dev.daymark.resources.attendance_period_custom
import dev.daymark.resources.attendance_period_end_day
import dev.daymark.resources.attendance_period_example
import dev.daymark.resources.attendance_period_pick_end
import dev.daymark.resources.attendance_period_pick_start
import dev.daymark.resources.attendance_period_recurring_help
import dev.daymark.resources.attendance_period_rule
import dev.daymark.resources.attendance_period_start_day
import dev.daymark.resources.cancel
import dev.daymark.resources.confirm
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.isolated
import dev.daymark.settings.str
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

private enum class PeriodBoundary { START, END }

internal data class AttendancePeriodEditorState(
    val rule: AttendancePeriodSettings,
    val enabled: Boolean,
    val showVacationYear: Boolean
)

@Composable
internal fun AttendancePeriodEditor(
    state: AttendancePeriodEditorState,
    onChange: (AttendancePeriodSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val (rule, enabled, showVacationYear) = state
    val closingDay = rule.closingDay
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    var exampleMonth by rememberSaveable { mutableStateOf(localToday().toString()) }
    val example = rule.period(LocalDate.parse(exampleMonth))

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = closingDay == null,
                onClick = { onChange(rule.copy(closingDay = null)) },
                label = { Text(str(Res.string.attendance_period_calendar)) },
                enabled = enabled,
                modifier = Modifier.testTag("attendance-period-calendar")
            )
            FilterChip(
                selected = closingDay != null,
                onClick = { if (closingDay == null) onChange(rule.copy(closingDay = 20)) },
                label = { Text(str(Res.string.attendance_period_custom)) },
                enabled = enabled,
                modifier = Modifier.testTag("attendance-period-custom")
            )
        }
        if (closingDay != null) {
            PeriodDayButton(PeriodBoundary.START, rule.startDay, enabled) { picker = PeriodBoundary.START.name }
            PeriodDayButton(PeriodBoundary.END, closingDay, enabled) { picker = PeriodBoundary.END.name }
            Text(str(Res.string.attendance_period_recurring_help), style = MaterialTheme.typography.bodySmall)
        }
        Text(
            str(Res.string.attendance_period_example, attendancePeriodDates(example)),
            style = MaterialTheme.typography.bodyMedium
        )
        if (showVacationYear) {
            VacationYearControl(rule, example.month.year + 1, enabled, onChange)
        }
    }
    if (picker != null && closingDay != null) {
        val boundary = PeriodBoundary.valueOf(requireNotNull(picker))
        PeriodDayPicker(
            boundary = boundary,
            firstChoice = example.pickerSelection(boundary, rule),
            onDismiss = { picker = null },
            onPick = { date ->
                val updated = if (boundary == PeriodBoundary.START) rule.withStartDay(date.day)
                else rule.copy(closingDay = date.day)
                val period = if (boundary == PeriodBoundary.START) updated.periodFor(date)
                else updated.period(date)
                exampleMonth = period.month.toString()
                onChange(updated)
                picker = null
            }
        )
    }
}

@Composable
internal fun attendancePeriodRuleLabel(closingDay: Int?): String =
    if (closingDay == null) str(Res.string.attendance_period_calendar)
    else {
        val language = LocalAppLanguage.current
        str(
            Res.string.attendance_period_rule,
            isolated(DisplayFormats.number(AttendancePeriodSettings(closingDay).startDay, language)),
            isolated(DisplayFormats.number(closingDay, language))
        )
    }

@Composable
private fun PeriodDayButton(boundary: PeriodBoundary, day: Int, enabled: Boolean, onClick: () -> Unit) {
    val label = if (boundary == PeriodBoundary.START) Res.string.attendance_period_start_day
    else Res.string.attendance_period_end_day
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag("attendance-period-${boundary.name.lowercase()}")
    ) {
        Text(str(label, isolated(DisplayFormats.number(day, LocalAppLanguage.current))))
    }
}

private data class PickerSelection(val displayed: LocalDate, val selected: LocalDate?)

private fun AttendancePeriod.pickerSelection(
    boundary: PeriodBoundary,
    rule: AttendancePeriodSettings
): PickerSelection {
    val date = if (boundary == PeriodBoundary.START) from else through
    val preferredDay = if (boundary == PeriodBoundary.START) rule.startDay else rule.closingDay
    return PickerSelection(date, date.takeIf { it.day == preferredDay })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodDayPicker(
    boundary: PeriodBoundary,
    firstChoice: PickerSelection,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = firstChoice.selected?.calendarMillis(),
        initialDisplayedMonthMillis = firstChoice.displayed.calendarMillis()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let {
                        onPick(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date)
                    }
                },
                enabled = state.selectedDateMillis != null,
                modifier = Modifier.testTag("attendance-period-picker-confirm")
            ) { Text(str(Res.string.confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(Res.string.cancel)) } }
    ) {
        DatePicker(
            state = state,
            showModeToggle = false,
            title = {
                val title = if (boundary == PeriodBoundary.START) Res.string.attendance_period_pick_start
                else Res.string.attendance_period_pick_end
                Text(str(title), modifier = Modifier.fillMaxWidth())
            },
            modifier = Modifier.verticalScroll(rememberScrollState()).testTag("attendance-period-picker")
        )
    }
}

private fun LocalDate.calendarMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun localToday(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
