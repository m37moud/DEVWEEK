package dev.daymark.models.reporting

import dev.daymark.models.reporting.ConnectionResult
import dev.daymark.reporting.ReportIssue

data class ReportHealth(
    val binding: ReportBinding? = null,
    val pendingCount: Int = 0,
    val result: SyncPassResult = SyncPassResult.Idle,
    val issues: List<ReportIssue> = emptyList(),
    val connectionResult: ConnectionResult? = null,
    val lastSync: String? = null,
    val busy: Boolean = false
)
