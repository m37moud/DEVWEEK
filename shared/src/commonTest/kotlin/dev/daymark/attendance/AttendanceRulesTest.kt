package dev.daymark.attendance

import dev.daymark.*
import dev.daymark.location.LocationResult
import dev.daymark.plan.*
import kotlinx.datetime.DayOfWeek
import kotlin.math.PI
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class AttendanceRulesTest {
    @Test fun inclusive_grace_and_freshness() {
        val p = fixturePlan()
        assertEquals(AttendanceStatus.PRESENT, acceptedStatus(p, "09:05:00", CheckMode.USER_INITIATED))
        assertEquals(AttendanceStatus.LATE, acceptedStatus(p, "09:05:00.001", CheckMode.USER_INITIATED))
        assertEquals(AttendanceStatus.AWAITING_CHECK, evaluateDay(p, null, null, at("09:05:00")).status)
        assertEquals(AttendanceStatus.ABSENT, evaluateDay(p, null, null, at("09:05:00.001")).status)
        assertEquals(CheckRejection.STALE_FIX, reject(fixtureFix(ageSeconds = 31), at("09:02:31")))
        assertIs<FixVerdict.Accepted>(classifyFix(p, fixtureFix(ageSeconds = 30), CheckMode.USER_INITIATED, at("09:02:30")))
    }
    @Test fun freshness_includes_time_since_receipt() {
        val fix = fixtureFix(ageSeconds = 20)
        assertIs<FixVerdict.Accepted>(classifyFix(fixturePlan(), fix, CheckMode.USER_INITIATED, at("09:02:30")))
        assertEquals(CheckRejection.STALE_FIX, reject(fix, at("09:02:30.001")))
    }
    @Test fun pre_arrival_capture_cannot_confirm_and_automatic_never_late() {
        assertEquals(CheckRejection.BEFORE_WINDOW, reject(fixtureFix(capturedAt = at("08:59:59"), ageSeconds = 2), at("09:00:01")))
        assertEquals(CheckRejection.BEFORE_WINDOW, reject(fixtureFix(capturedAt = at("08:59:59")), at("08:59:59")))
        val late = fixtureFix(capturedAt = at("09:05:00.001"))
        assertEquals(CheckRejection.AFTER_AUTOMATIC_WINDOW,
            assertIs<FixVerdict.Rejected>(classifyFix(fixturePlan(), late, CheckMode.AUTOMATIC, late.receivedAt)).reason)
        val timely = fixtureFix(capturedAt = at("09:05:00")).copy(receivedAt = at("09:05:00.001"), ageAtReceipt = 1.milliseconds)
        assertEquals(AttendanceStatus.PRESENT, assertIs<FixVerdict.Accepted>(classifyFix(fixturePlan(), timely, CheckMode.AUTOMATIC, timely.receivedAt)).status)
    }
    @Test fun accuracy_and_radius_are_separate_inclusive_limits() {
        val longitude = 100.0 / 6_371_000.0 * 180.0 / PI
        val boundary = fixtureFix(accuracy = 50.0, longitude = longitude)
        assertIs<FixVerdict.Accepted>(classifyFix(fixturePlan(), boundary, CheckMode.USER_INITIATED, boundary.receivedAt))
        assertEquals(CheckRejection.INACCURATE_FIX, reject(boundary.copy(horizontalAccuracyMetres = 50.001)))
        assertEquals(CheckRejection.OUTSIDE_AREA, reject(boundary.copy(longitude = longitude * 1.00001)))
    }
    @Test fun malformed_sensor_values_never_confirm() {
        val f = fixtureFix()
        val invalid = listOf(f.copy(latitude = Double.NaN), f.copy(latitude = Double.POSITIVE_INFINITY),
            f.copy(latitude = 90.001), f.copy(longitude = 180.001), f.copy(longitude = Double.NEGATIVE_INFINITY),
            f.copy(horizontalAccuracyMetres = -1.0), f.copy(horizontalAccuracyMetres = Double.NaN),
            f.copy(horizontalAccuracyMetres = Double.POSITIVE_INFINITY), f.copy(ageAtReceipt = (-1).seconds),
            f.copy(ageAtReceipt = Duration.INFINITE), f.copy(capturedAt = f.receivedAt + 1.seconds),
            f.copy(receivedAt = f.receivedAt + 1.seconds))
        for (fix in invalid) assertEquals(CheckRejection.INVALID_FIX, reject(fix), fix.toString())
        assertEquals(CheckRejection.STALE_FIX, reject(f.copy(ageAtReceipt = null)))
    }
    @Test fun different_attendance_date_and_weekly_off_cannot_accept_checks() {
        val f = fixtureFix()
        assertEquals(CheckRejection.STATE_CHANGED, reject(f, Instant.parse("2026-10-28T09:02:00Z")))
        val off = fixturePlan(fixtureSchedule(days = setOf(DayOfWeek.MONDAY)))
        assertEquals(CheckRejection.DAY_OFF, assertIs<FixVerdict.Rejected>(classifyFix(off, f, CheckMode.USER_INITIATED, f.receivedAt)).reason)
    }
    @Test fun derived_day_moves_from_scheduled_through_waiting_to_absent() {
        val p = fixturePlan()
        assertEquals(AttendanceStatus.SCHEDULED, evaluateDay(p, null, null, at("08:59:59")).status)
        assertEquals(AttendanceStatus.AWAITING_CHECK, evaluateDay(p, null, null, at("09:00:00")).status)
        assertEquals(AttendanceStatus.ABSENT, evaluateDay(p, null, null, at("09:06:00")).status)
    }
    @Test fun manual_correction_keeps_chosen_status_even_if_time_disagrees() {
        val correction = AttendanceEvidence(AttendanceStatus.LATE, AttendanceSource.MANUAL_CORRECTION, at("09:01:00"), "اختبار", at("10:00:00"))
        val result = evaluateDay(fixturePlan(), correction, null, at("11:00:00"))
        assertEquals(AttendanceStatus.LATE, result.status)
        assertEquals(AttendanceSource.MANUAL_CORRECTION, result.source)
        assertEquals(at("09:01:00"), result.confirmedAt)
        assertEquals(AttendanceStatus.ABSENT,
            evaluateDay(fixturePlan(), correction.copy(status = AttendanceStatus.ABSENT, confirmedAt = null), null, at("09:01:00")).status)
    }
    @Test fun time_off_wins_without_erasing_underlying_evidence() {
        val p = fixturePlan()
        val evidence = AttendanceEvidence(AttendanceStatus.PRESENT, AttendanceSource.MANUAL_GPS, at("09:01:00"), null, at("09:01:00"))
        for ((kind, status) in listOf(TimeOffKind.HOLIDAY to AttendanceStatus.HOLIDAY, TimeOffKind.VACATION to AttendanceStatus.VACATION)) {
            val result = evaluateDay(p, evidence, kind, at("12:00:00"))
            assertEquals(status, result.status); assertEquals(kind, result.offReason)
            assertEquals(AttendanceSource.MANUAL_TIME_OFF, result.source); assertNull(result.confirmedAt)
        }
        assertEquals(AttendanceStatus.PRESENT, evaluateDay(p, evidence, null, at("12:00:00")).status)
        assertEquals(at("09:01:00"), evidence.confirmedAt)
    }
    @Test fun holiday_precedes_weekly_off_which_precedes_vacation() {
        val off = fixturePlan(fixtureSchedule(days = setOf(DayOfWeek.MONDAY)))
        assertEquals(AttendanceStatus.HOLIDAY, evaluateDay(off, null, TimeOffKind.HOLIDAY, at("12:00:00")).status)
        assertEquals(AttendanceStatus.DAY_OFF, evaluateDay(off, null, TimeOffKind.VACATION, at("12:00:00")).status)
    }
    @Test fun stored_success_survives_inferred_absence_and_clock_rollback() {
        for (status in listOf(AttendanceStatus.PRESENT, AttendanceStatus.LATE)) {
            val evidence = AttendanceEvidence(status, AttendanceSource.MANUAL_GPS, at("10:00:00"), null, at("10:00:00"))
            assertEquals(status, evaluateDay(fixturePlan(), evidence, null, at("12:00:00")).status)
            assertEquals(status, evaluateDay(fixturePlan(), evidence, null, at("08:00:00")).status)
        }
    }
    private fun reject(fix: LocationResult.Fix, now: Instant = at("09:02:00")) =
        assertIs<FixVerdict.Rejected>(classifyFix(fixturePlan(), fix, CheckMode.USER_INITIATED, now)).reason
}
