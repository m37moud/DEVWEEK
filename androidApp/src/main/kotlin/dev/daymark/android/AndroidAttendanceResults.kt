package dev.daymark.android

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.daymark.logging.AppLogger
import dev.daymark.models.attendance.CheckMode
import dev.daymark.notifications.AttendanceResultPort

class AndroidAttendanceResults(private val context: Context) : AttendanceResultPort {
    override val mode = CheckMode.AUTOMATIC
    private val manager = context.getSystemService(NotificationManager::class.java)
    private var launcher: ActivityResultLauncher<String>? = null
    private var permissionCompletion: ((Boolean) -> Unit)? = null

    fun attach(activity: ComponentActivity): () -> Unit {
        launcher?.unregister()
        permissionCompletion?.invoke(false)
        permissionCompletion = null
        val registration =
            activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
                permissionCompletion?.invoke(allowed && manager.areNotificationsEnabled())
                permissionCompletion = null
            }
        launcher = registration
        return {
            if (launcher === registration) {
                registration.unregister()
                launcher = null
                permissionCompletion?.invoke(false)
                permissionCompletion = null
            }
        }
    }

    override fun requestPermission(completion: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            completion(manager.areNotificationsEnabled())
        } else {
            val request = launcher ?: return completion(false)
            permissionCompletion = completion
            request.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun post(identifier: String, title: String, body: String) {
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "Daymark",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                })
            val tap = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).setData(Uri.parse("daymark://today"))
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            manager.notify(
                identifier, 0, NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_launcher_monochrome).setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(tap)
                    .setSilent(true).setOnlyAlertOnce(true).setAutoCancel(true).build()
            )
        } catch (failure: RuntimeException) {
            AppLogger.e("AndroidAttendanceResults", "Result notification failed", failure)
        }
    }

    override fun clearResults() {
        manager.activeNotifications.filter { it.tag?.startsWith("daymark.result:") == true }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    private companion object {
        const val CHANNEL = "attendance-results"
    }
}
