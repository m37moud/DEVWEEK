package dev.daymark.presentation.features.settings.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Google
import compose.icons.fontawesomeicons.solid.Info
import dev.daymark.models.reporting.ConnectionResult
import dev.daymark.models.reporting.ProjectionKey
import dev.daymark.models.reporting.ReportBinding
import dev.daymark.models.reporting.ReportHealth
import dev.daymark.models.reporting.SyncPassResult
import dev.daymark.presentation.features.settings.SettingsUiState
import dev.daymark.reporting.ReportField
import dev.daymark.reporting.ReportIssue
import dev.daymark.resources.Res
import dev.daymark.resources.cancel
import dev.daymark.resources.confirm
import dev.daymark.resources.correction_note
import dev.daymark.resources.delete_local_confirmation
import dev.daymark.resources.delete_local_data
import dev.daymark.resources.delete_local_failed
import dev.daymark.resources.edit_record
import dev.daymark.resources.google_report
import dev.daymark.resources.info
import dev.daymark.resources.profile_name
import dev.daymark.resources.report_auth_required
import dev.daymark.resources.report_choice
import dev.daymark.resources.report_connect
import dev.daymark.resources.report_configuration_required
import dev.daymark.resources.report_connection_failed
import dev.daymark.resources.report_consent_cancelled
import dev.daymark.resources.report_content_too_long
import dev.daymark.resources.report_creation_pending
import dev.daymark.resources.report_disconnect
import dev.daymark.resources.report_disconnect_confirmation
import dev.daymark.resources.report_disconnected
import dev.daymark.resources.report_field_too_long
import dev.daymark.resources.report_last_sync
import dev.daymark.resources.report_local_authority
import dev.daymark.resources.report_observed_sync
import dev.daymark.resources.report_open
import dev.daymark.resources.report_pending
import dev.daymark.resources.report_pending_count
import dev.daymark.resources.report_ready
import dev.daymark.resources.report_rebuild
import dev.daymark.resources.report_rebuild_confirmation
import dev.daymark.resources.report_reconnect
import dev.daymark.resources.report_replacement
import dev.daymark.resources.report_replacement_confirmation
import dev.daymark.resources.report_schema_conflict
import dev.daymark.resources.report_select
import dev.daymark.resources.report_unavailable
import dev.daymark.resources.retry
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.isolated
import dev.daymark.settings.str
import dev.daymark.ui.theme.DaymarkTheme
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Instant

internal data class ReportActions(
    val connectReport: () -> Unit,
    val reconnectReport: () -> Unit,
    val selectReport: (String) -> Unit,
    val rebuildReport: () -> Unit,
    val createReplacementReport: () -> Unit,
    val disconnectReport: () -> Unit,
    val retryReport: () -> Unit,
    val deleteLocalData: () -> Unit
)

@Composable
internal fun GoogleConnectionSection(
    state: SettingsUiState,
    actions: ReportActions,
    modifier: Modifier = Modifier
) {
    val health = state.report
    val busy = state.reportBusy
    val deleting = state.deleting
    val uri = LocalUriHandler.current
    val language = LocalAppLanguage.current
    var confirmChoice by remember { mutableStateOf<String?>(null) }
    var showInfo by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("google-section"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ReportHeader(
                onToggleInfo = { showInfo = !showInfo }
            )

            ReportInfoBanner(visible = showInfo)

            AnimatedVisibility(
                visible = busy || deleting || health.busy,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            AnimatedContent(
                targetState = health.binding != null,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ReportConnectionContent"
            ) { isConnected ->
                if (isConnected && health.binding != null) {
                    val reportState = ConnectedReportState(
                        binding = health.binding,
                        health = health,
                        interactive = !busy && !deleting,
                        language = language
                    )
                    val reportCallbacks = ConnectedReportCallbacks(
                        onOpenSpreadsheet = { id ->
                            uri.openUri("https://docs.google.com/spreadsheets/d/$id/edit")
                        },
                        onRetry = actions.retryReport,
                        onReconnect = actions.reconnectReport,
                        onRebuild = { confirmChoice = "rebuild" },
                        onDisconnect = { confirmChoice = "disconnect" }
                    )
                    ConnectedReportContent(
                        state = reportState,
                        callbacks = reportCallbacks
                    )
                } else {
                    DisconnectedReportContent(
                        busy = busy,
                        deleting = deleting,
                        onConnect = actions.connectReport
                    )
                }
            }

            ReportIssuesList(issues = health.issues, language = language)

            ReportConnectionResultNotice(
                result = if (state.reportActionFailed) {
                    ConnectionResult.Failed("connection_failed")
                } else {
                    health.connectionResult
                },
                busy = busy,
                language = language,
                onSelectReport = actions.selectReport
            )

            ReportFooterActions(
                interactive = !busy && !deleting,
                deletionFailed = state.deletionFailed,
                onRequestReplacement = { confirmChoice = "replacement" },
                onRequestDeleteLocal = { confirmChoice = "delete" }
            )
        }
    }

    confirmChoice?.let { choice ->
        ReportConfirmationDialog(
            choice = choice,
            onDismiss = { confirmChoice = null },
            onConfirm = {
                confirmChoice = null
                when (choice) {
                    "delete" -> actions.deleteLocalData()
                    "rebuild" -> actions.rebuildReport()
                    "replacement" -> actions.createReplacementReport()
                    else -> actions.disconnectReport()
                }
            }
        )
    }
}

@Composable
private fun ReportHeader(
    onToggleInfo: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = str(Res.string.google_report),
            style = MaterialTheme.typography.titleLarge
        )
        IconButton(
            onClick = onToggleInfo,
            modifier = Modifier.size(24.dp),
            shape = IconButtonDefaults.outlinedShape,
            colors = IconButtonDefaults.outlinedIconButtonColors()
        ) {
            Icon(
                imageVector = FontAwesomeIcons.Solid.Info,
                contentDescription = str(Res.string.info),
                modifier = Modifier.size(12.dp).testTag("google-info")
            )
        }
    }
}

@Composable
private fun ReportInfoBanner(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = str(Res.string.report_local_authority),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

private data class ConnectedReportState(
    val binding: ReportBinding,
    val health: ReportHealth,
    val interactive: Boolean,
    val language: AppLanguage
)

private data class ConnectedReportCallbacks(
    val onOpenSpreadsheet: (String) -> Unit,
    val onRetry: () -> Unit,
    val onReconnect: () -> Unit,
    val onRebuild: () -> Unit,
    val onDisconnect: () -> Unit
)

@Composable
private fun ConnectedReportContent(
    state: ConnectedReportState,
    callbacks: ConnectedReportCallbacks
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        ConnectedReportStatusBadge(state.health.result)

        ReportSyncMetricsRow(
            pendingCount = state.health.pendingCount,
            lastSync = state.health.lastSync,
            language = state.language
        )

        ConnectedReportQuickActions(
            spreadsheetId = state.binding.spreadsheetId,
            interactive = state.interactive,
            onOpenSpreadsheet = callbacks.onOpenSpreadsheet,
            onRetry = callbacks.onRetry
        )

        ConnectedReportManagementActions(
            interactive = state.interactive,
            onReconnect = callbacks.onReconnect,
            onRebuild = callbacks.onRebuild,
            onDisconnect = callbacks.onDisconnect
        )
    }
}

private data class StatusVisuals(
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
    val messageRes: StringResource
)

@Composable
private fun resolveStatusVisuals(result: SyncPassResult): StatusVisuals {
    val isError = result in setOf(
        SyncPassResult.AuthorizationRequired,
        SyncPassResult.SchemaConflict,
        SyncPassResult.ReportUnavailable,
        SyncPassResult.ContentTooLarge
    )
    val isPending = result == SyncPassResult.Pending
    val isSynced = result == SyncPassResult.Synced

    val containerColor = when {
        isError -> MaterialTheme.colorScheme.errorContainer
        isPending -> MaterialTheme.colorScheme.tertiaryContainer
        isSynced -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = when {
        isError -> MaterialTheme.colorScheme.onErrorContainer
        isPending -> MaterialTheme.colorScheme.onTertiaryContainer
        isSynced -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when {
        isError -> Icons.Default.Warning
        isPending -> Icons.Default.Refresh
        isSynced -> Icons.Default.Check
        else -> Icons.Default.Info
    }
    val messageRes = when (result) {
        SyncPassResult.AuthorizationRequired -> Res.string.report_auth_required
        SyncPassResult.SchemaConflict -> Res.string.report_schema_conflict
        SyncPassResult.ReportUnavailable -> Res.string.report_unavailable
        SyncPassResult.ContentTooLarge -> Res.string.report_content_too_long
        SyncPassResult.Pending -> Res.string.report_pending
        SyncPassResult.Synced -> Res.string.report_observed_sync
        else -> Res.string.report_ready
    }
    return StatusVisuals(icon, containerColor, contentColor, messageRes)
}

@Composable
private fun ConnectedReportStatusBadge(result: SyncPassResult) {
    val visuals = resolveStatusVisuals(result)
    val animatedBg by animateColorAsState(visuals.containerColor)
    val animatedContent by animateColorAsState(visuals.contentColor)

    Surface(
        color = animatedBg,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = visuals.icon,
                contentDescription = null,
                tint = animatedContent,
                modifier = Modifier.size(18.dp)
            )
            AnimatedContent(
                targetState = visuals.messageRes,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.weight(1f)
            ) { textRes ->
                Text(
                    text = str(textRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = animatedContent
                )
            }
        }
    }
}

@Composable
private fun ReportSyncMetricsRow(
    pendingCount: Int,
    lastSync: String?,
    language: AppLanguage
) {
    val pendingText = str(
        Res.string.report_pending_count,
        DisplayFormats.number(pendingCount, language)
    )
    val lastSyncText = lastSync?.let { raw ->
        runCatching { Instant.parse(raw) }.getOrNull()?.let { at ->
            val zone = TimeZone.currentSystemDefault()
            str(
                Res.string.report_last_sync,
                DisplayFormats.date(at.toLocalDateTime(zone).date, language),
                DisplayFormats.time(at, zone.id, language)
            )
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        MetricTile(
            icon = Icons.Default.Refresh,
            text = pendingText
        )
        if (lastSyncText != null) {
            MetricTile(
                icon = Icons.Default.DateRange,
                text = lastSyncText
            )
        }
    }
}

@Composable
private fun MetricTile(
    icon: ImageVector,
    text: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConnectedReportQuickActions(
    spreadsheetId: String,
    interactive: Boolean,
    onOpenSpreadsheet: (String) -> Unit,
    onRetry: () -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilledTonalButton(
            enabled = interactive,
            onClick = { onOpenSpreadsheet(spreadsheetId) }
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(str(Res.string.report_open), modifier = Modifier.weight(1f, fill = false))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        OutlinedButton(
            enabled = interactive,
            onClick = onRetry,
            modifier = Modifier.testTag("report-retry")
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Text(str(Res.string.retry), modifier = Modifier.weight(1f, fill = false))
            }
        }
    }
}

@Composable
private fun ConnectedReportManagementActions(
    interactive: Boolean,
    onReconnect: () -> Unit,
    onRebuild: () -> Unit,
    onDisconnect: () -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedButton(
            enabled = interactive,
            onClick = onReconnect,
            modifier = Modifier.testTag("report-reconnect")
        ) {
            Text(str(Res.string.report_reconnect))
        }
        OutlinedButton(
            enabled = interactive,
            onClick = onRebuild
        ) {
            Text(str(Res.string.report_rebuild))
        }
        OutlinedButton(
            enabled = interactive,
            onClick = onDisconnect,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
            modifier = Modifier.testTag("report-disconnect")
        ) {
            Text(str(Res.string.report_disconnect))
        }
    }
}

@Composable
private fun DisconnectedReportContent(
    busy: Boolean,
    deleting: Boolean,
    onConnect: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = str(Res.string.report_disconnected),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            enabled = !busy && !deleting,
            onClick = onConnect,
            modifier = Modifier.testTag("report-connect")
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(str(Res.string.report_connect))
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp).testTag("report-connect-loading"),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = FontAwesomeIcons.Brands.Google,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportIssuesList(
    issues: List<ReportIssue>,
    language: AppLanguage
) {
    if (issues.isEmpty()) return
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        issues.forEach { issue ->
            val label = str(
                when (issue.field) {
                    ReportField.NOTE -> Res.string.correction_note
                    ReportField.PROFILE_NAME -> Res.string.profile_name
                    ReportField.RECORD -> Res.string.edit_record
                }
            )
            val date = (issue.key as? ProjectionKey.Day)?.date?.let {
                DisplayFormats.date(it, language)
            }.orEmpty()
            Text(
                text = str(Res.string.report_field_too_long, label, date),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("report-content-error")
            )
        }
    }
}

@Composable
private fun ReportConnectionResultNotice(
    result: ConnectionResult?,
    busy: Boolean,
    language: AppLanguage,
    onSelectReport: (String) -> Unit
) {
    when (result) {
        ConnectionResult.PendingCreation -> {
            Text(
                text = str(Res.string.report_creation_pending),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        is ConnectionResult.SelectionRequired -> {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = str(Res.string.report_select),
                    style = MaterialTheme.typography.bodyMedium
                )
                result.reports.forEachIndexed { index, report ->
                    TextButton(
                        enabled = !busy,
                        onClick = { onSelectReport(report.id) }
                    ) {
                        Text(
                            str(
                                Res.string.report_choice,
                                DisplayFormats.number(index + 1, language),
                                isolated(report.id)
                            )
                        )
                    }
                }
            }
        }
        ConnectionResult.AuthorizationRequired -> {
            Text(
                text = str(Res.string.report_auth_required),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        ConnectionResult.Cancelled -> {
            Text(
                text = str(Res.string.report_consent_cancelled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        is ConnectionResult.Failed -> {
            val errorRes = if (result.errorKey == "authorization_configuration") {
                Res.string.report_configuration_required
            } else {
                Res.string.report_connection_failed
            }
            Text(
                text = str(errorRes),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        else -> Unit
    }
}

@Composable
private fun ReportFooterActions(
    interactive: Boolean,
    deletionFailed: Boolean,
    onRequestReplacement: () -> Unit,
    onRequestDeleteLocal: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        TextButton(
            enabled = interactive,
            onClick = onRequestReplacement
        ) {
            Text(str(Res.string.report_replacement))
        }

        HorizontalDivider()

        TextButton(
            enabled = interactive,
            onClick = onRequestDeleteLocal,
            modifier = Modifier.testTag("delete-local-data")
        ) {
            Text(
                text = str(Res.string.delete_local_data),
                color = MaterialTheme.colorScheme.error
            )
        }

        if (deletionFailed) {
            Text(
                text = str(Res.string.delete_local_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ReportConfirmationDialog(
    choice: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val titleRes = when (choice) {
        "delete" -> Res.string.delete_local_data
        "rebuild" -> Res.string.report_rebuild
        "replacement" -> Res.string.report_replacement
        else -> Res.string.report_disconnect
    }
    val textRes = when (choice) {
        "delete" -> Res.string.delete_local_confirmation
        "rebuild" -> Res.string.report_rebuild_confirmation
        "replacement" -> Res.string.report_replacement_confirmation
        else -> Res.string.report_disconnect_confirmation
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(titleRes)) },
        text = { Text(str(textRes)) },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(str(Res.string.cancel))
            }
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag("report-confirm-action"),
                onClick = onConfirm
            ) {
                Text(str(Res.string.confirm))
            }
        }
    )
}

@Preview(widthDp = 320, fontScale = 2f)
@Composable
private fun GoogleConnectionConnectedPreview() {
    val sampleBinding = ReportBinding(
        trackerId = "test-tracker",
        connectionGeneration = "gen-1",
        accountReference = "user@example.com",
        spreadsheetId = "sample-sheet-id",
        annualTabIds = mapOf(2026 to 0)
    )
    val sampleHealth = ReportHealth(
        binding = sampleBinding,
        pendingCount = 2,
        result = SyncPassResult.Synced,
        lastSync = "2026-11-09T10:30:00Z"
    )
    val sampleActions = ReportActions(
        connectReport = {},
        reconnectReport = {},
        selectReport = {},
        rebuildReport = {},
        createReplacementReport = {},
        disconnectReport = {},
        retryReport = {},
        deleteLocalData = {}
    )

    DaymarkTheme(ThemePreference.LIGHT) {
        GoogleConnectionSection(
            state = SettingsUiState(report = sampleHealth),
            actions = sampleActions
        )
    }
}

@Preview
@Composable
private fun GoogleConnectionDisconnectedPreview() {
    val sampleActions = ReportActions(
        connectReport = {},
        reconnectReport = {},
        selectReport = {},
        rebuildReport = {},
        createReplacementReport = {},
        disconnectReport = {},
        retryReport = {},
        deleteLocalData = {}
    )

    DaymarkTheme(ThemePreference.DARK) {
        GoogleConnectionSection(
            state = SettingsUiState(report = ReportHealth(binding = null)),
            actions = sampleActions
        )
    }
}
