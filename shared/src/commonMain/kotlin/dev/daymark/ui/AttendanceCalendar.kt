package dev.daymark.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.daymark.attendance.*
import dev.daymark.resources.*
import dev.daymark.settings.*
import kotlinx.datetime.*
import org.jetbrains.compose.resources.StringResource

internal fun attendanceGlyph(status: AttendanceStatus?): String = when (status) {
    AttendanceStatus.PRESENT -> "✓"
    AttendanceStatus.LATE -> "!"
    AttendanceStatus.ABSENT -> "×"
    AttendanceStatus.HOLIDAY,AttendanceStatus.VACATION,AttendanceStatus.DAY_OFF -> "—"
    AttendanceStatus.AWAITING_CHECK -> "…"
    AttendanceStatus.SCHEDULED -> "○"
    null -> "·"
}
internal fun DayOfWeek.shortLabel(): StringResource = when (this) {
    DayOfWeek.MONDAY -> Res.string.mon_short; DayOfWeek.TUESDAY -> Res.string.tue_short
    DayOfWeek.WEDNESDAY -> Res.string.wed_short; DayOfWeek.THURSDAY -> Res.string.thu_short
    DayOfWeek.FRIDAY -> Res.string.fri_short; DayOfWeek.SATURDAY -> Res.string.sat_short
    DayOfWeek.SUNDAY -> Res.string.sun_short
}
@Composable internal fun AttendanceLegend() {
    Text(str(Res.string.attendance_legend),style=MaterialTheme.typography.bodySmall)
}
@Composable internal fun AttendanceCalendar(year: Int,month: Int,records: List<HistoryRecord>,today: LocalDate,
    selected: LocalDate?,onSelect: (LocalDate)->Unit) {
    val language=LocalAppLanguage.current
    val byDate=records.associateBy { it.plan.key.date }
    Column(Modifier.fillMaxWidth().testTag("history-calendar"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cell=maxOf(48.dp,maxWidth/7)
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                Row { DayOfWeek.entries.forEach { day -> Box(Modifier.width(cell).padding(4.dp)) { Text(str(day.shortLabel()),style=MaterialTheme.typography.labelSmall) } } }
                calendarWeeks(year,month).forEach { week -> Row {
                    week.forEach { date ->
                        if (date==null) Spacer(Modifier.width(cell)) else {
                            val planned=date>today && byDate[date]==null
                            val status=byDate[date]?.outcome?.status
                            val label=if (planned) str(Res.string.planned_date) else status?.let { str(it.label()) } ?: str(Res.string.not_tracked_date)
                            val description="${DisplayFormats.date(date,language)}: $label"
                            TextButton(onClick={ onSelect(date) },modifier=Modifier.width(cell).heightIn(min=64.dp)
                                .testTag("calendar-date-$date").semantics { contentDescription=description },
                                contentPadding=PaddingValues(2.dp),colors=ButtonDefaults.textButtonColors(
                                    containerColor=if (date==selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
                                Column { Text(DisplayFormats.number(date.day,language)); Text(if (planned) "○" else attendanceGlyph(status),style=MaterialTheme.typography.labelMedium) }
                            }
                        }
                    }
                } }
            }
        }
        AttendanceLegend()
    }
}
