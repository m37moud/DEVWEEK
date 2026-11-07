package dev.daymark.presentation.features.today.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.daymark.models.WeekDaySummary
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.presentation.component.AttendanceLegend
import dev.daymark.presentation.component.attendanceGlyph
import dev.daymark.presentation.component.label
import dev.daymark.presentation.component.shortLabel
import dev.daymark.resources.Res
import dev.daymark.resources.not_tracked_date
import dev.daymark.resources.planned_date
import dev.daymark.resources.this_week
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.str
import kotlinx.datetime.LocalDate

@Composable
internal fun TodayWeekStrip(
    week: List<WeekDaySummary>,
    currentDate: LocalDate?,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = str(Res.string.this_week),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag("today-week"),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            week.forEach { day ->
                val isToday = day.date == currentDate
                val showPlanned = day.planned && day.status !in setOf(
                    AttendanceStatus.DAY_OFF,
                    AttendanceStatus.HOLIDAY,
                    AttendanceStatus.VACATION
                )
                val statusLabel = when {
                    showPlanned -> str(Res.string.planned_date)
                    day.status != null -> str(day.status.label())
                    else -> str(Res.string.not_tracked_date)
                }
                val cellDescription = "${DisplayFormats.date(day.date, language)}: $statusLabel"

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (isToday) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    border = if (isToday) {
                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    } else null,
                    modifier = Modifier
                        .width(62.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = cellDescription
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = str(day.date.dayOfWeek.shortLabel()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = DisplayFormats.number(day.date.day, language),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val glyphColor = when (day.status) {
                            AttendanceStatus.PRESENT -> MaterialTheme.colorScheme.primary
                            AttendanceStatus.LATE -> MaterialTheme.colorScheme.tertiary
                            AttendanceStatus.ABSENT -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            text = if (showPlanned) "○" else attendanceGlyph(day.status),
                            style = MaterialTheme.typography.bodyMedium,
                            color = glyphColor
                        )
                    }
                }
            }
        }

        AttendanceLegend()
    }
}
