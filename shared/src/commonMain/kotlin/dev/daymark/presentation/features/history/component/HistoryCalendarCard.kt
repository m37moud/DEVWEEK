package dev.daymark.presentation.features.history.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.daymark.models.attendance.AttendanceEvidence
import dev.daymark.models.attendance.AttendanceSource
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.models.attendance.DayKey
import dev.daymark.models.attendance.DayOutcome
import dev.daymark.models.attendance.DayPlan
import dev.daymark.models.attendance.HistoryRecord
import dev.daymark.models.attendance.RegisteredPlace
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.presentation.component.calendarWeeks
import dev.daymark.presentation.component.label
import dev.daymark.presentation.component.shortLabel
import dev.daymark.resources.Res
import dev.daymark.resources.not_tracked_date
import dev.daymark.resources.planned_date
import dev.daymark.resources.status_absent
import dev.daymark.resources.status_day_off
import dev.daymark.resources.status_late
import dev.daymark.resources.status_present
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.str
import dev.daymark.ui.theme.DaymarkTheme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

@Composable
internal fun HistoryCalendarCard(
    year: Int,
    month: Int,
    records: List<HistoryRecord>,
    today: LocalDate,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val language = LocalAppLanguage.current
    val byDate = remember(records) { records.associateBy { it.plan.key.date } }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("history-calendar"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Weekday Headers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DayOfWeek.entries.forEach { day ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = str(day.shortLabel()),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Directional animated calendar grid
            AnimatedContent(
                targetState = Pair(year, month),
                transitionSpec = {
                    val targetDate = LocalDate(targetState.first, targetState.second, 1)
                    val initialDate = LocalDate(initialState.first, initialState.second, 1)
                    if (targetDate > initialDate) {
                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> -width } + fadeOut()
                        )
                    } else {
                        (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> width } + fadeOut()
                        )
                    }
                },
                label = "calendarMonthTransition"
            ) { (gridYear, gridMonth) ->
                val weeks = remember(gridYear, gridMonth) { calendarWeeks(gridYear, gridMonth) }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    weeks.forEach { week ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            week.forEach { date ->
                                if (date == null) {
                                    Spacer(modifier = Modifier.weight(1f))
                                } else {
                                    CalendarDayCell(
                                        date = date,
                                        record = byDate[date],
                                        today = today,
                                        isSelected = date == selected,
                                        language = language,
                                        onSelect = onSelect,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Divider & Visual Legend Strip
            HorizontalDivider(
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LegendBadge(
                    label = str(Res.string.status_present),
                    dotColor = MaterialTheme.colorScheme.primary
                )
                LegendBadge(
                    label = str(Res.string.status_late),
                    dotColor = MaterialTheme.colorScheme.tertiary
                )
                LegendBadge(
                    label = str(Res.string.status_absent),
                    dotColor = MaterialTheme.colorScheme.error
                )
                LegendBadge(
                    label = str(Res.string.status_day_off),
                    dotColor = MaterialTheme.colorScheme.secondary
                )
                LegendBadge(
                    label = str(Res.string.planned_date),
                    dotColor = MaterialTheme.colorScheme.outlineVariant,
                    isHollow = true
                )
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    record: HistoryRecord?,
    today: LocalDate,
    isSelected: Boolean,
    language: AppLanguage,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val isToday = date == today
    val planned = date > today && record == null
    val status = record?.outcome?.status

    val label = if (planned) {
        str(Res.string.planned_date)
    } else {
        status?.let { str(it.label()) } ?: str(Res.string.not_tracked_date)
    }
    val description = "${DisplayFormats.date(date, language)}: $label"

    val (dotColor, showBorder) = when {
        status == AttendanceStatus.PRESENT -> MaterialTheme.colorScheme.primary to false
        status == AttendanceStatus.LATE -> MaterialTheme.colorScheme.tertiary to false
        status == AttendanceStatus.ABSENT -> MaterialTheme.colorScheme.error to false
        status == AttendanceStatus.HOLIDAY ||
                status == AttendanceStatus.VACATION ||
                status == AttendanceStatus.DAY_OFF -> MaterialTheme.colorScheme.secondary to false

        planned -> MaterialTheme.colorScheme.outlineVariant to true
        else -> Color.Transparent to false
    }

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "cellScale"
    )

    val containerColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            isToday -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> Color.Transparent
        },
        label = "cellContainerColor"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
            isToday -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        },
        label = "cellContentColor"
    )

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .testTag("calendar-date-$date")
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = containerColor,
            border = if (isToday && !isSelected) {
                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            } else null,
            modifier = Modifier
                .width(42.dp)
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onSelect(date) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = DisplayFormats.number(date.day, language),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (dotColor != Color.Transparent) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .then(
                                if (showBorder) {
                                    Modifier.border(1.dp, dotColor, CircleShape)
                                } else {
                                    Modifier.background(dotColor, CircleShape)
                                }
                            )
                    )
                } else {
                    Spacer(modifier = Modifier.size(5.dp))
                }
            }
        }
    }
}

@Composable
private fun LegendBadge(
    label: String,
    dotColor: Color,
    isHollow: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .then(
                    if (isHollow) {
                        Modifier.border(1.2.dp, dotColor, CircleShape)
                    } else {
                        Modifier.background(dotColor, CircleShape)
                    }
                )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview
@Preview(name = "Narrow large text", widthDp = 320, fontScale = 2f)
@Composable
private fun HistoryCalendarCardPreview() {
    val sampleDate = LocalDate(2026, 11, 7)
    val place = RegisteredPlace(0.0, 0.0, 100, 1)
    val dayKey = DayKey("test-tracker", sampleDate)
    val instant = Instant.fromEpochMilliseconds(1794042000000)
    val dayPlan = DayPlan(
        key = dayKey,
        category = TrackerCategory.WORK,
        arrival = instant,
        deadline = instant,
        zoneId = "UTC",
        scheduleVersion = 1,
        place = place,
        isScheduled = true
    )
    val sampleRecords = listOf(
        HistoryRecord(
            plan = dayPlan,
            evidence = AttendanceEvidence(
                status = AttendanceStatus.PRESENT,
                source = AttendanceSource.MANUAL_GPS,
                confirmedAt = instant,
                note = null,
                updatedAt = instant
            ),
            outcome = DayOutcome(
                status = AttendanceStatus.PRESENT,
                source = AttendanceSource.MANUAL_GPS,
                confirmedAt = instant,
                offReason = null
            )
        ),
        HistoryRecord(
            plan = dayPlan.copy(key = DayKey("test-tracker", LocalDate(2026, 11, 6))),
            evidence = AttendanceEvidence(
                status = AttendanceStatus.LATE,
                source = AttendanceSource.MANUAL_GPS,
                confirmedAt = instant,
                note = null,
                updatedAt = instant
            ),
            outcome = DayOutcome(
                status = AttendanceStatus.LATE,
                source = AttendanceSource.MANUAL_GPS,
                confirmedAt = instant,
                offReason = null
            )
        ),
        HistoryRecord(
            plan = dayPlan.copy(key = DayKey("test-tracker", LocalDate(2026, 11, 5))),
            evidence = null,
            outcome = DayOutcome(
                status = AttendanceStatus.ABSENT,
                source = AttendanceSource.NONE,
                confirmedAt = null,
                offReason = null
            )
        )
    )

    DaymarkTheme(ThemePreference.LIGHT) {
        HistoryCalendarCard(
            year = 2026,
            month = 11,
            records = sampleRecords,
            today = sampleDate,
            selected = sampleDate,
            onSelect = {}
        )
    }
}

@Preview
@Composable
private fun HistoryCalendarCardDarkPreview() {
    val sampleDate = LocalDate(2026, 11, 7)
    DaymarkTheme(ThemePreference.DARK) {
        HistoryCalendarCard(
            year = 2026,
            month = 11,
            records = emptyList(),
            today = sampleDate,
            selected = null,
            onSelect = {}
        )
    }
}
