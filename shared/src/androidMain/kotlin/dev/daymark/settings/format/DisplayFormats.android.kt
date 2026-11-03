package dev.daymark.settings

import kotlinx.datetime.LocalDate
import java.text.NumberFormat
import java.time.ZoneId
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.util.Locale
import kotlin.time.Instant

actual object DisplayFormats {
    private fun locale(language: AppLanguage) = Locale.forLanguageTag(language.tag)
    actual fun date(date: LocalDate, language: AppLanguage): String =
        java.time.LocalDate.parse(date.toString()).format(
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale(language))
                .withChronology(IsoChronology.INSTANCE)
                .withDecimalStyle(DecimalStyle.of(locale(language)))
        )

    actual fun time(at: Instant, zoneId: String, language: AppLanguage): String =
        DateTimeFormatter.ofPattern("h:mm a", locale(language))
            .withDecimalStyle(DecimalStyle.of(locale(language))).withZone(ZoneId.of(zoneId))
            .format(
                java.time.Instant.ofEpochSecond(
                    at.epochSeconds,
                    at.nanosecondsOfSecond.toLong()
                )
            )

    actual fun number(value: Int, language: AppLanguage): String =
        NumberFormat.getIntegerInstance(locale(language)).apply { isGroupingUsed = false }
            .format(value)
}
