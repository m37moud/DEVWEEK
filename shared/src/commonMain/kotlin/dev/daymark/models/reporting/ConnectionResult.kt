package dev.daymark.models.reporting

import dev.daymark.models.reporting.RemoteReport
import dev.daymark.models.reporting.ReportBinding

sealed interface ConnectionResult {
    data class Connected(val binding: ReportBinding) : ConnectionResult
    data object PendingCreation : ConnectionResult
    data class SelectionRequired(val reports: List<RemoteReport>) : ConnectionResult
    data object Cancelled : ConnectionResult
    data object AuthorizationRequired : ConnectionResult
    data class Failed(val errorKey: String) : ConnectionResult
}
