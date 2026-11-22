package dev.daymark.presentation.features.historydetail.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.history.HistoryMetric
import dev.daymark.history.lateDuration
import dev.daymark.models.attendance.HistoryRecord
import dev.daymark.presentation.component.lateDurationLabel
import dev.daymark.presentation.features.history.component.HistoryRecordCard
import dev.daymark.presentation.features.historydetail.HistoryDetailUiState
import dev.daymark.resources.Res
import dev.daymark.resources.history_scheduled_arrival
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.str

@Composable
internal fun HistoryDetailRow(
    record: HistoryRecord,
    state: HistoryDetailUiState,
    onSelect: (HistoryRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val language = LocalAppLanguage.current
    val date = record.plan.key.date
    val isMission = record.outcome.status == AttendanceStatus.WORK_MISSION
    val isLate = state.metric == HistoryMetric.LATE

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = DisplayFormats.date(date, language),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!isMission) {
                    Text("•", color = MaterialTheme.colorScheme.outlineVariant)
                    val arrivalTime = DisplayFormats.time(record.plan.arrival, record.plan.zoneId, language)
                    Text(
                        text = str(Res.string.history_scheduled_arrival, arrivalTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isLate) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = lateDurationLabel(record.lateDuration()),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
        HistoryRecordCard(
            record = record,
            language = language,
            onEditRecord = onSelect,
            onSelectOffDate = { onSelect(record) }
        )
    }
}
