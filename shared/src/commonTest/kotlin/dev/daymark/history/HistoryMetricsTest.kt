package dev.daymark.history

import dev.daymark.at
import dev.daymark.fixturePlan
import dev.daymark.attendance.evaluateDay
import dev.daymark.plan.TimeOffKind
import dev.daymark.models.attendance.*
import dev.daymark.presentation.features.historydetail.HistoryDetailUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.LocalDate

class HistoryMetricsTest {
    @Test
    fun attendance_includes_late_but_future_attendance_does_not_count() {
        val today = LocalDate(2026, 10, 27)
        val rows = listOf(
            record(AttendanceStatus.PRESENT),
            record(AttendanceStatus.LATE),
            record(AttendanceStatus.ABSENT),
            record(AttendanceStatus.LATE, today = LocalDate(2026, 10, 28))
        )
        val metrics = historyMetrics(rows, today)
        assertEquals(2, metrics.attendance)
        assertEquals(1, metrics.late)
        assertEquals(1, metrics.absent)
        assertEquals(601.seconds, metrics.lateDuration)
    }

    @Test
    fun a_period_spanning_school_and_work_keeps_the_generic_attendance_total() {
        val today = LocalDate(2026, 10, 28)
        val school =
            record(AttendanceStatus.PRESENT).let { it.copy(plan = it.plan.copy(category = TrackerCategory.SCHOOL)) }
        val mission = record(AttendanceStatus.WORK_MISSION, today)
        val metrics = historyMetrics(listOf(school, mission), today)
        assertEquals(2, metrics.attendance)
        assertEquals(1, metrics.workMissions)
        assertEquals(true, metrics.hasNonWorkDays)
        assertEquals(false, historyMetrics(listOf(mission), today).hasNonWorkDays)
    }

    @Test
    fun late_duration_uses_chosen_status_and_clamps_negative_corrections() {
        assertEquals(Duration.ZERO, record(AttendanceStatus.PRESENT).lateDuration())
        assertEquals(
            Duration.ZERO, record(AttendanceStatus.LATE).copy(
                outcome = DayOutcome(
                    AttendanceStatus.LATE,
                    AttendanceSource.MANUAL_CORRECTION,
                    at("08:59:00"),
                    null
                )
            ).lateDuration()
        )
        assertEquals(601.seconds, record(AttendanceStatus.LATE).lateDuration())
    }

    @Test
    fun leave_counts_only_elapsed_vacation_and_details_match() {
        val today = LocalDate(2026, 10, 27)
        val past = LocalDate(2026, 10, 26)
        val future = LocalDate(2026, 10, 28)
        val rows = listOf(
            record(AttendanceStatus.DAY_OFF, past), record(AttendanceStatus.DAY_OFF),
            record(AttendanceStatus.HOLIDAY, past), record(AttendanceStatus.HOLIDAY),
            record(AttendanceStatus.VACATION, past), record(AttendanceStatus.VACATION),
            record(AttendanceStatus.VACATION, future), record(AttendanceStatus.HOLIDAY, future),
            record(AttendanceStatus.DAY_OFF, future)
        )
        val state = HistoryDetailUiState(
            HistoryMetric.LEAVE_REST, past, future, HistoryRange(rows, past, today)
        )
        assertEquals(2, state.metrics.leaveRest)
        assertEquals(listOf(today, past), state.records.map { it.plan.key.date })
        assertEquals(state.metrics.leaveRest, state.records.size)
    }

    @Test
    fun holidays_count_only_elapsed_holidays_and_details_match() {
        val today = LocalDate(2026, 10, 27)
        val past = LocalDate(2026, 10, 26)
        val future = LocalDate(2026, 10, 28)
        val rows = listOf(
            record(AttendanceStatus.HOLIDAY, past), record(AttendanceStatus.HOLIDAY),
            record(AttendanceStatus.HOLIDAY, future), record(AttendanceStatus.VACATION),
            record(AttendanceStatus.DAY_OFF)
        )
        val state = HistoryDetailUiState(
            HistoryMetric.HOLIDAY, past, future, HistoryRange(rows, past, today)
        )
        assertEquals(2, state.metrics.holidays)
        assertEquals(listOf(today, past), state.records.map { it.plan.key.date })
        assertEquals(state.metrics.holidays, state.records.size)
    }

    @Test
    fun registered_leave_counts_for_all_categories_but_weekly_rest_and_holidays_do_not() {
        val today = LocalDate(2026, 10, 27)
        for (category in TrackerCategory.entries) {
            val plan = fixturePlan(date = today).copy(category = category)
            val vacation = HistoryRecord(
                plan, null, evaluateDay(plan, null, TimeOffKind.VACATION, at("12:00:00"))
            )
            val weeklyPlan = plan.copy(isScheduled = false)
            val weeklyRest = HistoryRecord(
                weeklyPlan, null,
                evaluateDay(weeklyPlan, null, TimeOffKind.VACATION, at("12:00:00"))
            )
            val holiday = HistoryRecord(
                plan, null, evaluateDay(plan, null, TimeOffKind.HOLIDAY, at("12:00:00"))
            )
            val state = HistoryDetailUiState(
                HistoryMetric.LEAVE_REST, today, today,
                HistoryRange(listOf(vacation, weeklyRest, holiday), today, today)
            )
            assertEquals(1, state.metrics.leaveRest, category.name)
            assertEquals(listOf(vacation), state.records, category.name)
        }
    }

    @Test
    fun saved_leave_metric_name_remains_compatible() {
        assertEquals(
            HistoryMetric.LEAVE_REST,
            kotlinx.serialization.json.Json.decodeFromString<HistoryMetric>("\"LEAVE_REST\"")
        )
    }

    @Test
    fun empty_range_has_zero_metrics_and_no_details() {
        val today = LocalDate(2026, 10, 27)
        assertEquals(HistoryMetrics(), historyMetrics(emptyList(), today))
        for (metric in HistoryMetric.entries) {
            assertEquals(emptyList(), HistoryDetailUiState(metric, today, today).records)
        }
    }

    private fun record(
        status: AttendanceStatus,
        today: LocalDate = LocalDate(2026, 10, 27)
    ): HistoryRecord {
        val plan = fixturePlan(date = today)
        return HistoryRecord(
            plan,
            null,
            DayOutcome(status, AttendanceSource.MANUAL_CORRECTION, at("09:10:01"), null)
        )
    }
}
