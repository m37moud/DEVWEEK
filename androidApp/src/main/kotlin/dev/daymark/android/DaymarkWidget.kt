package dev.daymark.android

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.effectiveLanguage
import dev.daymark.settings.isolated
import dev.daymark.shared.R
import dev.daymark.widgets.WidgetAction
import dev.daymark.widgets.WidgetSnapshot
import dev.daymark.widgets.currentStatus
import dev.daymark.widgets.widgetAction
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import androidx.glance.color.ColorProvider as DayNightColorProvider

class DaymarkWidget : GlanceAppWidget() {
    override val previewSizeMode = SizeMode.Responsive(setOf(DpSize(250.dp, 40.dp)))

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val locales = context.resources.configuration.locales
        val language = effectiveLanguage(
            LanguagePreference.SYSTEM,
            (0 until locales.size()).map { locales[it].toLanguageTag() })
        provideContent {
            WidgetContent(
                context,
                widgetPreviewSnapshot().copy(effectiveLocale = language.tag)
            )
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshots = AndroidWidgetPublisher.observe(context)
        provideContent {
            val snapshot by snapshots.collectAsState(initial = null)
            val feedback by WidgetCheckState.updates.collectAsState()
            WidgetContent(context, snapshot, feedback)
        }
    }
}

@Composable
internal fun WidgetContent(
    context: Context,
    snapshot: WidgetSnapshot?,
    feedback: WidgetCheckFeedback? = null
) {
    val language =
        remember(snapshot, context) {
            snapshot?.effectiveLocale?.let { if (it == "ar") AppLanguage.AR else AppLanguage.EN }
                ?: effectiveLanguage(
                    LanguagePreference.SYSTEM,
                    context.resources.configuration.locales.let { locales ->
                        (0 until locales.size()).map { locales[it].toLanguageTag() }
                    }
                )
        }
    val localized = remember(context, language) { widgetLocalizedContext(context, snapshot) }

    fun palette(light: Color, dark: Color) = when (snapshot?.themePreference) {
        "DARK" -> ColorProvider(dark)
        "LIGHT" -> ColorProvider(light)
        else -> DayNightColorProvider(light, dark)
    }

    val foreground = palette(Color(0xFF20392D), Color(0xFFEDF2E7))
    val muted = palette(Color(0xFF56695C), Color(0xFFA1B09E))
    val primary = palette(Color(0xFF28664D), Color(0xFFC2DF9D))
    val onPrimary = palette(Color(0xFFF6FAEF), Color(0xFF21351E))

    val statusContainer = when (snapshot?.statusCode) {
        "PRESENT" -> palette(Color(0xFFDCEFD8), Color(0xFF1B3626))
        "LATE" -> palette(Color(0xFFFDE8CD), Color(0xFF3D280B))
        "ABSENT" -> palette(Color(0xFFFCE4E4), Color(0xFF3F1B1B))
        "DAY_OFF", "HOLIDAY", "VACATION" -> palette(Color(0xFFE2ECE5), Color(0xFF203328))
        else -> palette(Color(0xFFEAF1EC), Color(0xFF1E2B23))
    }

    val now = Clock.System.now()
    val status = snapshot?.currentStatus(now)
    val interaction = feedback?.takeIf {
        snapshot != null && it.trackerId == snapshot.trackerId &&
                kotlin.time.Instant.fromEpochMilliseconds(it.startedAtMillis)
                    .toLocalDateTime(TimeZone.of(snapshot.zoneId)).date == now.toLocalDateTime(
            TimeZone.of(snapshot.zoneId)
        ).date
    }
    val action = snapshot?.widgetAction(now) ?: WidgetAction.OPEN_APP
    val opensApp = action == WidgetAction.OPEN_APP || interaction?.outcome.needsWidgetRecovery()
    val feedbackText = interaction?.let { widgetFeedbackText(localized, snapshot, it) }
    val statusLabel = when {
        snapshot == null -> localized.getString(R.string.widget_open)
        status == null -> localized.getString(R.string.widget_refresh)
        !snapshot.isTracked -> localized.getString(R.string.widget_not_started_short)
        else -> localized.getString(widgetStatusResource(status))
    }
    val dark = when (snapshot?.themePreference) {
        "DARK" -> true
        "LIGHT" -> false
        else -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }
    val statusText: @Composable RowScope.() -> Unit = {
        Column(
            modifier = GlanceModifier.defaultWeight(),
            horizontalAlignment = if (language.isRtl) Alignment.End else Alignment.Start
        ) {
            Text(
                text = statusLabel, style = TextStyle(
                    color = foreground,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold
                ), maxLines = 2
            )
            snapshot?.let {
                Text(
                    text = feedbackText ?: isolated(
                        localized.getString(
                            R.string.widget_updated, isolated(
                                DisplayFormats.time(
                                    kotlin.time.Instant.fromEpochMilliseconds(it.generatedAtEpochMillis),
                                    it.zoneId, language
                                )
                            )
                        )
                    ),
                    style = TextStyle(color = muted, fontSize = 12.sp), maxLines = 2
                )
            }
        }
    }
    val statusIconContent: @Composable () -> Unit = {
        Image(
            provider = ImageProvider(widgetStatusIcon(status)),
            contentDescription = statusLabel,
            modifier = GlanceModifier.size(24.dp),
            colorFilter = ColorFilter.tint(if (status == "PRESENT") primary else foreground)
        )
    }
    val statusContent: @Composable RowScope.() -> Unit = {
        if (language.isRtl) statusText() else statusIconContent()
        Spacer(GlanceModifier.width(10.dp))
        if (language.isRtl) statusIconContent() else statusText()
    }
    val actionContent: @Composable () -> Unit = {
        CircleIconButton(
            imageProvider = ImageProvider(
                if (opensApp) {
                    if (language.isRtl) R.drawable.widget_open_icon_rtl else R.drawable.widget_open_icon
                } else R.drawable.widget_check_icon
            ),
            contentDescription = localized.getString(
                when {
                    opensApp -> R.string.widget_open
                    interaction?.outcome is dev.daymark.models.attendance.CheckOutcome.Rejected &&
                            interaction.outcome.reason != dev.daymark.models.attendance.CheckRejection.BEFORE_WINDOW ->
                        R.string.widget_check_retry_action

                    else -> R.string.widget_check
                }
            ),
            onClick = if (opensApp) actionStartActivity(widgetLaunchIntent(context, null))
            else actionStartService(
                widgetCheckIntent(context, requireNotNull(snapshot)),
                isForegroundService = true
            ),
            backgroundColor = if (action == WidgetAction.CHECK_IN && !opensApp) primary else statusContainer,
            contentColor = if (action == WidgetAction.CHECK_IN && !opensApp) onPrimary else foreground,
            modifier = GlanceModifier.size(48.dp)
        )
    }

    Row(
        modifier = GlanceModifier.fillMaxSize().background(
            ImageProvider(
                if (dark) R.drawable.widget_background_dark else R.drawable.widget_background_light
            )
        )
            .cornerRadius(20.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (language.isRtl) actionContent() else statusContent()
        Spacer(GlanceModifier.width(8.dp))
        if (language.isRtl) statusContent() else actionContent()
    }
}

internal fun widgetCheckIntent(context: Context, snapshot: WidgetSnapshot): Intent =
    Intent(context, WidgetCheckService::class.java).setData(Uri.parse(snapshot.quickCheckRoute))

internal fun widgetLaunchIntent(context: Context, snapshot: WidgetSnapshot?): Intent =
    Intent(context, MainActivity::class.java).apply {
        flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        action = Intent.ACTION_VIEW
        data = Uri.parse(snapshot?.quickCheckRoute ?: "daymark://quick-check")
    }

private fun widgetStatusResource(status: String): Int = when (status) {
    "PRESENT" -> R.string.widget_status_present
    "LATE" -> R.string.widget_status_late
    "ABSENT" -> R.string.widget_status_absent
    "DAY_OFF" -> R.string.widget_status_day_off
    "HOLIDAY" -> R.string.widget_status_holiday
    "VACATION" -> R.string.widget_status_vacation
    "AWAITING_CHECK" -> R.string.widget_status_awaiting_check
    else -> R.string.widget_status_scheduled
}

private fun widgetStatusIcon(status: String?): Int = when (status) {
    "PRESENT" -> R.drawable.widget_present_icon
    "LATE" -> R.drawable.widget_late_icon
    "ABSENT" -> R.drawable.widget_absent_icon
    "DAY_OFF", "HOLIDAY", "VACATION" -> R.drawable.widget_off_icon
    else -> R.drawable.widget_pending_icon
}

/** Nonpersonal sample used only by picker and developer previews. */
internal fun widgetPreviewSnapshot(status: String = "AWAITING_CHECK"): WidgetSnapshot {
    val now = Clock.System.now()
    val id = "f8428c3a-1347-40dc-9667-af38e69c6801"
    return WidgetSnapshot(
        trackerId = id, generatedAtEpochMillis = now.toEpochMilliseconds(),
        attendanceDate = if (status == "STALE") "2020-01-01" else now.toLocalDateTime(TimeZone.UTC).date.toString(),
        effectiveLocale = "en", displayStatus = status,
        statusCode = if (status == "STALE") "PRESENT" else status,
        placeLabel = "", deadlineDisplay = null, deadlineEpochMillis = null,
        zoneId = "UTC", quickCheckRoute = "daymark://quick-check?v=1&trackerId=$id",
        arrivalEpochMillis = now.toEpochMilliseconds() + if (status == "SCHEDULED") 3600000 else -3600000
    )
}
