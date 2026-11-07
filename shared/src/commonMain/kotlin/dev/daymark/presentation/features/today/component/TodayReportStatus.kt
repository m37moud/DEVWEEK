package dev.daymark.presentation.features.today.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.daymark.models.reporting.ReportHealth
import dev.daymark.models.reporting.SyncPassResult
import dev.daymark.resources.Res
import dev.daymark.resources.report_observed_sync
import dev.daymark.resources.report_ready
import dev.daymark.resources.today_report_attention
import dev.daymark.resources.today_report_local
import dev.daymark.resources.today_report_pending
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.str

@Composable
internal fun TodayReportStatus(
    report: ReportHealth,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val needsAttention = report.issues.isNotEmpty() || report.result in setOf(
        SyncPassResult.AuthorizationRequired,
        SyncPassResult.SchemaConflict,
        SyncPassResult.ReportUnavailable,
        SyncPassResult.ContentTooLarge
    )
    val isPending = report.pendingCount > 0 || report.busy || report.result == SyncPassResult.Pending

    val reportCopy = when {
        report.binding == null -> str(Res.string.today_report_local)
        needsAttention -> str(Res.string.today_report_attention)
        isPending -> str(
            Res.string.today_report_pending,
            DisplayFormats.number(report.pendingCount, language)
        )
        report.lastSync != null -> str(Res.string.report_observed_sync)
        else -> str(Res.string.report_ready)
    }

    val (icon, tint) = when {
        needsAttention -> Icons.Default.Warning to MaterialTheme.colorScheme.error
        isPending -> Icons.Default.Refresh to MaterialTheme.colorScheme.tertiary
        report.binding != null && report.lastSync != null -> Icons.Default.Check to MaterialTheme.colorScheme.primary
        else -> Icons.Default.Info to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = tint
            )
            Text(
                text = reportCopy,
                modifier = Modifier.testTag("today-report-state"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
