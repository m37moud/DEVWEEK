package dev.daymark.presentation.features.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.daymark.history.HistoryMetric
import dev.daymark.history.HistoryRange
import dev.daymark.history.lateDuration
import dev.daymark.models.attendance.Correction
import dev.daymark.models.attendance.HistoryRecord
import dev.daymark.presentation.component.metricLabel
import dev.daymark.presentation.component.lateDurationLabel
import dev.daymark.presentation.features.history.component.*
import dev.daymark.resources.*
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.str
import dev.daymark.ui.theme.DaymarkTheme
import kotlinx.datetime.LocalDate

private data class HistoryDetailActions(
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
        state,
        HistoryDetailActions(onBack, model::selectRange, model::saveCorrection, onEditTimeOff)
    )
}

@Composable
private fun HistoryDetailContent(state: HistoryDetailUiState, actions: HistoryDetailActions) {
    var editing by remember { mutableStateOf<HistoryRecord?>(null) }
    var offDate by remember { mutableStateOf<LocalDate?>(null) }
    val language = LocalAppLanguage.current
    LaunchedEffect(state.savedKey) {
        if (editing?.plan?.key == state.savedKey) editing = null
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().testTag("history-detail-root"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TextButton(onClick = actions.back) { Text(str(Res.string.back)) }
            Text(str(state.metric.metricLabel()), style = MaterialTheme.typography.headlineMedium)
        }
        item {
            HistoryRangeFilter(state, actions.selectRange)
        }
        item { HistoryDetailSummary(state) }
        if (state.storageError) item {
            Text(
                str(Res.string.load_failed),
                color = MaterialTheme.colorScheme.error
            )
        }
        if (!state.ready) item { CircularProgressIndicator() }
        else if (state.records.isEmpty() && !state.storageError) item { HistoryEmptyState() }
        items(state.records, key = { it.plan.key.date.toString() }) { record ->
            HistoryDetailRow(record, state, { selected ->
                if (!selected.plan.isScheduled || selected.outcome.offReason != null) offDate =
                    selected.plan.key.date
                else editing = selected
            }, Modifier.animateItem())
        }
    }
    offDate?.let { date ->
        HistoryTimeOffDialog(date, actions.timeOff) { offDate = null }
    }
    editing?.let { record ->
        key(record.plan.key) {
            HistoryCorrectionDialog(
                record = record, busy = state.busy, noteTooLong = state.noteTooLong,
                invalidCorrection = state.invalidCorrection, storageError = state.storageError,
                language = language, onSaveCorrection = actions.correct,
                onEditTimeOff = actions.timeOff, onDismiss = { if (!state.busy) editing = null }
            )
        }
    }
}

@Composable
private fun HistoryDetailSummary(state: HistoryDetailUiState) {
    val language = LocalAppLanguage.current
    val metrics = state.metrics
    val count = when (state.metric) {
        HistoryMetric.ATTENDANCE -> metrics.attendance
        HistoryMetric.LATE -> metrics.late
        HistoryMetric.ABSENT -> metrics.absent
        HistoryMetric.LEAVE_REST -> metrics.leaveRest
        HistoryMetric.HOLIDAY -> metrics.holidays
    }
    Text(
        str(Res.string.history_elapsed_days, DisplayFormats.number(count, language)),
        style = MaterialTheme.typography.titleMedium
    )
    if (state.metric == HistoryMetric.LATE) {
        Text(lateDurationLabel(metrics.lateDuration))
        Text(str(Res.string.history_late_basis), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun HistoryDetailRow(
    record: HistoryRecord,
    state: HistoryDetailUiState,
    onSelect: (HistoryRecord) -> Unit,
    modifier: Modifier
) {
    val language = LocalAppLanguage.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        val date = record.plan.key.date
        Text(DisplayFormats.date(date, language), style = MaterialTheme.typography.titleSmall)
        Text(
            str(
                Res.string.history_scheduled_arrival,
                DisplayFormats.time(record.plan.arrival, record.plan.zoneId, language)
            ),
            style = MaterialTheme.typography.bodySmall
        )
        if (state.metric == HistoryMetric.LATE) Text(lateDurationLabel(record.lateDuration()))
        HistoryRecordCard(record, language, onSelect, { onSelect(record) })
    }
}

@Preview
@Composable
private fun HistoryHolidayDetailPreview() {
    val from = LocalDate(2026, 11, 1)
    DaymarkTheme(ThemePreference.LIGHT) {
        HistoryDetailContent(
            HistoryDetailUiState(
                HistoryMetric.HOLIDAY,
                from,
                LocalDate(2026, 11, 30),
                HistoryRange(
                    listOf(
                        previewLateRecord().copy(
                            outcome = dev.daymark.models.attendance.DayOutcome(
                                dev.daymark.models.attendance.AttendanceStatus.HOLIDAY,
                                dev.daymark.models.attendance.AttendanceSource.MANUAL_TIME_OFF,
                                null, null
                            )
                        )
                    ), from, LocalDate(2026, 11, 15)
                ),
                ready = true
            ),
            HistoryDetailActions({}, { _, _ -> }, {}, {})
        )
    }
}

private fun previewLateRecord(): HistoryRecord {
    val arrival = kotlin.time.Instant.parse("2026-11-10T09:00:00Z")
    val confirmed = kotlin.time.Instant.parse("2026-11-10T09:10:00Z")
    val plan = dev.daymark.models.attendance.DayPlan(
        dev.daymark.models.attendance.DayKey("preview", LocalDate(2026, 11, 10)),
        dev.daymark.models.attendance.TrackerCategory.WORK, arrival, arrival, "UTC", 1,
        dev.daymark.models.attendance.RegisteredPlace(0.0, 0.0, 100, 1), true
    )
    val evidence = dev.daymark.models.attendance.AttendanceEvidence(
        dev.daymark.models.attendance.AttendanceStatus.LATE,
        dev.daymark.models.attendance.AttendanceSource.MANUAL_CORRECTION, confirmed, null, confirmed
    )
    return HistoryRecord(
        plan, evidence, dev.daymark.models.attendance.DayOutcome(
            evidence.status, evidence.source, confirmed, null
        )
    )
}
