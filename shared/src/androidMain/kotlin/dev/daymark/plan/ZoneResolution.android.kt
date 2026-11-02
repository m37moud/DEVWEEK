package dev.daymark.plan

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.number
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.time.Instant

actual fun resolveLocalArrival(date: LocalDate, time: LocalTime, zoneId: String): Instant {
    val local = LocalDateTime.of(date.year, date.month.number, date.day, time.hour, time.minute, time.second, time.nanosecond)
    val rules = ZoneId.of(zoneId).rules
    val offsets = rules.getValidOffsets(local)
    val resolved = if (offsets.isEmpty()) requireNotNull(rules.getTransition(local)).instant
        else offsets.map { local.toInstant(it) }.minOrNull()!!
    return Instant.fromEpochSeconds(resolved.epochSecond, resolved.nano)
}
