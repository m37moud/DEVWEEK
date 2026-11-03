package dev.daymark.reminders

import dev.daymark.plan.PlanReader
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlin.time.Instant

suspend fun planReminders(
    now: Instant,
    reader: PlanReader,
    copy: ReminderCopy
): List<ReminderSpec> {
    val context = reader.context() ?: return emptyList()
    val first = now.toLocalDateTime(TimeZone.of(context.zoneId)).date
    return buildList {
        repeat(30) { offset ->
            val date = first.plus(offset, DateTimeUnit.DAY)
            val day = reader.day(date)
            val plan = day.plan
            if (plan.isScheduled && day.off == null && !day.hasSuccessOrCorrection &&
                plan.arrival >= context.trackingStartsAt && plan.arrival >= now
            ) {
                add(
                    ReminderSpec(
                        "daymark.reminder:${context.trackerId}:$date",
                        context.trackerId,
                        date.toString(),
                        plan.arrival.toEpochMilliseconds(),
                        copy.title,
                        copy.body,
                        copy.localeTag
                    )
                )
            }
        }
    }
}

fun encodeReminders(trackerId: String, reminders: List<ReminderSpec>): String {
    require(reminders.size <= 30 && reminders.all { it.trackerId == trackerId })
    require(reminders.map { it.id }.distinct().size == reminders.size)
    return Json.encodeToString(ReminderEnvelope(trackerId, reminders))
}
