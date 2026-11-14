package dev.daymark.android

import android.content.Intent
import android.content.pm.ServiceInfo
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.daymark.models.attendance.CheckOutcome
import dev.daymark.models.attendance.CheckRejection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(AndroidJUnit4::class)
class WidgetCheckServiceTest {
    @Test
    fun scheduled_native_button_starts_service_without_opening_main_activity() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val snapshot = widgetPreviewSnapshot("SCHEDULED")
        val app = context.applicationContext as DaymarkApplication
        assertNotEquals(snapshot.trackerId, app.graph.database.dao().tracker()?.id)
        val intent = widgetCheckIntent(context, snapshot)
        val service = context.packageManager.getServiceInfo(requireNotNull(intent.component), 0)
        assertFalse(service.exported)
        if (android.os.Build.VERSION.SDK_INT >= 29)
            assertEquals(
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                service.foregroundServiceType
            )
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        val views = GlanceRemoteViews().compose(context, DpSize(250.dp, 80.dp)) {
            WidgetContent(context, snapshot)
        }.remoteViews
        try {
            WidgetCheckState.clearFinished()
            instrumentation.runOnMainSync {
                val native = views.apply(context, null)
                val button = descendants(native).first { it.isClickable && it.isEnabled }
                assertTrue(button.performClick())
            }
            val feedback = withTimeout(10_000) {
                WidgetCheckState.updates.first { it?.trackerId == snapshot.trackerId && it.outcome != null }
            }
            assertTrue(
                feedback?.outcome is CheckOutcome.StateChanged ||
                        feedback?.outcome?.reason == CheckRejection.NO_TRACKER ||
                        feedback?.outcome?.reason == CheckRejection.LOCATION_DENIED
            )
            instrumentation.waitForIdleSync()
            assertEquals(0, monitor.hits)
        } finally {
            context.stopService(Intent(context, WidgetCheckService::class.java))
            instrumentation.removeMonitor(monitor)
            WidgetCheckState.clearFinished()
        }
    }

    private fun descendants(view: View): List<View> = listOf(view) +
            if (view is ViewGroup) (0 until view.childCount).flatMap {
                descendants(
                    view.getChildAt(
                        it
                    )
                )
            }
            else emptyList()
}
