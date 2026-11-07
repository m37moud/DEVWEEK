package dev.daymark.presentation.features.today.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.presentation.component.attendanceGlyph
import dev.daymark.presentation.component.label
import dev.daymark.presentation.features.today.TodayUiState
import dev.daymark.resources.Res
import dev.daymark.resources.arrival_at
import dev.daymark.resources.attendance_zone
import dev.daymark.resources.check_attendance
import dev.daymark.resources.checking_attendance
import dev.daymark.resources.confirmed_at
import dev.daymark.resources.deadline_at
import dev.daymark.resources.history
import dev.daymark.resources.today_place
import dev.daymark.resources.tracking_not_started
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.isolated
import dev.daymark.settings.str

@Composable
internal fun TodayHeroCard(
    state: TodayUiState,
    summaryReady: Boolean,
    language: AppLanguage,
    onCheckAttendance: () -> Unit,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val outcome = state.outcome
    val status = outcome?.status
    val cardBorder = when (status) {
        AttendanceStatus.PRESENT -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        AttendanceStatus.LATE -> BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))
        AttendanceStatus.ABSENT -> BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = cardBorder
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!state.ready || !summaryReady) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    outcome?.let {
                        val (badgeBg, badgeFg) = when (status) {
                            AttendanceStatus.PRESENT -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                            AttendanceStatus.LATE -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                            AttendanceStatus.ABSENT -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                            AttendanceStatus.DAY_OFF, AttendanceStatus.HOLIDAY, AttendanceStatus.VACATION ->
                                MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = badgeBg,
                            contentColor = badgeFg
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = attendanceGlyph(status),
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    text = str(it.status.label()),
                                    modifier = Modifier.testTag("today-status"),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }

                        Text(
                            text = str(it.source.label()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } ?: Text(
                        text = str(Res.string.tracking_not_started),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedContent(
                    targetState = outcome?.confirmedAt,
                    label = "HeroConfirmedState"
                ) { confirmedTime ->
                    if (confirmedTime != null && state.plan != null) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = str(
                                        Res.string.confirmed_at,
                                        DisplayFormats.time(confirmedTime, state.plan.zoneId, language)
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    } else if (state.plan != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TimeMetricCard(
                                    text = str(
                                        Res.string.arrival_at,
                                        DisplayFormats.time(state.plan.arrival, state.plan.zoneId, language)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                TimeMetricCard(
                                    text = str(
                                        Res.string.deadline_at,
                                        DisplayFormats.time(state.plan.deadline, state.plan.zoneId, language)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Text(
                                text = str(Res.string.attendance_zone, isolated(state.plan.zoneId)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                state.dashboard.takeIf { it.ready && it.placeName.isNotEmpty() }?.let { summary ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = str(Res.string.today_place, isolated(summary.placeName)),
                            modifier = Modifier.testTag("today-place"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = onCheckAttendance,
                    enabled = state.canCheck && summaryReady,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("today-check")
                ) {
                    if (state.busy) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(str(Res.string.checking_attendance))
                        }
                    } else {
                        Text(str(Res.string.check_attendance))
                    }
                }

                OutlinedButton(
                    onClick = onHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("today-history")
                ) {
                    Text(str(Res.string.history))
                }
            }
        }
    }
}

@Composable
private fun TimeMetricCard(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
