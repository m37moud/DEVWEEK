package dev.daymark.presentation.features.settings.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Regular
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Google
import compose.icons.fontawesomeicons.solid.Info
import dev.daymark.models.reporting.ConnectionResult
import dev.daymark.models.reporting.ProjectionKey
import dev.daymark.models.reporting.SyncPassResult
import dev.daymark.presentation.features.settings.SettingsUiState
import dev.daymark.reporting.ReportField
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
import dev.daymark.resources.report_connected
import dev.daymark.resources.report_connection_failed
import dev.daymark.resources.report_configuration_required
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
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.isolated
import dev.daymark.settings.str
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
    val deleting = state.deleting
    val busy = state.reportBusy
    val deletionFailed = state.deletionFailed
    val uri = LocalUriHandler.current
    val language = LocalAppLanguage.current
    var confirm by remember { mutableStateOf<String?>(null) }
    var showMessageInfo by remember { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth().testTag("google-section"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
//            modifier = modifier.fillMaxWidth().testTag("google-section")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(str(Res.string.google_report), style = MaterialTheme.typography.titleLarge)
                IconButton(
                    onClick = {
                        showMessageInfo = !showMessageInfo
                    },
                    modifier = Modifier.size(16.dp),
                    shape = IconButtonDefaults.outlinedShape,
                    colors = IconButtonDefaults.outlinedIconButtonColors()
                ) {
                    Icon(
                        imageVector = FontAwesomeIcons.Solid.Info,
                        contentDescription = str(Res.string.info),
                        modifier = Modifier.testTag("google-info")
                    )
                }
            }
            AnimatedVisibility(
                showMessageInfo,
                label = str(Res.string.info),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()

            ) {

                Text(
                    str(Res.string.report_local_authority),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(str(if (health.binding == null) Res.string.report_disconnected else Res.string.report_connected))
            if (busy || deleting || health.busy)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            if (health.binding != null) {
                Text(
                    str(
                        Res.string.report_pending_count,
                        DisplayFormats.number(health.pendingCount, language)
                    )
                )
                health.lastSync?.let { raw ->
                    runCatching { Instant.parse(raw) }.getOrNull()?.let { at ->
                        val zone = TimeZone.currentSystemDefault()
                        Text(
                            str(
                                Res.string.report_last_sync,
                                DisplayFormats.date(at.toLocalDateTime(zone).date, language),
                                DisplayFormats.time(at, zone.id, language)
                            )
                        )
                    }
                }
                Text(
                    str(
                        when (health.result) {
                            SyncPassResult.AuthorizationRequired -> Res.string.report_auth_required
                            SyncPassResult.SchemaConflict -> Res.string.report_schema_conflict
                            SyncPassResult.ReportUnavailable -> Res.string.report_unavailable
                            SyncPassResult.ContentTooLarge -> Res.string.report_content_too_long
                            SyncPassResult.Pending -> Res.string.report_pending
                            SyncPassResult.Synced -> Res.string.report_observed_sync
                            else -> Res.string.report_ready
                        }
                    )
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = !busy && !deleting,
                        onClick = { actions.retryReport() },
                        modifier = Modifier.testTag("report-retry")
                    ) { Text(str(Res.string.retry)) }
                    TextButton(
                        enabled = !busy && !deleting,
                        onClick = { uri.openUri("https://docs.google.com/spreadsheets/d/${health.binding.spreadsheetId}/edit") }) {
                        Text(
                            str(Res.string.report_open)
                        )
                    }
                    TextButton(
                        enabled = !busy && !deleting,
                        onClick = { actions.reconnectReport() },
                        modifier = Modifier.testTag("report-reconnect")
                    ) { Text(str(Res.string.report_reconnect)) }
                    TextButton(
                        enabled = !busy && !deleting,
                        onClick = { confirm = "disconnect" },
                        modifier = Modifier.testTag("report-disconnect")
                    ) { Text(str(Res.string.report_disconnect)) }
                    TextButton(enabled = !busy && !deleting, onClick = { confirm = "rebuild" }) {
                        Text(
                            str(Res.string.report_rebuild)
                        )
                    }
                }
            } else
                Button(
                    enabled = !busy && !deleting,
                    onClick = { actions.connectReport() },
                    modifier = Modifier.testTag("report-connect")
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(str(Res.string.report_connect))

                        if (busy) CircularProgressIndicator(
                            modifier = Modifier.size(16.dp).testTag("report-connect-loading"),
                            strokeWidth = 2.dp
                        ) else Icon(
                            imageVector = FontAwesomeIcons.Brands.Google,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            health.issues.forEach { issue ->
                val label = str(
                    when (issue.field) {
                        ReportField.NOTE -> Res.string.correction_note; ReportField.PROFILE_NAME -> Res.string.profile_name; ReportField.RECORD -> Res.string.edit_record
                    }
                )
                val date =
                    (issue.key as? ProjectionKey.Day)?.date?.let {
                        DisplayFormats.date(
                            it,
                            language
                        )
                    }
                        .orEmpty()
                Text(
                    str(Res.string.report_field_too_long, label, date),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("report-content-error")
                )
            }
            when (val result = if (state.reportActionFailed) ConnectionResult.Failed("connection_failed") else health.connectionResult) {
                ConnectionResult.PendingCreation -> Text(str(Res.string.report_creation_pending))
                is ConnectionResult.SelectionRequired -> {
                    Text(str(Res.string.report_select))
                    result.reports.forEachIndexed { index, report ->
                        TextButton(
                            enabled = !busy,
                            onClick = { actions.selectReport(report.id) }) {
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

                ConnectionResult.AuthorizationRequired -> Text(str(Res.string.report_auth_required))
                ConnectionResult.Cancelled -> Text(str(Res.string.report_consent_cancelled))
                is ConnectionResult.Failed -> Text(
                    str(if (result.errorKey == "authorization_configuration") Res.string.report_configuration_required else Res.string.report_connection_failed),
                    color = MaterialTheme.colorScheme.error
                )

                else -> Unit
            }
            TextButton(enabled = !busy && !deleting, onClick = { confirm = "replacement" }) {
                Text(
                    str(
                        Res.string.report_replacement
                    )
                )
            }
            HorizontalDivider()
            TextButton(
                enabled = !busy && !deleting,
                onClick = { confirm = "delete" },
                modifier = Modifier.testTag("delete-local-data")
            ) { Text(str(Res.string.delete_local_data), color = MaterialTheme.colorScheme.error) }
            if (deletionFailed) Text(
                str(Res.string.delete_local_failed),
                color = MaterialTheme.colorScheme.error
            )
        }
    }
    confirm?.let { choice ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = {
                Text(
                    str(
                        when (choice) {
                            "delete" -> Res.string.delete_local_data
                            "rebuild" -> Res.string.report_rebuild
                            "replacement" -> Res.string.report_replacement
                            else -> Res.string.report_disconnect
                        }
                    )
                )
            },
            text = {
                Text(
                    str(
                        when (choice) {
                            "delete" -> Res.string.delete_local_confirmation
                            "rebuild" -> Res.string.report_rebuild_confirmation
                            "replacement" -> Res.string.report_replacement_confirmation
                            else -> Res.string.report_disconnect_confirmation
                        }
                    )
                )
            },
            dismissButton = {
                TextButton(onClick = {
                    confirm = null
                }) { Text(str(Res.string.cancel)) }
            },
            confirmButton = {
                TextButton(modifier = Modifier.testTag("report-confirm-action"), onClick = {
                    confirm = null
                    when (choice) {
                        "delete" -> actions.deleteLocalData()

                        "rebuild" -> actions.rebuildReport()
                        "replacement" -> actions.createReplacementReport()
                        else -> actions.disconnectReport()
                    }
                }) { Text(str(Res.string.confirm)) }
            })
    }
}
