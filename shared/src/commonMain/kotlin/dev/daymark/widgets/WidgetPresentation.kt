package dev.daymark.widgets

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

enum class WidgetAction { CHECK_IN, DISABLED, OPEN_APP }

fun WidgetSnapshot.currentStatus(now: Instant): String? {
    if (now.toLocalDateTime(TimeZone.of(zoneId)).date.toString() != attendanceDate) return null
    if (!isTracked || statusCode !in listOf("SCHEDULED", "AWAITING_CHECK")) return statusCode
    val millis = now.toEpochMilliseconds()
    if (deadlineEpochMillis != null && millis > deadlineEpochMillis) return "ABSENT"
    if (arrivalEpochMillis != null && millis >= arrivalEpochMillis) return "AWAITING_CHECK"
    return statusCode
}

fun WidgetSnapshot.widgetAction(now: Instant): WidgetAction {
    val status = currentStatus(now)
    if (status == null || !isTracked) return WidgetAction.OPEN_APP
    if (status in listOf(
            "PRESENT",
            "LATE",
            "DAY_OFF",
            "HOLIDAY",
            "VACATION"
        )
    ) return WidgetAction.OPEN_APP
    if (arrivalEpochMillis != null && now.toEpochMilliseconds() < arrivalEpochMillis) return WidgetAction.DISABLED
    return if (status in listOf(
            "AWAITING_CHECK",
            "ABSENT"
        )
    ) WidgetAction.CHECK_IN else WidgetAction.OPEN_APP
}
