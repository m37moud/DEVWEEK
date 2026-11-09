package dev.daymark.android

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import dev.daymark.settings.*
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.effectiveLanguage
import dev.daymark.shared.R
import dev.daymark.widgets.WidgetSnapshot
import java.util.Locale
import kotlin.time.Clock
import kotlinx.datetime.*

class DaymarkWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshots = AndroidWidgetPublisher.observe(context)
        provideContent {
            val snapshot by snapshots.collectAsState(initial = null)
            WidgetContent(context, snapshot)
        }
    }
}

@Composable
private fun WidgetContent(context: Context, snapshot: WidgetSnapshot?) {
    val language =
        snapshot?.effectiveLocale?.let { if (it == "ar") AppLanguage.AR else AppLanguage.EN }
            ?: effectiveLanguage(
                LanguagePreference.SYSTEM,
                context.resources.configuration.locales.let { locales ->
                    (0 until locales.size()).map { locales[it].toLanguageTag() }
                }
            )
    val localized = remember(context, language) {
        context.createConfigurationContext(
            Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language.tag))
            }
        )
    }

    fun palette(light: Color, dark: Color) = when (snapshot?.themePreference) {
        "DARK" -> ColorProvider(dark)
        "LIGHT" -> ColorProvider(light)
        else -> DayNightColorProvider(light, dark)
    }

    val foreground = palette(Color(0xFF20392D), Color(0xFFEDF2E7))
    val muted = palette(Color(0xFF56695C), Color(0xFFA1B09E))
    val background = palette(Color(0xFFF8FAF5), Color(0xFF121D18))
    val primary = palette(Color(0xFF28664D), Color(0xFFC2DF9D))
    val onPrimary = palette(Color(0xFFF6FAEF), Color(0xFF21351E))

    val statusContainer = when (snapshot?.statusCode) {
        "PRESENT" -> palette(Color(0xFFDCEFD8), Color(0xFF1B3626))
        "LATE" -> palette(Color(0xFFFDE8CD), Color(0xFF3D280B))
        "ABSENT" -> palette(Color(0xFFFCE4E4), Color(0xFF3F1B1B))
        "DAY_OFF", "HOLIDAY", "VACATION" -> palette(Color(0xFFE2ECE5), Color(0xFF203328))
        else -> palette(Color(0xFFEAF1EC), Color(0xFF1E2B23))
    }

    val statusContentColor = when (snapshot?.statusCode) {
        "PRESENT" -> palette(Color(0xFF1B4D36), Color(0xFFC2DF9D))
        "LATE" -> palette(Color(0xFF7A4A00), Color(0xFFF5BA72))
        "ABSENT" -> palette(Color(0xFF8A2424), Color(0xFFFCA5A5))
        "DAY_OFF", "HOLIDAY", "VACATION" -> palette(Color(0xFF355444), Color(0xFFA1B09E))
        else -> palette(Color(0xFF20392D), Color(0xFFEDF2E7))
    }

    val today = snapshot?.let {
        Clock.System.now()
            .toLocalDateTime(TimeZone.of(it.zoneId)).date.toString() == it.attendanceDate
    } == true

    val intent = widgetLaunchIntent(context, snapshot)

    val isAlreadyConfirmed = snapshot != null && today &&
            (snapshot.statusCode == "PRESENT" || snapshot.statusCode == "LATE")

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(background)
            .cornerRadius(20.dp)
            .padding(16.dp),
        horizontalAlignment = if (language.isRtl) Alignment.End else Alignment.Start
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Daymark",
                style = TextStyle(
                    color = foreground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(GlanceModifier.height(10.dp))

        Box(
            modifier = GlanceModifier
                .cornerRadius(10.dp)
                .background(statusContainer)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            val statusLabel = when {
                snapshot == null -> localized.getString(R.string.widget_open)
                !today -> localized.getString(R.string.widget_stale)
                else -> snapshot.displayStatus
            }
            Text(
                text = statusLabel,
                style = TextStyle(
                    color = statusContentColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 2
            )
        }

        if (snapshot != null) {
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = isolated(snapshot.placeLabel),
                style = TextStyle(color = muted, fontSize = 12.sp),
                maxLines = 1
            )
            snapshot.deadlineDisplay?.let { deadline ->
                Text(
                    text = localized.getString(R.string.widget_deadline, isolated(deadline)),
                    style = TextStyle(color = muted, fontSize = 12.sp),
                    maxLines = 1
                )
            }
            Text(
                text = localized.getString(
                    R.string.widget_updated,
                    isolated(
                        DisplayFormats.time(
                            kotlin.time.Instant.fromEpochMilliseconds(snapshot.generatedAtEpochMillis),
                            snapshot.zoneId,
                            language
                        )
                    )
                ),
                style = TextStyle(color = muted, fontSize = 10.sp),
                maxLines = 1
            )
        }

        Spacer(GlanceModifier.defaultWeight())

        if (isAlreadyConfirmed) {
            Button(
                text = "✓ " + (snapshot.displayStatus),
                onClick = actionStartActivity(intent),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = palette(Color(0xFFE2ECE5), Color(0xFF203328)),
                    contentColor = foreground
                ),
                modifier = GlanceModifier.fillMaxWidth()
            )
        } else {
            Button(
                text = localized.getString(if (snapshot == null) R.string.widget_open else R.string.widget_check),
                onClick = actionStartActivity(intent),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = primary,
                    contentColor = onPrimary
                ),
                modifier = GlanceModifier.fillMaxWidth()
            )
        }
    }
}

internal fun widgetLaunchIntent(context: Context, snapshot: WidgetSnapshot?): Intent =
    Intent(context, MainActivity::class.java).apply {
        flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        action = Intent.ACTION_VIEW
        data = Uri.parse(snapshot?.quickCheckRoute ?: "daymark://quick-check")
    }
