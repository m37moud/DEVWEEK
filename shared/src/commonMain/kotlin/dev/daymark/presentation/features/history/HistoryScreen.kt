package dev.daymark.presentation.features.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.daymark.models.attendance.AttendanceEvidence
import dev.daymark.models.attendance.AttendanceSource
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.models.attendance.Correction
import dev.daymark.models.attendance.DayKey
import dev.daymark.models.attendance.DayOutcome
import dev.daymark.models.attendance.DayPlan
import dev.daymark.models.attendance.HistoryMonth
import dev.daymark.models.attendance.HistoryRecord
import dev.daymark.models.attendance.RegisteredPlace
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.presentation.features.history.component.HistoryCalendarCard
import dev.daymark.presentation.features.history.component.HistoryCorrectionDialog
import dev.daymark.presentation.features.history.component.HistoryEmptyState
import dev.daymark.presentation.features.history.component.HistoryFilterBanner
import dev.daymark.presentation.features.history.component.HistoryHeader
import dev.daymark.presentation.features.history.component.HistoryMonthNavigator
import dev.daymark.presentation.features.history.component.HistoryRecordCard
import dev.daymark.presentation.features.history.component.HistoryTimeOffDialog
import dev.daymark.resources.Res
import dev.daymark.resources.load_failed
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.str
import dev.daymark.ui.theme.DaymarkTheme
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

private data class HistoryActions(
    val selectMonth: (Int, Int) -> Unit,
    val saveCorrection: (Correction) -> Unit
)

@Composable
fun HistoryScreen(
    model: HistoryViewModel,
    onBack: () -> Unit,
    onEditTimeOff: (LocalDate) -> Unit
) {
    val state by model.uiState.collectAsStateWithLifecycle()
    LifecycleStartEffect(model) {
        model.setActive(true)
        onStopOrDispose { model.setActive(false) }
    }
    val actions = remember(model) {
        HistoryActions(model::selectMonth, model::saveCorrection)
    }
    HistoryContent(state, onBack, onEditTimeOff, actions)
}

@Composable
private fun HistoryContent(
    state: HistoryUiState,
    onBack: () -> Unit,
    onEditTimeOff: (LocalDate) -> Unit,
    actions: HistoryActions
) {
    val language = LocalAppLanguage.current
    var editing by remember { mutableStateOf<HistoryRecord?>(null) }
    var offDate by remember { mutableStateOf<LocalDate?>(null) }

    LaunchedEffect(state.savedKey) {
        if (editing?.plan?.key == state.savedKey) {
            editing = null
        }
    }

    var selectedDate by rememberSaveable(state.year, state.month) { mutableStateOf<String?>(null) }
    val selected = selectedDate?.let(LocalDate::parse)
    val shown = state.history.records.filter { selected == null || it.plan.key.date == selected }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .testTag("history-root"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(contentType = "HistoryHeader") {
            HistoryHeader()
        }

        item(contentType = "HistoryMonthNavigator") {
            HistoryMonthNavigator(
                year = state.year,
                month = state.month,
                history = state.history,
                language = language,
                onSelectMonth = actions.selectMonth
            )
        }

        item(contentType = "HistoryCalendarCard") {
            HistoryCalendarCard(
                year = state.year,
                month = state.month,
                records = state.history.records,
                today = state.today,
                selected = selected,
                onSelect = { selectedDate = it.toString() }
            )
        }

        item(contentType = "HistoryFilterBanner") {
            HistoryFilterBanner(
                selected = selected,
                today = state.today,
                records = state.history.records,
                language = language,
                onClearFilter = { selectedDate = null },
                onEditTimeOff = onEditTimeOff
            )
        }

        if (state.storageError) {
            item(contentType = "HistoryStorageError") {
                Text(
                    text = str(Res.string.load_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (!state.ready) {
            item(contentType = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (shown.isEmpty() && (selected == null || selected <= state.today)) {
            item(contentType = "HistoryEmptyState") {
                HistoryEmptyState()
            }
        }

        items(
            items = shown,
            key = { it.plan.key.date.toString() },
            contentType = { _ -> "HistoryRecordCard" }) { record ->
            HistoryRecordCard(
                record = record,
                language = language,
                onEditRecord = { editing = it },
                onSelectOffDate = { offDate = it },
                modifier = Modifier.animateItem()
            )
        }
    }

    offDate?.let { date ->
        HistoryTimeOffDialog(
            date = date,
            onEditTimeOff = onEditTimeOff,
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
                onSaveCorrection = actions.saveCorrection,
                onEditTimeOff = onEditTimeOff,
                onDismiss = { if (!state.busy) editing = null }
            )
        }
    }
}

@Preview
@Composable
private fun HistoryScreenPreview() {
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
                note = "Arrived on time",
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
    val sampleState = HistoryUiState(
        year = 2026,
        month = 11,
        history = HistoryMonth(sampleRecords, present = 1, late = 1, absent = 1),
        ready = true,
        today = sampleDate
    )
    val actions = HistoryActions({ _, _ -> }, { _ -> })

    DaymarkTheme(ThemePreference.LIGHT) {
        HistoryContent(
            state = sampleState,
            onBack = {},
            onEditTimeOff = {},
            actions = actions
        )
    }
}

@Preview
@Composable
private fun HistoryScreenDarkPreview() {
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
                note = "Arrived on time",
                updatedAt = instant
            ),
            outcome = DayOutcome(
                status = AttendanceStatus.PRESENT,
                source = AttendanceSource.MANUAL_GPS,
                confirmedAt = instant,
                offReason = null
            )
        )
    )
    val sampleState = HistoryUiState(
        year = 2026,
        month = 11,
        history = HistoryMonth(sampleRecords, present = 1, late = 0, absent = 0),
        ready = true,
        today = sampleDate
    )
    val actions = HistoryActions({ _, _ -> }, { _ -> })

    DaymarkTheme(ThemePreference.DARK) {
        HistoryContent(
            state = sampleState,
            onBack = {},
            onEditTimeOff = {},
            actions = actions
        )
    }
}
