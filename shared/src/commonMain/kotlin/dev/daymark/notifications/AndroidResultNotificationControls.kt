package dev.daymark.notifications

import kotlinx.coroutines.flow.StateFlow

/** Optional capabilities of the Android result adapter. */
interface AndroidResultNotificationControls {
    val status: StateFlow<ResultNotificationStatus>
    fun openAppSettings()
    fun openChannelSettings(channel: ResultNotificationChannel)
    fun enableFailureChannel()
    fun post(notification: AutomaticResultNotification)
}

data class AutomaticResultNotification(
    val identifier: String,
    val title: String,
    val body: String,
    val manualCheckLabel: String? = null,
    val failureSoundEnabled: Boolean = false
)

enum class ResultNotificationChannel { SUCCESS, FAILURE }

data class ResultNotificationStatus(
    val appAllowed: Boolean,
    val legacyChannelOnly: Boolean = false,
    val successBlocked: Boolean = false,
    val failureBlocked: Boolean = false,
    val failureChannelSilent: Boolean = false
)
