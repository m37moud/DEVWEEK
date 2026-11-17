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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.daymark.models.attendance.Correction
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
import dev.daymark.settings.str
import kotlinx.datetime.LocalDate

data class HistoryDetailActions(
    val back: () -> Unit,
    val selectRange: (LocalDate, LocalDate) -> Unit,
    val correct: (Correction) -> Unit,
    val timeOff: (LocalDate) -> Unit,
    val beginCorrection: () -> Unit
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
        actions = HistoryDetailActions(
            onBack,
            model::selectRange,
            model::saveCorrection,
            onEditTimeOff,
            model::beginCorrection
        )
    )
}

@Composable
fun HistoryDetailContent(
    state: HistoryDetailUiState,
    actions: HistoryDetailActions,
    appLanguage: AppLanguage = LocalAppLanguage.current
) {
    var editingDate by rememberSaveable { mutableStateOf<String?>(null) }
    var offDate by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = editingDate?.let { date ->
        state.range.records.firstOrNull { it.plan.key.date.toString() == date }
    }

    LaunchedEffect(state.savedKey) {
        if (editingDate == state.savedKey?.date?.toString()) editingDate = null
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
                            offDate = selected.plan.key.date.toString()
                        } else {
                            actions.beginCorrection()
                            editingDate = selected.plan.key.date.toString()
                        }
                    },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }

    offDate?.let { date ->
        HistoryTimeOffDialog(
            date = LocalDate.parse(date),
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
                language = appLanguage,
                onSaveCorrection = actions.correct,
                onEditTimeOff = actions.timeOff,
                onDismiss = { if (!state.busy) editingDate = null }
            )
        }
    }
}
