package dev.daymark.models.settings

import dev.daymark.attendance.DEFAULT_AUTOMATIC_ATTEMPT_LIMIT
import dev.daymark.history.AttendancePeriodSettings
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.ThemePreference

data class AppSettings(
    val displayName: String = "",
    val language: LanguagePreference = LanguagePreference.SYSTEM,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val remindersEnabled: Boolean = false,
    val resultNotificationsEnabled: Boolean = false,
    val attendancePeriod: AttendancePeriodSettings = AttendancePeriodSettings(),
    val resultFailureSoundEnabled: Boolean = false,
    val automaticAttemptLimit: Int = DEFAULT_AUTOMATIC_ATTEMPT_LIMIT
) {
    override fun toString() =
        "AppSettings(displayName=<redacted>, language=$language, theme=$theme)"
}
