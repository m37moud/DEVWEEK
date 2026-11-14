package dev.daymark.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import dev.daymark.logging.AppLogger
import java.util.UUID

internal actual fun createWidgetSetupPort(): WidgetSetupPort =
    AndroidWidgetSetupPort(widgetContext())

internal class AndroidWidgetSetupPort(private val context: Context) : WidgetSetupPort {
    private val manager = AppWidgetManager.getInstance(context)
    private val provider =
        ComponentName(context.packageName, "dev.daymark.android.DaymarkWidgetReceiver")
    private var pinReceiver: BroadcastReceiver? = null
    override val canRequestPin: Boolean get() = manager.isRequestPinAppWidgetSupported

    override fun query(completion: (WidgetSetupStatus) -> Unit) {
        val status = try {
            if (manager.getAppWidgetIds(provider)
                    .isNotEmpty()
            ) WidgetSetupStatus.ADDED else WidgetSetupStatus.NOT_ADDED
        } catch (failure: RuntimeException) {
            AppLogger.e("WidgetSetup", "Widget configuration query failed", failure)
            WidgetSetupStatus.UNKNOWN
        }
        completion(status)
    }

    override fun requestPin(onConfirmed: () -> Unit): Boolean {
        if (!canRequestPin) return false
        close()
        return try {
            manager.requestPinAppWidget(provider, null, pinConfirmation(onConfirmed))
                .also { if (!it) close() }
        } catch (failure: RuntimeException) {
            close()
            AppLogger.e("WidgetSetup", "Widget pin request failed", failure)
            false
        }
    }

    private fun pinConfirmation(onConfirmed: () -> Unit): PendingIntent {
        val action = context.packageName + ".WIDGET_PIN." + UUID.randomUUID()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                close()
                onConfirmed()
            }
        }
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(
            receiver,
            IntentFilter(action),
            Context.RECEIVER_NOT_EXPORTED
        )
        else context.registerReceiver(receiver, IntentFilter(action))
        pinReceiver = receiver
        return PendingIntent.getBroadcast(
            context, 0, Intent(action).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override fun close() {
        pinReceiver?.let(context::unregisterReceiver)
        pinReceiver = null
    }
}
