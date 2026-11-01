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
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import androidx.glance.color.ColorProvider as DayNightColorProvider
import dev.daymark.settings.*
import dev.daymark.widgets.WidgetSnapshot
import dev.daymark.shared.R
import kotlinx.datetime.*
import java.util.Locale
import kotlin.time.Clock

class DaymarkWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context,id: GlanceId) {
        val snapshots = AndroidWidgetPublisher.observe(context)
        provideContent {
            val snapshot by snapshots.collectAsState(initial = null)
            WidgetContent(context,snapshot)
        }
    }
}
@Composable private fun WidgetContent(context: Context,snapshot: WidgetSnapshot?) {
    val language = snapshot?.effectiveLocale?.let { if (it == "ar") AppLanguage.AR else AppLanguage.EN }
        ?: effectiveLanguage(LanguagePreference.SYSTEM,context.resources.configuration.locales.let { locales -> (0 until locales.size()).map { locales[it].toLanguageTag() } })
    val localized = remember(context,language) { context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language.tag)) }) }
    fun palette(light: Color,dark: Color) = when(snapshot?.themePreference) {
        "DARK" -> ColorProvider(dark); "LIGHT" -> ColorProvider(light)
        else -> DayNightColorProvider(light,dark)
    }
    val foreground = palette(Color(0xFF20392D),Color(0xFFEDF2E7))
    val muted = palette(Color(0xFF56695C),Color(0xFFA1B09E))
    val background = palette(Color(0xFFF8FAF5),Color(0xFF121D18))
    val primary = palette(Color(0xFF28664D),Color(0xFFC2DF9D))
    val onPrimary = palette(Color(0xFFF6FAEF),Color(0xFF21351E))
    val today = snapshot?.let { Clock.System.now().toLocalDateTime(TimeZone.of(it.zoneId)).date.toString() == it.attendanceDate } == true
    val intent = widgetLaunchIntent(context,snapshot)
    Column(GlanceModifier.fillMaxSize().background(background).padding(16.dp),
        horizontalAlignment = if (language.isRtl) Alignment.End else Alignment.Start) {
        Text("Daymark",style = TextStyle(color = foreground,fontSize = 16.sp,fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.height(6.dp))
        Text(if (snapshot == null) localized.getString(R.string.widget_open) else if (today) snapshot.displayStatus else localized.getString(R.string.widget_stale),
            style = TextStyle(color = foreground,fontSize = 16.sp,fontWeight = FontWeight.Medium),maxLines = 2)
        if (snapshot != null) {
            Text(DisplayFormats.date(LocalDate.parse(snapshot.attendanceDate),language),style = TextStyle(color = muted,fontSize = 11.sp),maxLines = 1)
            Text(isolated(snapshot.placeLabel),style = TextStyle(color = muted,fontSize = 12.sp),maxLines = 1)
            snapshot.deadlineDisplay?.let { Text(localized.getString(R.string.widget_deadline,isolated(it)),style = TextStyle(color = muted,fontSize = 12.sp),maxLines = 1) }
            Text(localized.getString(R.string.widget_updated,isolated(DisplayFormats.time(kotlin.time.Instant.fromEpochMilliseconds(snapshot.generatedAtEpochMillis),snapshot.zoneId,language))),
                style = TextStyle(color = muted,fontSize = 10.sp),maxLines = 1)
        }
        Spacer(GlanceModifier.defaultWeight())
        Button(text = localized.getString(if (snapshot == null) R.string.widget_open else R.string.widget_check),
            onClick = actionStartActivity(intent),colors = ButtonDefaults.buttonColors(backgroundColor = primary,contentColor = onPrimary),modifier = GlanceModifier.fillMaxWidth())
    }
}

internal fun widgetLaunchIntent(context: Context,snapshot: WidgetSnapshot?): Intent = Intent(context,MainActivity::class.java).apply {
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    action = Intent.ACTION_VIEW
    data = Uri.parse(snapshot?.quickCheckRoute ?: "daymark://quick-check")
}
