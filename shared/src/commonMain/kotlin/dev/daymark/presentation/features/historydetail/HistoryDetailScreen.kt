package dev.daymark.presentation.features.historydetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.daymark.history.HistoryMetric
import dev.daymark.history.HistoryRange
import dev.daymark.models.attendance.Correction
import dev.daymark.models.attendance.HistoryRecord
import dev.daymark.presentation.features.history.component.HistoryCorrectionDialog
import dev.daymark.presentation.features.history.component.HistoryEmptyState
import dev.daymark.presentation.features.history.component.HistoryTimeOffDialog
import dev.daymark.presentation.features.historydetail.component.HistoryDetailHeader
import dev.daymark.presentation.features.historydetail.component.HistoryDetailHeroCard
import dev.daymark.presentation.features.historydetail.component.HistoryDetailRow
import dev.daymark.presentation.features.historydetail.component.HistoryRangeFilter
import dev.daymark.resources.Res
import dev.daymark.resources.load_failed
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.str
import dev.daymark.ui.theme.DaymarkTheme
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

data class HistoryDetailActions(
    val back: () -> Unit,
    val selectRange: (LocalDate, LocalDate) -> Unit,
    val correct: (Correction) -> Unit,
    val timeOff: (LocalDate) -> Unit
)

@Composable
fun HistoryDetailScreen(
    model: HistoryDetailViewModel,
    onBack: () -> Unit,
    onEditTimeOff: (LocalDate) -> Unit
) {
    val state by model.uiState.collectAsStateWithLifecycle()
    LifecycleStartEffect(model) {
        model.setActive(true)
        onStopOrDispose { model.setActive(false) }
    }
    HistoryDetailContent(
        state = state,
        actions = HistoryDetailActions(onBack, model::selectRange, model::saveCorrection, onEditTimeOff)
    )
}

@Composable
fun HistoryDetailContent(
    state: HistoryDetailUiState,
    actions: HistoryDetailActions
) {
    var editing by remember { mutableStateOf<HistoryRecord?>(null) }
    var offDate by remember { mutableStateOf<LocalDate?>(null) }
    val language = LocalAppLanguage.current

    LaunchedEffect(state.savedKey) {
        if (editing?.plan?.key == state.savedKey) editing = null
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .testTag("history-detail-root"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HistoryDetailHeader(
                metric = state.metric,
                onBack = actions.back
            )
        }

        item {
            HistoryDetailHeroCard(state = state)
        }

        item {
            HistoryRangeFilter(
                state = state,
                onSelect = actions.selectRange
            )
        }

        item {
            AnimatedVisibility(
                visible = state.storageError,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = str(Res.string.load_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        if (!state.ready) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (state.records.isEmpty() && !state.storageError) {
            item {
                HistoryEmptyState()
            }
        } else {
            items(state.records, key = { it.plan.key.date.toString() }) { record ->
                HistoryDetailRow(
                    record = record,
                    state = state,
                    onSelect = { selected ->
                        if (!selected.plan.isScheduled || selected.outcome.offReason != null) {
                            offDate = selected.plan.key.date
                        } else {
                            editing = selected
                        }
                    },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }

    offDate?.let { date ->
        HistoryTimeOffDialog(
            date = date,
            onEditTimeOff = actions.timeOff,
            onDismiss = { offDate = null }
        )
    }

    editing?.let { record ->
        key(record.plan.key) {
            HistoryCorrectionDialog(
                record = record,
                busy = state.busy,
                noteTooLong = state.noteTooLong,
                invalidCorrection = state.invalidCorrection,
                storageError = state.storageError,
                language = language,
                onSaveCorrection = actions.correct,
                onEditTimeOff = actions.timeOff,
                onDismiss = { if (!state.busy) editing = null }
            )
        }
    }
}

@Preview
@Composable
private fun HistoryHolidayDetailPreview() {
    val from = LocalDate(2026, 11, 1)
    DaymarkTheme(ThemePreference.LIGHT) {
        HistoryDetailContent(
            state = HistoryDetailUiState(
                metric = HistoryMetric.HOLIDAY,
                from = from,
                through = LocalDate(2026, 11, 30),
                range = HistoryRange(
                    records = listOf(
                        previewLateRecord().copy(
                            outcome = dev.daymark.models.attendance.DayOutcome(
                                dev.daymark.models.attendance.AttendanceStatus.HOLIDAY,
                                dev.daymark.models.attendance.AttendanceSource.MANUAL_TIME_OFF,
                                null, null
                            )
                        )
                    ),
                    firstDate = from,
                    today = LocalDate(2026, 11, 15)
                ),
                ready = true
            ),
            actions = HistoryDetailActions({}, { _, _ -> }, {}, {})
        )
    }
}

@Preview
@Composable
private fun HistoryLateDetailDarkPreview() {
    val from = LocalDate(2026, 11, 1)
    DaymarkTheme(ThemePreference.DARK) {
        HistoryDetailContent(
            state = HistoryDetailUiState(
                metric = HistoryMetric.LATE,
                from = from,
                through = LocalDate(2026, 11, 30),
                range = HistoryRange(
                    records = listOf(previewLateRecord()),
                    firstDate = from,
                    today = LocalDate(2026, 11, 15)
                ),
                ready = true
            ),
            actions = HistoryDetailActions({}, { _, _ -> }, {}, {})
        )
    }
}

@Preview
@Composable
private fun HistoryAttendanceDetailArabicPreview() {
    val from = LocalDate(2026, 11, 1)
    CompositionLocalProvider(LocalAppLanguage provides AppLanguage.AR) {
        DaymarkTheme(ThemePreference.LIGHT) {
            HistoryDetailContent(
                state = HistoryDetailUiState(
                    metric = HistoryMetric.ATTENDANCE,
                    from = from,
                    through = LocalDate(2026, 11, 30),
                    range = HistoryRange(
                        records = listOf(previewLateRecord()),
                        firstDate = from,
                        today = LocalDate(2026, 11, 15)
                    ),
                    ready = true
                ),
                actions = HistoryDetailActions({}, { _, _ -> }, {}, {})
            )
        }
    }
}

@Preview(name = "Narrow large text", widthDp = 320, fontScale = 2f)
@Composable
private fun HistoryNarrowTextPreview() {
    val from = LocalDate(2026, 11, 1)
    DaymarkTheme(ThemePreference.LIGHT) {
        HistoryDetailContent(
            state = HistoryDetailUiState(
                metric = HistoryMetric.ATTENDANCE,
                from = from,
                through = LocalDate(2026, 11, 30),
                range = HistoryRange(
                    records = listOf(previewLateRecord()),
                    firstDate = from,
                    today = LocalDate(2026, 11, 15)
                ),
                ready = true
            ),
            actions = HistoryDetailActions({}, { _, _ -> }, {}, {})
        )
    }
}

private fun previewLateRecord(): HistoryRecord {
    val arrival = Instant.parse("2026-11-10T09:00:00Z")
    val confirmed = Instant.parse("2026-11-10T09:10:00Z")
    val plan = dev.daymark.models.attendance.DayPlan(
        dev.daymark.models.attendance.DayKey("preview", LocalDate(2026, 11, 10)),
        dev.daymark.models.attendance.TrackerCategory.WORK,
        arrival,
        arrival,
        "UTC",
        1,
        dev.daymark.models.attendance.RegisteredPlace(0.0, 0.0, 100, 1),
        true
    )
    val evidence = dev.daymark.models.attendance.AttendanceEvidence(
        dev.daymark.models.attendance.AttendanceStatus.LATE,
        dev.daymark.models.attendance.AttendanceSource.MANUAL_CORRECTION,
        confirmed,
        null,
        confirmed
    )
    return HistoryRecord(
        plan = plan,
        evidence = evidence,
        outcome = dev.daymark.models.attendance.DayOutcome(
            evidence.status,
            evidence.source,
            confirmed,
            null
        )
    )
}
