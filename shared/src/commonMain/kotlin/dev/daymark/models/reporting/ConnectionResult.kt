package dev.daymark.models.reporting

sealed interface ConnectionResult {
    data class Connected(val binding: ReportBinding) : ConnectionResult
    data object PendingCreation : ConnectionResult
    data class SelectionRequired(val reports: List<RemoteReport>) : ConnectionResult
    data object Cancelled : ConnectionResult
    data object AuthorizationRequired : ConnectionResult
    data object LocalBindingUnreadable : ConnectionResult
    data class Failed(val errorKey: String) : ConnectionResult
}
