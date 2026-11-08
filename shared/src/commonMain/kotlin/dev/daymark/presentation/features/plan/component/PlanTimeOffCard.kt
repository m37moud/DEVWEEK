package dev.daymark.presentation.features.plan.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.plan.TimeOffEntry
import dev.daymark.plan.TimeOffKind
import dev.daymark.resources.Res
import dev.daymark.resources.add_time_off
import dev.daymark.resources.available_balance
import dev.daymark.resources.balance_help
import dev.daymark.resources.edit
import dev.daymark.resources.preview_no_dates
import dev.daymark.resources.status_day_off
import dev.daymark.resources.status_holiday
import dev.daymark.resources.status_vacation
import dev.daymark.resources.time_off
import dev.daymark.resources.time_off_from
import dev.daymark.resources.time_off_through
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.str

@Composable
internal fun PlanTimeOffCard(
    category: TrackerCategory,
    availableBalance: Int?,
    entries: List<TimeOffEntry>,
    busy: Boolean,
    onAdjustBalance: () -> Unit,
    onAddTimeOff: () -> Unit,
    onEditEntry: (TimeOffEntry) -> Unit,
    modifier: Modifier = Modifier,
    appLanguage: AppLanguage = LocalAppLanguage.current
) {

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (category == TrackerCategory.WORK) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize()
                                .padding(28.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = str(
                                    Res.string.available_balance,
                                    DisplayFormats.number(availableBalance ?: 0, appLanguage)
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = str(Res.string.balance_help),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                        }

                        OutlinedButton(
                            onClick = onAdjustBalance,
                            enabled = !busy,
                            modifier = Modifier
                                .align(Alignment.TopEnd),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 10.dp
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
//                            Text(
//                                text = str(Res.string.adjust_balance),
//                                modifier = Modifier.padding(start = 6.dp)
//                            )
                        }

                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = str(Res.string.time_off),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
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

            if (entries.isEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = str(Res.string.preview_no_dates),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (entry in entries.sortedBy { it.from }) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Surface(
                                        shape = MaterialTheme.shapes.extraSmall,
                                        color = if (entry.kind == TimeOffKind.HOLIDAY)
                                            MaterialTheme.colorScheme.tertiaryContainer
                                        else
                                            MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = str(
                                                if (entry.kind == TimeOffKind.HOLIDAY) Res.string.status_holiday
                                                else if (category == TrackerCategory.WORK) Res.string.status_vacation
                                                else Res.string.status_day_off
                                            ),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (entry.kind == TimeOffKind.HOLIDAY)
                                                MaterialTheme.colorScheme.onTertiaryContainer
                                            else
                                                MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(
                                                horizontal = 8.dp,
                                                vertical = 2.dp
                                            )
                                        )
                                    }
                                    Text(
                                        text = str(
                                            Res.string.time_off_from,
                                            DisplayFormats.date(entry.from, appLanguage)
                                        ),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = str(
                                            Res.string.time_off_through,
                                            DisplayFormats.date(entry.through, appLanguage)
                                        ),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                TextButton(
                                    onClick = { onEditEntry(entry) },
                                    enabled = !busy,
                                    modifier = Modifier.testTag("time-off-entry-edit")
                                ) {
                                    Text(str(Res.string.edit))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
