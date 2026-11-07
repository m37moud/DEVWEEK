package dev.daymark.presentation.features.today.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.daymark.models.TodayDashboard
import dev.daymark.resources.Res
import dev.daymark.resources.history
import dev.daymark.resources.month_counts
import dev.daymark.resources.month_so_far
import dev.daymark.resources.status_absent
import dev.daymark.resources.status_late
import dev.daymark.resources.status_present
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.str

@Composable
internal fun TodayMonthMetrics(
    summary: TodayDashboard,
    language: AppLanguage,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val presentText = DisplayFormats.number(summary.present, language)
    val lateText = DisplayFormats.number(summary.late, language)
    val absentText = DisplayFormats.number(summary.absent, language)
    val monthCountsDescription = str(Res.string.month_counts, presentText, lateText, absentText)

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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("today-month-counts")
                .semantics(mergeDescendants = true) {
                    contentDescription = monthCountsDescription
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiTile(
                count = presentText,
                label = str(Res.string.status_present),
                dotColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            KpiTile(
                count = lateText,
                label = str(Res.string.status_late),
                dotColor = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
            KpiTile(
                count = absentText,
                label = str(Res.string.status_absent),
                dotColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun KpiTile(
    count: String,
    label: String,
    dotColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color = dotColor, shape = CircleShape)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = count,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
