package dev.daymark.models.settings

import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.ThemePreference

data class AppSettings(
    val displayName: String = "",
    val language: LanguagePreference = LanguagePreference.SYSTEM,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val remindersEnabled: Boolean = false
) {
    override fun toString() =
        "AppSettings(displayName=<redacted>, language=$language, theme=$theme)"
}
