package dev.daymark.presentation.features.plan.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.daymark.presentation.component.calendarWeeks
import dev.daymark.presentation.component.shortLabel
import dev.daymark.resources.Res
import dev.daymark.resources.next_month
import dev.daymark.resources.previous_month
import dev.daymark.resources.time_off_selected_days
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.str
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus

@Composable
internal fun SeparateDaysCalendar(
    initialDate: LocalDate,
    selectedDates: Set<LocalDate>,
    onToggle: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val language = LocalAppLanguage.current
    var displayedMonth by rememberSaveable {
        mutableStateOf(LocalDate(initialDate.year, initialDate.month, 1).toString())
    }
    val month = LocalDate.parse(displayedMonth)
    val weeks = remember(month) { calendarWeeks(month.year, month.month.number) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { displayedMonth = month.minus(1, DateTimeUnit.MONTH).toString() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, str(Res.string.previous_month))
            }
            Text(DisplayFormats.monthYear(month, language), Modifier.weight(1f))
            IconButton(onClick = { displayedMonth = month.plus(1, DateTimeUnit.MONTH).toString() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, str(Res.string.next_month))
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cellWidth = maxOf(48.dp, maxWidth / 7)
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                Row {
                    DayOfWeek.entries.forEach { day ->
                        Box(Modifier.width(cellWidth), contentAlignment = Alignment.Center) {
                            Text(str(day.shortLabel()), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                weeks.forEach { week ->
                    Row {
                        week.forEach { date ->
                            val cell = Modifier.width(cellWidth).heightIn(min = 48.dp)
                            if (date == null) Box(cell) else {
                                SelectableDate(date, date in selectedDates, onToggle, cell)
                            }
                        }
                    }
                }
            }
        }
        Text(str(Res.string.time_off_selected_days, DisplayFormats.number(selectedDates.size, language)))
    }
}

@Composable
private fun SelectableDate(
    date: LocalDate,
    selected: Boolean,
    onToggle: (LocalDate) -> Unit,
    modifier: Modifier
) {
    val language = LocalAppLanguage.current
    val dateLabel = DisplayFormats.date(date, language)
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .testTag("time-off-select-$date")
            .semantics { contentDescription = dateLabel }
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle(date) }),
        shape = MaterialTheme.shapes.small,
        color = if (selected) colors.secondaryContainer else colors.surface,
        contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(DisplayFormats.number(date.day, language), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
