package dev.daymark.attendance

import dev.daymark.plan.TimeOffKind
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

data class DayKey(val trackerId: String, val date: LocalDate) {
    init { require(trackerId.isNotBlank()) }
}
enum class TrackerCategory { WORK, SCHOOL, COURSE }
enum class AttendanceStatus { SCHEDULED, AWAITING_CHECK, PRESENT, LATE, ABSENT, DAY_OFF, HOLIDAY, VACATION }
enum class AttendanceSource { NONE, AUTOMATIC_GPS, MANUAL_GPS, MANUAL_CORRECTION, MANUAL_TIME_OFF }
enum class CheckMode { AUTOMATIC, USER_INITIATED }

data class RegisteredPlace(val latitude: Double, val longitude: Double, val radiusMetres: Int, val version: Long) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        require(radiusMetres in 100..500 && version >= 0)
    }
    override fun toString(): String = "RegisteredPlace(coordinates=<redacted>, radiusMetres=$radiusMetres, version=$version)"
}

data class DayPlan(
    val key: DayKey, val category: TrackerCategory, val arrival: Instant, val deadline: Instant,
    val zoneId: String, val scheduleVersion: Long, val place: RegisteredPlace, val isScheduled: Boolean,
) {
    init { require(deadline >= arrival && scheduleVersion >= 0) }
}

data class AttendanceEvidence(
    val status: AttendanceStatus, val source: AttendanceSource, val confirmedAt: Instant?,
    val note: String?, val updatedAt: Instant,
) {
    init {
        if (status == AttendanceStatus.PRESENT || status == AttendanceStatus.LATE) require(confirmedAt != null)
    }
    override fun toString(): String = "AttendanceEvidence(status=$status, source=$source, note=<redacted>)"
}

data class DayOutcome(val status: AttendanceStatus, val source: AttendanceSource, val confirmedAt: Instant?, val offReason: TimeOffKind?)
sealed interface FixVerdict {
    data class Accepted(val status: AttendanceStatus, val confirmedAt: Instant) : FixVerdict
    data class Rejected(val reason: CheckRejection) : FixVerdict
}
enum class CheckRejection {
    BEFORE_WINDOW, AFTER_AUTOMATIC_WINDOW, STALE_FIX, INVALID_FIX, INACCURATE_FIX, OUTSIDE_AREA,
    NO_TRACKER, DAY_OFF, LOCATION_DENIED, LOCATION_DISABLED, LOCATION_TIMEOUT, UNAVAILABLE, CANCELLED, STATE_CHANGED,
}
