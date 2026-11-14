package dev.daymark.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import dev.daymark.logging.AppLogger
import dev.daymark.models.attendance.CheckOutcome
import dev.daymark.models.attendance.CheckRejection
import dev.daymark.shared.R
import dev.daymark.widgets.parseQuickCheckRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.time.Instant

class WidgetCheckService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL_ACTION) {
            if (checkJob == null) stopSelf() else checkJob?.cancel()
            return START_NOT_STICKY
        }
        val trackerId = intent?.dataString?.let(::parseQuickCheckRoute)?.trackerId
        if (trackerId == null) {
            AppLogger.i("WidgetCheck", "Invalid widget request rejected")
            stopSelf()
            return START_NOT_STICKY
        }
        if (checkJob != null) return START_NOT_STICKY
        if ((application as DaymarkApplication).graph.isDeleting) {
            stopSelf()
            return START_NOT_STICKY
        }
        WidgetCheckState.checking(trackerId)
        if (!promote(trackerId)) return START_NOT_STICKY
        checkJob = scope.launch { checkAttendance(trackerId) }
        return START_NOT_STICKY
    }

    private fun promote(trackerId: String): Boolean = try {
        val notification = progressNotification()
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        else startForeground(NOTIFICATION_ID, notification)
        true
    } catch (failure: SecurityException) {
        AppLogger.e("WidgetCheck", "Location service permission unavailable", failure)
        WidgetCheckState.finished(
            trackerId,
            CheckOutcome.Rejected(null, CheckRejection.LOCATION_DENIED)
        )
        (application as DaymarkApplication).graph.requestWidgetUpdate()
        stopSelf()
        false
    } catch (failure: IllegalStateException) {
        AppLogger.e("WidgetCheck", "Location service start unavailable", failure)
        WidgetCheckState.finished(
            trackerId,
            CheckOutcome.Rejected(null, CheckRejection.UNAVAILABLE)
        )
        (application as DaymarkApplication).graph.requestWidgetUpdate()
        stopSelf()
        false
    }

    private suspend fun checkAttendance(trackerId: String) {
        val graph = (application as DaymarkApplication).graph
        graph.requestWidgetUpdate()
        AppLogger.d("WidgetCheck", "User initiated widget check")
        try {
            val runner = WidgetCheckRunner(
                check = { expectedId -> trackedCheck(graph, expectedId) },
                refresh = { graph.widgets.refresh() }
            )
            val outcome = withContext(Dispatchers.IO) { runner.run(trackerId) }
            AppLogger.i("WidgetCheck", "Check completed: ${outcome.reason?.name ?: "COMPLETE"}")
        } catch (cancelled: CancellationException) {
            WidgetCheckState.cancelled(trackerId)
            AppLogger.d("WidgetCheck", "Widget check cancelled")
            throw cancelled
        } finally {
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                graph.requestWidgetUpdate()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                checkJob = null
            }
        }
    }

    private suspend fun trackedCheck(graph: AndroidAppGraph, expectedId: String): CheckOutcome {
        val tracker = graph.database.dao().tracker()
            ?: return CheckOutcome.Rejected(null, CheckRejection.NO_TRACKER)
        if (tracker.id != expectedId) return CheckOutcome.StateChanged(null)
        val day = graph.attendanceRepository.loadCurrentDay(graph.clock.now())
            ?: return CheckOutcome.Rejected(null, CheckRejection.NO_TRACKER)
        if (day.revision == 0L && day.plan.arrival < Instant.parse(tracker.trackingStartsAt))
            return CheckOutcome.StateChanged(null)
        return graph.attendance.widgetCheck(expectedId)
    }

    private fun progressNotification(): Notification {
        val localized = widgetLocalizedContext(this, AndroidWidgetPublisher.read(this))
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                localized.getString(R.string.widget_check_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        val cancel = PendingIntent.getService(
            this, 0,
            Intent(this, WidgetCheckService::class.java).setAction(CANCEL_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.widget_check_icon)
            .setContentTitle(localized.getString(R.string.widget_checking))
            .setContentText(localized.getString(R.string.widget_check_progress))
            .setOngoing(true)
            .setProgress(0, 0, true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    localized.getString(R.string.widget_cancel), cancel
                ).build()
            )
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private var checkJob: Job? = null

        internal suspend fun cancelAndJoin() = withContext(Dispatchers.Main.immediate) {
            checkJob?.cancelAndJoin()
        }

        private const val CHANNEL_ID = "widget-check"
        private const val NOTIFICATION_ID = 4201
        private const val CANCEL_ACTION = "dev.daymark.app.CANCEL_WIDGET_CHECK"
    }
}
