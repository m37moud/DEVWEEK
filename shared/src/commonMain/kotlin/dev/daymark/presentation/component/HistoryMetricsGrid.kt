package dev.daymark.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.daymark.history.HistoryMetric
import dev.daymark.history.HistoryMetrics
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.str
import dev.daymark.ui.theme.DaymarkTheme

@Composable
 fun HistoryMetricsGrid(
    metrics: HistoryMetrics,
    onMetric: (HistoryMetric) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (pair in HistoryMetric.entries.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (metric in pair) {
                    MonthKpiTile(
                        metric = metric,
                        metrics = metrics,
                        onClick = { onMetric(metric) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
 fun MonthKpiTile(
    metric: HistoryMetric,
    metrics: HistoryMetrics,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    appLanguage: AppLanguage = LocalAppLanguage.current,
) {
    val count = when (metric) {
        HistoryMetric.ATTENDANCE -> metrics.attendance
        HistoryMetric.LATE -> metrics.late
        HistoryMetric.ABSENT -> metrics.absent
        HistoryMetric.LEAVE_REST -> metrics.leaveRest
        HistoryMetric.HOLIDAY -> metrics.holidays
    }
    val detail = if (metric == HistoryMetric.LATE) lateDurationLabel(metrics.lateDuration)
    else null
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp).testTag("metric-${metric.name}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(str(metric.metricLabel()), style = MaterialTheme.typography.labelMedium)
            Text(
                DisplayFormats.number(count, appLanguage),
                style = MaterialTheme.typography.titleLarge
            )
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

