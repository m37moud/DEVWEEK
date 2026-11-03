package dev.daymark.settings

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class LocaleControllerTest {
    @Test
    fun fallback_explicit_choice_and_ordered_system_languages() {
        assertEquals(AppLanguage.EN, effectiveLanguage(LanguagePreference.SYSTEM, listOf("fr-FR")))
        assertEquals(AppLanguage.AR, effectiveLanguage(LanguagePreference.AR, listOf("en-US")))
        assertEquals(AppLanguage.EN, effectiveLanguage(LanguagePreference.EN, listOf("ar-EG")))
        assertEquals(
            AppLanguage.AR,
            effectiveLanguage(LanguagePreference.SYSTEM, listOf("fr-FR", "ar-EG"))
        )
        assertTrue(AppLanguage.AR.isRtl)
        assertFalse(AppLanguage.EN.isRtl)
    }

    @Test
    fun whole_day_input_is_localized_but_never_fractional_or_negative() {
        assertEquals(12, parseWholeDays("١٢", AppLanguage.AR))
        assertEquals(12, parseWholeDays("12", AppLanguage.EN))
        assertNull(parseWholeDays("١٫٥", AppLanguage.AR))
        assertNull(parseWholeDays("-1", AppLanguage.EN))
    }

    @Test
    fun gregorian_keys_survive_formatting_in_both_languages() {
        val date = LocalDate(2026, 10, 27)
        assertTrue(DisplayFormats.date(date, AppLanguage.EN).contains("2026"))
        val arabic = DisplayFormats.date(date, AppLanguage.AR)
        assertTrue(arabic.contains("٢٠٢٦"), "Gregorian Arabic display: $arabic")
        assertEquals("2026-10-27", date.toString())
        assertEquals("12", DisplayFormats.number(12, AppLanguage.EN))
        assertEquals("١٢", DisplayFormats.number(12, AppLanguage.AR))
        val at = Instant.parse("2026-10-27T09:00:00Z")
        assertNotEquals(
            DisplayFormats.time(at, "UTC", AppLanguage.EN),
            DisplayFormats.time(at, "Asia/Kathmandu", AppLanguage.EN)
        )
    }
}
