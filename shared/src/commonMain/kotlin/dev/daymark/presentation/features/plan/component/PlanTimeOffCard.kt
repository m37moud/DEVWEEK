package dev.daymark.presentation.features.plan.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.plan.TimeOffEntry
import dev.daymark.plan.TimeOffKind
import dev.daymark.presentation.component.label
import dev.daymark.resources.Res
import dev.daymark.resources.add_time_off
import dev.daymark.resources.edit
import dev.daymark.resources.time_off_empty
import dev.daymark.resources.time_off_from
import dev.daymark.resources.time_off_through
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.isolated
import dev.daymark.settings.str
import dev.daymark.ui.theme.DaymarkTheme
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

@Composable
internal fun PlanTimeOffCard(
    category: TrackerCategory,
    entries: List<TimeOffEntry>,
    busy: Boolean,
    onAddTimeOff: () -> Unit,
    onEditEntry: (TimeOffEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 16.dp, horizontal = 18.dp)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PlanTimeOffHeader(
                busy = busy,
                onAddTimeOff = onAddTimeOff
            )

            AnimatedContent(
                targetState = entries.isEmpty(),
                transitionSpec = {
                    fadeIn(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ).togetherWith(
                        fadeOut(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        )
                    ).using(SizeTransform(clip = false))
                },
                label = "time-off-entries-content"
            ) { isEmpty ->
                if (isEmpty) {
                    PlanTimeOffEmptyState()
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (entry in entries.sortedBy { it.from }) {
                            PlanTimeOffEntryItem(
                                entry = entry,
                                category = category,
                                busy = busy,
                                onEdit = { onEditEntry(entry) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanTimeOffHeader(
    busy: Boolean,
    onAddTimeOff: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Button(
            onClick = onAddTimeOff,
            enabled = !busy,
            modifier = Modifier.testTag("plan-add-time-off")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = str(Res.string.add_time_off),
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

@Composable
private fun PlanTimeOffEntryItem(
    entry: TimeOffEntry,
    category: TrackerCategory,
    busy: Boolean,
    onEdit: () -> Unit
) {
    val language = LocalAppLanguage.current
    val isSingleDay = entry.from == entry.through
    val daysCount = (entry.through.toEpochDays() - entry.from.toEpochDays() + 1).coerceAtLeast(1).toInt()
    val isHoliday = entry.kind == TimeOffKind.HOLIDAY

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = !busy, onClick = onEdit),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = if (isHoliday) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    ) {
                        Text(
                            text = str(entry.kind.label(category)),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isHoliday) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    if (!isSingleDay) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = isolated(DisplayFormats.number(daysCount, language)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                TextButton(
                    onClick = onEdit,
                    enabled = !busy,
                    modifier = Modifier.testTag("time-off-entry-edit")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp).padding(end = 4.dp)
                    )
                    Text(
                        text = str(Res.string.edit),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            entry.note?.let { note ->
                Text(isolated(note), style = MaterialTheme.typography.bodyMedium)
            }

            if (isSingleDay) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = DisplayFormats.date(entry.from, language),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = str(
                            Res.string.time_off_from,
                            DisplayFormats.date(entry.from, language)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = str(
                            Res.string.time_off_through,
                            DisplayFormats.date(entry.through, language)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanTimeOffEmptyState(
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Text(
                text = str(Res.string.time_off_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(widthDp = 320, fontScale = 2f)
@Composable
private fun PlanTimeOffCardWorkPreview() {
    val sampleEntries = listOf(
        TimeOffEntry(
            id = "sample-1",
            kind = TimeOffKind.VACATION,
            from = LocalDate(2026, 11, 20),
            through = LocalDate(2026, 11, 24),
            updatedAt = Instant.fromEpochMilliseconds(0)
        ),
        TimeOffEntry(
            id = "sample-2",
            kind = TimeOffKind.HOLIDAY,
            from = LocalDate(2026, 12, 25),
            through = LocalDate(2026, 12, 25),
            updatedAt = Instant.fromEpochMilliseconds(0)
        )
    )
    DaymarkTheme(preference = ThemePreference.LIGHT) {
        PlanTimeOffCard(
            category = TrackerCategory.WORK,
            entries = sampleEntries,
            busy = false,
            onAddTimeOff = {},
            onEditEntry = {}
        )
    }
}

@Preview
@Composable
private fun PlanTimeOffCardEmptyDarkPreview() {
    DaymarkTheme(preference = ThemePreference.DARK) {
        PlanTimeOffCard(
            category = TrackerCategory.WORK,
            entries = emptyList(),
            busy = false,
            onAddTimeOff = {},
            onEditEntry = {}
        )
    }
}
