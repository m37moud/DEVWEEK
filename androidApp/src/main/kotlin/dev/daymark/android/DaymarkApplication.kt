package dev.daymark.android

import android.app.Application
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidgetManager
import dev.daymark.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class DaymarkApplication : Application() {
    val graph by lazy { AndroidAppGraph(this) }
    internal var locationPermissions: AndroidLocationPermissions? = null
    internal val googleAuth get() = graph.googleAuth
    override fun onCreate() {
        super.onCreate()
        graph.requestRecovery()
        if (Build.VERSION.SDK_INT >= 35) graph.scope.launch {
            try {
                val outcome = GlanceAppWidgetManager(this@DaymarkApplication).setWidgetPreviews(
                    DaymarkWidgetReceiver::class
                )
                AppLogger.d(
                    "WidgetPreview",
                    if (outcome == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS) "Picker preview published" else "Picker preview deferred"
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: RuntimeException) {
                AppLogger.e("WidgetPreview", "Picker preview publication failed", failure)
            }
        }
    }
}
