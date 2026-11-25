package dev.daymark.android

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dev.daymark.logging.AppLogger
import dev.daymark.models.attendance.CheckMode
import dev.daymark.notifications.AndroidResultNotificationControls
import dev.daymark.notifications.AttendanceResultPort
import dev.daymark.notifications.AutomaticResultNotification
import dev.daymark.notifications.ResultNotificationChannel
import dev.daymark.notifications.ResultNotificationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidAttendanceResults(private val context: Context) :
    AttendanceResultPort, AndroidResultNotificationControls {
    override val mode = CheckMode.AUTOMATIC
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val currentStatus = MutableStateFlow(readStatus())
    override val status = currentStatus.asStateFlow()

    private var launcher: ActivityResultLauncher<String>? = null
    private var permissionCompletion: ((Boolean) -> Unit)? = null

    fun attach(activity: ComponentActivity): () -> Unit {
        launcher?.unregister()
        finishPermission()
        val registration = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { finishPermission() }
        launcher = registration
        return {
            if (launcher === registration) {
                registration.unregister()
                launcher = null
                finishPermission()
            }
        }
    }

    fun refreshStatus() {
        currentStatus.value = readStatus()
    }

    private fun finishPermission() {
        refreshStatus()
        val completion = permissionCompletion
        permissionCompletion = null
        completion?.invoke(status.value.appAllowed)
    }

    override fun requestPermission(completion: (Boolean) -> Unit) {
        refreshStatus()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || hasRuntimePermission()) {
            completion(status.value.appAllowed)
            return
        }
        val request = launcher ?: return completion(false)
        if (permissionCompletion != null) return completion(false)
        permissionCompletion = completion
        try {
            request.launch(Manifest.permission.POST_NOTIFICATIONS)
        } catch (failure: IllegalStateException) {
            permissionCompletion = null
            throw failure
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun hasRuntimePermission() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED

    override fun openAppSettings() = openSettings(
        intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    )

    override fun openChannelSettings(channel: ResultNotificationChannel) = openSettings(
        intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(
            Settings.EXTRA_CHANNEL_ID,
            if (channel == ResultNotificationChannel.FAILURE) FAILURE_CHANNEL else SUCCESS_CHANNEL
        )
    )

    private fun openSettings(intent: Intent) {
        try {
            context.startActivity(
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (failure: ActivityNotFoundException) {
            AppLogger.e(TAG, "Notification settings unavailable", failure)
        }
    }

    override fun enableFailureChannel() {
        synchronized(channelLock) {
            if (manager.getNotificationChannel(SUCCESS_CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE) {
                createFailureChannel()
            }
        }
        refreshStatus()
    }

    override fun post(identifier: String, title: String, body: String) =
        post(
            notification = AutomaticResultNotification(
                identifier = identifier,
                title = title,
                body = body
            )
        )

    override fun post(notification: AutomaticResultNotification) {
        if (!manager.areNotificationsEnabled()) return
        try {
            val channel = postingChannel(failure = notification.manualCheckLabel != null)
            if (manager.getNotificationChannel(channel)?.importance == NotificationManager.IMPORTANCE_NONE) return
            val alert = channel == FAILURE_CHANNEL && notification.failureSoundEnabled
            manager.notify(
                notification.identifier, 0,
                buildNotification(notification = notification, channel = channel, alert = alert)
            )
            AppLogger.i(TAG, "Result notification posted")
        } catch (failure: SecurityException) {
            AppLogger.e(TAG, "Result notification blocked", failure)
        }
    }

    private fun postingChannel(failure: Boolean): String = synchronized(channelLock) {
        if (manager.getNotificationChannel(SUCCESS_CHANNEL) == null) {
            if (manager.getNotificationChannel(FAILURE_CHANNEL) == null) createFailureChannel()
            createSuccessChannel()
        }
        if (failure && manager.getNotificationChannel(FAILURE_CHANNEL) != null) FAILURE_CHANNEL
        else SUCCESS_CHANNEL
    }

    private fun buildNotification(
        notification: AutomaticResultNotification,
        channel: String,
        alert: Boolean
    ): android.app.Notification {
        val tap = todayIntent()
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
            .setContentIntent(tap)
            .setSilent(!alert)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
        notification.manualCheckLabel?.let { builder.addAction(0, it, tap) }
        return builder.build()
    }

    private fun todayIntent() = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).setData("daymark://today".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun createSuccessChannel() = manager.createNotificationChannel(
        NotificationChannel(
            SUCCESS_CHANNEL, context.getString(R.string.attendance_results_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setSound(null, null)
            enableVibration(false)
        }
    )

    private fun createFailureChannel() = manager.createNotificationChannel(
        NotificationChannel(
            FAILURE_CHANNEL, context.getString(R.string.attendance_failures_channel),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(
                Settings.System.DEFAULT_NOTIFICATION_URI,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
            )
        }
    )

    private fun readStatus(): ResultNotificationStatus {
        val success = manager.getNotificationChannel(SUCCESS_CHANNEL)
        val failure = manager.getNotificationChannel(FAILURE_CHANNEL)
        return ResultNotificationStatus(
            appAllowed = manager.areNotificationsEnabled(),
            legacyChannelOnly = success != null && failure == null,
            successBlocked = success?.importance == NotificationManager.IMPORTANCE_NONE,
            failureBlocked = failure?.importance == NotificationManager.IMPORTANCE_NONE,
            failureChannelSilent = failure != null &&
                    (failure.sound == null || failure.importance < NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    override fun clearResults() {
        manager.activeNotifications.filter { it.tag?.startsWith("daymark.result:") == true }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    private val channelLock = Any()

    private companion object {
        const val TAG = "AndroidAttendanceResults"
        const val SUCCESS_CHANNEL = "attendance-results"
        const val FAILURE_CHANNEL = "attendance-result-failures"
    }
}
