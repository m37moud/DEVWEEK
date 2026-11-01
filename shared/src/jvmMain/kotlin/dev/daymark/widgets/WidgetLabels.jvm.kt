package dev.daymark.widgets

import dev.daymark.attendance.AttendanceStatus
import dev.daymark.settings.AppLanguage
import java.util.Locale

// The JVM target is a test harness; publication tests inject their label provider.
internal actual fun widgetStatusLabel(status: AttendanceStatus?,language: AppLanguage): String = error("No native widget on the test-only JVM target")
internal actual fun widgetSystemLanguageTags(): List<String> = listOf(Locale.getDefault().toLanguageTag())
