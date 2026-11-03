package dev.daymark.attendance

import dev.daymark.location.LocationResult
import dev.daymark.plan.TimeOffKind
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

fun evaluateDay(
    plan: DayPlan,
    evidence: AttendanceEvidence?,
    off: TimeOffKind?,
    now: Instant
): DayOutcome {
    if (off == TimeOffKind.HOLIDAY) return DayOutcome(
        AttendanceStatus.HOLIDAY,
        AttendanceSource.MANUAL_TIME_OFF,
        null,
        off
    )
    if (!plan.isScheduled) return DayOutcome(
        AttendanceStatus.DAY_OFF,
        AttendanceSource.NONE,
        null,
        null
    )
    if (off == TimeOffKind.VACATION) return DayOutcome(
        if (plan.category == TrackerCategory.WORK) AttendanceStatus.VACATION else AttendanceStatus.DAY_OFF,
        AttendanceSource.MANUAL_TIME_OFF, null, off
    )
    if (evidence != null) {
        val keep = when (evidence.source) {
            AttendanceSource.MANUAL_CORRECTION -> evidence.status in setOf(
                AttendanceStatus.PRESENT,
                AttendanceStatus.LATE,
                AttendanceStatus.ABSENT
            )

            AttendanceSource.AUTOMATIC_GPS -> evidence.status == AttendanceStatus.PRESENT
            AttendanceSource.MANUAL_GPS -> evidence.status == AttendanceStatus.PRESENT || evidence.status == AttendanceStatus.LATE
            else -> false
        }
        if (keep) return DayOutcome(evidence.status, evidence.source, evidence.confirmedAt, null)
    }
    val status = when {
        now < plan.arrival -> AttendanceStatus.SCHEDULED
        now <= plan.deadline -> AttendanceStatus.AWAITING_CHECK
        else -> AttendanceStatus.ABSENT
    }
    return DayOutcome(status, AttendanceSource.NONE, null, null)
}

fun classifyFix(plan: DayPlan, fix: LocationResult.Fix, mode: CheckMode, now: Instant): FixVerdict {
    fun reject(reason: CheckRejection) = FixVerdict.Rejected(reason)
    if (!plan.isScheduled) return reject(CheckRejection.DAY_OFF)
    if (now.toLocalDateTime(TimeZone.of(plan.zoneId)).date != plan.key.date) return reject(
        CheckRejection.STATE_CHANGED
    )
    val age = fix.ageAtReceipt
    if (!fix.latitude.isFinite() || fix.latitude !in -90.0..90.0 ||
        !fix.longitude.isFinite() || fix.longitude !in -180.0..180.0 ||
        !fix.horizontalAccuracyMetres.isFinite() || fix.horizontalAccuracyMetres < 0 ||
        fix.capturedAt > fix.receivedAt || fix.receivedAt > now ||
        (age != null && (!age.isFinite() || age.isNegative()))
    ) return reject(CheckRejection.INVALID_FIX)
    if (now < plan.arrival || fix.capturedAt < plan.arrival) return reject(CheckRejection.BEFORE_WINDOW)
    if (mode == CheckMode.AUTOMATIC && fix.capturedAt > plan.deadline) return reject(CheckRejection.AFTER_AUTOMATIC_WINDOW)
    if (age == null || age + (now - fix.receivedAt) > 30.seconds) return reject(CheckRejection.STALE_FIX)
    if (fix.horizontalAccuracyMetres > 50.0) return reject(CheckRejection.INACCURATE_FIX)
    if (distanceMetres(plan.place, fix) > plan.place.radiusMetres + 1e-7) return reject(
        CheckRejection.OUTSIDE_AREA
    )
    return FixVerdict.Accepted(
        if (fix.capturedAt <= plan.deadline) AttendanceStatus.PRESENT else AttendanceStatus.LATE,
        fix.capturedAt
    )
}

private fun distanceMetres(place: RegisteredPlace, fix: LocationResult.Fix): Double {
    val lat1 = place.latitude * PI / 180.0
    val lat2 = fix.latitude * PI / 180.0
    val dLat = sin((lat2 - lat1) / 2)
    val dLon = sin((fix.longitude - place.longitude) * PI / 360.0)
    val a = (dLat * dLat + cos(lat1) * cos(lat2) * dLon * dLon).coerceIn(0.0, 1.0)
    return 2 * 6_371_000.0 * atan2(sqrt(a), sqrt(1 - a))
}
