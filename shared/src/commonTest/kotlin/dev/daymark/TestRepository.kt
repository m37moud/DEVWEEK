package dev.daymark

import dev.daymark.attendance.AttendanceRepository
import dev.daymark.models.TodayDashboard
import dev.daymark.models.reporting.ReportHealth
import dev.daymark.plan.PlanReader
import dev.daymark.reminders.ReminderPort
import dev.daymark.repository.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

open class TestRepository : Repository {
    override val attendance: AttendanceRepository get() = error("Unexpected attendance access")
    override val checks: AttendanceChecks get() = error("Unexpected check access")
    override val plans: PlanDataSource get() = error("Unexpected plan access")
    override val timeOff: TimeOffDataSource get() = error("Unexpected time off access")
    override val planReader: PlanReader get() = error("Unexpected schedule access")
    override val setup: SetupDataSource get() = error("Unexpected setup access")
    override val settings: SettingsDataSource get() = error("Unexpected settings access")
    override val application: ApplicationDataSource get() = error("Unexpected application access")
    override val dashboard = object : DashboardDataSource {
        override fun observe() = flowOf(TodayDashboard())
    }
    override val reporting = object : ReportingDataSource {
        override val health = MutableStateFlow(ReportHealth())
        override suspend fun connect(): Nothing = error("Unexpected connection")
        override suspend fun reconnect(): Nothing = error("Unexpected reconnect")
        override suspend fun selectReport(reportId: String): Nothing = error("Unexpected selection")
        override suspend fun rebuildReport(): Nothing = error("Unexpected rebuild")
        override suspend fun createReplacementReport(): Nothing = error("Unexpected replacement")
        override suspend fun disconnect(): Nothing = error("Unexpected disconnect")
        override suspend fun refreshConnection(result: dev.daymark.models.reporting.ConnectionResult?): Nothing =
            error("Unexpected report refresh")

        override fun retryReport(): Nothing = error("Unexpected retry")
    }
    override val dataLifecycle = object : DataLifecycleDataSource {
        override val deleting = MutableStateFlow(false)
        override suspend fun deleteLocalData(): Nothing = error("Unexpected deletion")
    }
}

fun testRepository(
    attendanceSource: AttendanceRepository? = null,
    checksSource: AttendanceChecks? = null,
    plansSource: PlanDataSource? = null,
    timeOffSource: TimeOffDataSource? = null,
    planReaderSource: PlanReader? = null,
    setupSource: SetupDataSource? = null,
    settingsSource: SettingsDataSource? = null
): Repository = object : TestRepository() {
    override val attendance get() = requireNotNull(attendanceSource)
    override val checks get() = requireNotNull(checksSource)
    override val plans get() = requireNotNull(plansSource)
    override val timeOff get() = requireNotNull(timeOffSource)
    override val planReader get() = requireNotNull(planReaderSource)
    override val setup get() = requireNotNull(setupSource)
    override val settings get() = requireNotNull(settingsSource)
}

object TestReminders : ReminderPort {
    override fun requestPermission(completion: (Boolean) -> Unit) = completion(false)
    override fun replace(remindersJson: String, completion: (Boolean) -> Unit) =
        error("Unexpected reminder replacement")

    override fun cancelTracker(trackerId: String, completion: (Boolean) -> Unit) =
        error("Unexpected reminder cancellation")
}
