package dev.daymark.widgets

import dev.daymark.attendance.AttendanceStatus
import dev.daymark.settings.*
import platform.Foundation.NSBundle

internal actual fun widgetStatusLabel(status: AttendanceStatus?,language: AppLanguage): String {
    val key = status?.let { "widget_status_${it.name.lowercase()}" } ?: "widget_not_started"
    val path = NSBundle.mainBundle.pathForResource(language.tag,ofType = "lproj")
    val bundle = path?.let(NSBundle::bundleWithPath) ?: NSBundle.mainBundle
    val value = bundle.localizedStringForKey(key,value = null,table = "Localizable")
    check(value != key) { "Widget localization is unavailable" }
    return value
}
internal actual fun widgetSystemLanguageTags(): List<String> = IosAppLocale.systemTags
