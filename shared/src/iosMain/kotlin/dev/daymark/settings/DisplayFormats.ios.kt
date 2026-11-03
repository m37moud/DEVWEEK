package dev.daymark.settings

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeZoneForSecondsFromGMT
import platform.Foundation.timeZoneWithName
import kotlin.time.Instant

actual object DisplayFormats {
    private fun formatter(language: AppLanguage, zoneId: String, pattern: String) =
        NSDateFormatter().apply {
            locale =
                NSLocale(if (language == AppLanguage.AR) "ar_EG@numbers=arab" else "en_US_POSIX")
            calendar = NSCalendar(NSCalendarIdentifierGregorian)
            timeZone = requireNotNull(NSTimeZone.timeZoneWithName(zoneId))
            dateFormat = pattern
        }

    actual fun date(date: LocalDate, language: AppLanguage): String {
        val calendar = NSCalendar(NSCalendarIdentifierGregorian).apply {
            timeZone = NSTimeZone.timeZoneForSecondsFromGMT(0)
        }
        val components = NSDateComponents().apply {
            year = date.year.toLong(); month = date.month.number.toLong(); day =
            date.day.toLong(); hour = 12
        }
        return formatter(
            language,
            "UTC",
            "EEE, d MMM yyyy"
        ).stringFromDate(requireNotNull(calendar.dateFromComponents(components)))
    }

    actual fun time(at: Instant, zoneId: String, language: AppLanguage): String =
        formatter(
            language,
            zoneId,
            "h:mm a"
        ).stringFromDate(NSDate.dateWithTimeIntervalSince1970(at.toEpochMilliseconds() / 1000.0))

    actual fun number(value: Int, language: AppLanguage): String =
        requireNotNull(NSNumberFormatter().apply {
            locale =
                NSLocale(if (language == AppLanguage.AR) "ar_EG@numbers=arab" else "en_US_POSIX"); numberStyle =
            NSNumberFormatterDecimalStyle; usesGroupingSeparator = false; maximumFractionDigits = 0u
        }.stringFromNumber(NSNumber(value)))
}
