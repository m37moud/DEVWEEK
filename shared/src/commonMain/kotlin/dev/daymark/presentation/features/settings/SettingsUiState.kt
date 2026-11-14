package dev.daymark.presentation.features.settings

import dev.daymark.location.LocationCapabilities
import dev.daymark.models.reporting.ReportHealth
import dev.daymark.models.settings.AppSettings

data class SettingsUiState(
    val widgetStatus: dev.daymark.widgets.WidgetSetupStatus = dev.daymark.widgets.WidgetSetupStatus.CHECKING,
    val widgetCanPin: Boolean = false,
    val widgetPinPending: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val ready: Boolean = false,
    val busy: Boolean = false,
    val automaticEnabled: Boolean = false,
    val permissionDenied: Boolean = false,
    val capabilities: LocationCapabilities? = null,
    val invalidName: Boolean = false,
    val requestingReminderPermission: Boolean = false,
    val reminderPermissionGranted: Boolean? = null,
    val report: ReportHealth = ReportHealth(),
    val reportBusy: Boolean = false,
    val reportActionFailed: Boolean = false,
    val deleting: Boolean = false,
    val deletionFailed: Boolean = false,
    val storageError: Boolean = false
)
