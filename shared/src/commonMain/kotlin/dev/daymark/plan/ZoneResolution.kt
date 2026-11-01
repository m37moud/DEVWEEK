package dev.daymark.plan

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

/** Gaps use the first valid instant; overlaps use the earlier occurrence. */
expect fun resolveLocalArrival(date: LocalDate, time: LocalTime, zoneId: String): Instant
