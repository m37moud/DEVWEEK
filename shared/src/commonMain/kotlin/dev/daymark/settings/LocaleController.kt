package dev.daymark.settings

enum class LanguagePreference { SYSTEM, EN, AR }
enum class AppLanguage(val tag: String, val isRtl: Boolean) { EN("en", false), AR("ar", true) }
enum class ThemePreference { SYSTEM, LIGHT, DARK }

fun effectiveLanguage(preference: LanguagePreference, systemTags: List<String>): AppLanguage =
    when (preference) {
        LanguagePreference.EN -> AppLanguage.EN
        LanguagePreference.AR -> AppLanguage.AR
        LanguagePreference.SYSTEM -> systemTags.firstNotNullOfOrNull { tag ->
            when (tag.substringBefore('-').substringBefore('_').lowercase()) {
                "ar" -> AppLanguage.AR; "en" -> AppLanguage.EN; else -> null
            }
        } ?: AppLanguage.EN
    }

fun parseWholeDays(text: String, language: AppLanguage): Int? =
    dev.daymark.plan.parseWholeDays(text)
