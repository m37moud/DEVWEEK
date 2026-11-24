package dev.daymark.data.local.plan

import dev.daymark.data.local.dao.AttendanceDao
import dev.daymark.data.local.dao.PlanningDao
import dev.daymark.data.local.dao.ProfileDao
import dev.daymark.data.local.settings.ATTENDANCE_PERIOD_KEY
import dev.daymark.data.local.settings.attendancePeriodSettings
import dev.daymark.models.plan.AnnualVacationPolicy
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** Assembles planning data inside the caller-owned transaction. */
class PlanningSnapshotReader(
    private val profileDao: ProfileDao,
    private val attendanceDao: AttendanceDao,
    private val planningDao: PlanningDao
) {
    internal suspend fun load(
        now: Instant,
        attendanceDates: ClosedRange<LocalDate>? = null
    ): PlanningStore? {
        val tracker = profileDao.tracker() ?: return null
        val versions = planningDao.versions(tracker.id)
        if (versions.isEmpty()) return null
        val attendance = if (attendanceDates == null) attendanceDao.days(trackerId = tracker.id)
        else attendanceDao.daysBetween(
            trackerId = tracker.id,
            from = attendanceDates.start.toString(),
            through = attendanceDates.endInclusive.toString()
        )
        return PlanningStore(
            tracker = tracker,
            versions = versions,
            rows = attendance.associateBy { LocalDate.parse(input = it.date) },
            entries = planningDao.timeOff(trackerId = tracker.id).map { it.entry() },
            adjustments = planningDao.balanceAdjustments(trackerId = tracker.id),
            now = now,
            policies = planningDao.annualVacationPolicies(trackerId = tracker.id).map { policy ->
                AnnualVacationPolicy(
                    effectiveYear = policy.effectiveYear,
                    annualAllowanceDays = policy.annualAllowanceDays,
                    carryOverUnusedDays = policy.carryOverUnusedDays
                )
            },
            periodSettings = attendancePeriodSettings(
                stored = profileDao.setting(key = ATTENDANCE_PERIOD_KEY)?.value
            )
        )
    }
}
