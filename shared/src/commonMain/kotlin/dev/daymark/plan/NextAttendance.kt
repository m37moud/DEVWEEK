package dev.daymark.plan

import dev.daymark.attendance.*
import kotlinx.datetime.*
import kotlin.time.Instant

data class ScheduledCheck(val key: DayKey, val arrival: Instant, val deadline: Instant)
data class ScheduleContext(val trackerId: String, val zoneId: String, val trackingStartsAt: Instant, val automaticEnabled: Boolean)
data class PlannedDay(val plan: DayPlan, val off: TimeOffKind?, val hasSuccessOrCorrection: Boolean, val automaticConsumed: Boolean)
interface PlanReader {
    suspend fun context(): ScheduleContext?
    suspend fun day(date: LocalDate): PlannedDay
}
suspend fun nextAttendance(now: Instant, plans: PlanReader): ScheduledCheck? {
    val context = plans.context() ?: return null
    if (!context.automaticEnabled) return null
    var date = now.toLocalDateTime(TimeZone.of(context.zoneId)).date
    repeat(3660) {
        val day = plans.day(date)
        val plan = day.plan
        if (plan.isScheduled && day.off == null && !day.hasSuccessOrCorrection && !day.automaticConsumed &&
            plan.arrival >= context.trackingStartsAt && plan.deadline >= now)
            return ScheduledCheck(plan.key, plan.arrival, plan.deadline)
        date = date.plus(1, DateTimeUnit.DAY)
    }
    return null
}
