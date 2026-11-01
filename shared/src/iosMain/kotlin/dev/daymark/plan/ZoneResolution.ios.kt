@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.daymark.plan

import kotlinx.datetime.*
import platform.Foundation.*
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

actual fun resolveLocalArrival(date: LocalDate, time: LocalTime, zoneId: String): Instant {
    val zone = TimeZone.of(zoneId)
    val calendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian)
    calendar.timeZone = requireNotNull(NSTimeZone.timeZoneWithName(zoneId))
    val components = NSDateComponents().apply {
        year = date.year.toLong(); month = date.month.number.toLong(); day = date.day.toLong()
        hour = time.hour.toLong(); minute = time.minute.toLong(); second = time.second.toLong()
        nanosecond = 0
    }
    val anchor = LocalDateTime(date, LocalTime(0, 0)).toInstant(TimeZone.UTC) - 36.hours
    val resolved = requireNotNull(calendar.nextDateAfterDate(
        NSDate.dateWithTimeIntervalSince1970(anchor.epochSeconds.toDouble()),
        matchingComponents = components, options = NSCalendarMatchNextTime or NSCalendarMatchFirst,
    )) { "Arrival could not be resolved" }
    val seconds = resolved.timeIntervalSince1970.roundToLong()
    val local = Instant.fromEpochSeconds(seconds).toLocalDateTime(zone)
    // NSDate cannot represent nanoseconds exactly; preserve them only for an actual matching time.
    val nanos = if (local.date == date && local.time.hour == time.hour && local.time.minute == time.minute && local.time.second == time.second)
        time.nanosecond else 0
    return Instant.fromEpochSeconds(seconds, nanos)
}
