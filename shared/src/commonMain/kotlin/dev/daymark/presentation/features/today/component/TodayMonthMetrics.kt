package dev.daymark.presentation.features.today.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.daymark.history.HistoryMetric
import dev.daymark.history.HistoryMetrics
import dev.daymark.presentation.component.HistoryMetricsGrid
import dev.daymark.resources.Res
import dev.daymark.resources.history
import dev.daymark.resources.month_so_far
import dev.daymark.settings.str

@Composable
internal fun TodayMonthMetrics(
    metrics: HistoryMetrics,
    onHistory: () -> Unit,
    onMetric: (HistoryMetric) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = str(Res.string.month_so_far),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = onHistory) {
                Text(
                    text = str(Res.string.history),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        HistoryMetricsGrid(metrics, onMetric, Modifier.testTag("today-month-counts"))
    }
}
