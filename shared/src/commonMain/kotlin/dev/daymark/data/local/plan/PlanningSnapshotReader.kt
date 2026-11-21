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
    internal suspend fun load(now: Instant): PlanningStore? {
        val tracker = profileDao.tracker() ?: return null
        val versions = planningDao.versions(tracker.id)
        if (versions.isEmpty()) return null
        return PlanningStore(
            tracker,
            versions,
            attendanceDao.days(tracker.id).associateBy { LocalDate.parse(it.date) },
            planningDao.timeOff(tracker.id).map { it.entry() },
            planningDao.balanceAdjustments(tracker.id),
            now,
            planningDao.annualVacationPolicies(tracker.id).map { policy ->
                AnnualVacationPolicy(policy.effectiveYear, policy.annualAllowanceDays, policy.carryOverUnusedDays)
            },
            attendancePeriodSettings(profileDao.setting(ATTENDANCE_PERIOD_KEY)?.value)
        )
    }
}
