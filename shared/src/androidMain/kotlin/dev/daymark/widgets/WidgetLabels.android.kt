package dev.daymark.widgets

import android.content.Context
import android.content.res.Configuration
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.settings.AppLanguage
import dev.daymark.shared.R
import java.util.Locale

private var widgetContext: Context? = null
fun initializeWidgetResources(context: Context) {
    widgetContext = context.applicationContext
}

internal fun widgetContext(): Context =
    requireNotNull(widgetContext) { "Widget resources are not initialized" }

internal actual fun widgetStatusLabel(status: AttendanceStatus?, language: AppLanguage): String {
    val base = widgetContext()
    val localized =
        base.createConfigurationContext(Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language.tag))
        })
    return localized.getString(
        when (status) {
            null -> R.string.widget_not_started
            AttendanceStatus.SCHEDULED -> R.string.widget_status_scheduled
            AttendanceStatus.AWAITING_CHECK -> R.string.widget_status_awaiting_check
            AttendanceStatus.PRESENT -> R.string.widget_status_present
            AttendanceStatus.LATE -> R.string.widget_status_late
            AttendanceStatus.ABSENT -> R.string.widget_status_absent
            AttendanceStatus.DAY_OFF -> R.string.widget_status_day_off
            AttendanceStatus.HOLIDAY -> R.string.widget_status_holiday
            AttendanceStatus.VACATION -> R.string.widget_status_vacation
        }
    )
}

internal actual fun widgetSystemLanguageTags(): List<String> =
    widgetContext().resources.configuration.locales.let { locales ->
        (0 until locales.size()).map { locales[it].toLanguageTag() }
    }
