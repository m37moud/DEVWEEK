package dev.daymark.android

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(AndroidJUnit4::class)
class WidgetRenderingTest {
    @Test
    fun rendersActualWidgetStatesWithoutAttendanceAccess() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val base = instrumentation.targetContext
        val directory = File(base.getExternalFilesDir(null), "widget-evidence").apply { mkdirs() }
        for (language in listOf("en", "ar")) for (theme in listOf("LIGHT", "DARK")) {
            for ((widthDp, heightDp, scale) in listOf(
                Triple(250, 80, 1f),
                Triple(360, 160, 2f)
            )) {
                val context =
                    base.createConfigurationContext(Configuration(base.resources.configuration).apply {
                        setLocale(Locale(language))
                        fontScale = scale
                        uiMode = uiMode and Configuration.UI_MODE_NIGHT_MASK.inv() or
                                if (theme == "DARK") Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                    })
                for (status in listOf(
                    "AWAITING_CHECK",
                    "SCHEDULED",
                    "PRESENT",
                    "DAY_OFF",
                    "STALE", "CHECKING", "EARLY", "DENIED", "OUTSIDE", "SAVE_FAILED", "BUSY"
                )) {
                    val previewStatus = when (status) {
                        "EARLY" -> "SCHEDULED"
                        "CHECKING", "DENIED", "OUTSIDE", "SAVE_FAILED", "BUSY" -> "AWAITING_CHECK"
                        else -> status
                    }
                    val snapshot = widgetPreviewSnapshot(previewStatus).copy(
                        effectiveLocale = language,
                        themePreference = theme
                    )
                    val outcome = when (status) {
                        "EARLY" -> dev.daymark.models.attendance.CheckOutcome.Rejected(
                            null,
                            dev.daymark.models.attendance.CheckRejection.BEFORE_WINDOW
                        )

                        "DENIED" -> dev.daymark.models.attendance.CheckOutcome.Rejected(
                            null,
                            dev.daymark.models.attendance.CheckRejection.LOCATION_DENIED
                        )

                        "OUTSIDE" -> dev.daymark.models.attendance.CheckOutcome.Rejected(
                            null,
                            dev.daymark.models.attendance.CheckRejection.OUTSIDE_AREA
                        )

                        "SAVE_FAILED" -> dev.daymark.models.attendance.CheckOutcome.Rejected(
                            null,
                            dev.daymark.models.attendance.CheckRejection.LOCAL_SAVE_FAILED
                        )

                        "BUSY" -> dev.daymark.models.attendance.CheckOutcome.Busy(null)
                        else -> null
                    }
                    val feedback = if (status == "CHECKING" || outcome != null)
                        WidgetCheckFeedback(
                            snapshot.trackerId,
                            kotlin.time.Clock.System.now().toEpochMilliseconds(),
                            outcome
                        )
                    else null
                    val views =
                        GlanceRemoteViews().compose(context, DpSize(widthDp.dp, heightDp.dp)) {
                            WidgetContent(context, snapshot, feedback)
                        }.remoteViews
                    instrumentation.runOnMainSync {
                        val native = views.apply(context, null)
                        val density = context.resources.displayMetrics.density
                        val width = (widthDp * density).toInt()
                        val height = (heightDp * density).toInt()
                        native.measure(
                            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                        )
                        native.layout(0, 0, width, height)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        native.draw(Canvas(bitmap))
                        File(
                            directory,
                            "$language-${theme.lowercase()}-${status.lowercase()}-${widthDp}x${heightDp}.png"
                        ).outputStream().use {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                        bitmap.recycle()
                        for (text in descendants(native).filterIsInstance<android.widget.TextView>()
                            .filter { it.text.isNotBlank() && it.visibility == View.VISIBLE }) {
                            assertTrue(
                                "Text clipped: $language $theme $status ${widthDp}x${heightDp} scale=$scale text=${text.text} layout=${text.layout?.height} height=${text.height} padding=${text.paddingTop + text.paddingBottom}",
                                text.layout == null || text.layout.height <= text.height - text.paddingTop - text.paddingBottom
                            )
                        }
                        if (status == "SCHEDULED") assertTrue(descendants(native).any { it.isClickable && it.isEnabled })
                        assertTrue(descendants(native).any { it.contentDescription?.isNotBlank() == true })
                    }
                }
            }
        }
    }

    @Test
    fun pickerHasActualWidgetPreviewFallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = android.appwidget.AppWidgetManager.getInstance(context).installedProviders
            .first { it.provider.className == DaymarkWidgetReceiver::class.java.name }
        assertTrue(provider.previewImage != 0)
        assertNotNull(provider.loadPreviewImage(context, 0))
    }

    private fun descendants(view: View): List<View> = listOf(view) +
            if (view is ViewGroup) (0 until view.childCount).flatMap {
                descendants(
                    view.getChildAt(
                        it
                    )
                )
            } else emptyList()
}
