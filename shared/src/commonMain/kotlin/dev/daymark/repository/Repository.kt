package dev.daymark.repository

import dev.daymark.attendance.AttendanceRepository
import dev.daymark.plan.PlanReader

interface Repository {
    val placeAddresses: PlaceAddressDataSource
    val attendance: AttendanceRepository
    val checks: AttendanceChecks
    val plans: PlanDataSource
    val timeOff: TimeOffDataSource
    val planReader: PlanReader
    val setup: SetupDataSource
    val settings: SettingsDataSource
    val dashboard: DashboardDataSource
    val reporting: ReportingDataSource
    val application: ApplicationDataSource
    val dataLifecycle: DataLifecycleDataSource
}

class AppRepository(
    override val attendance: AttendanceRepository,
    override val checks: AttendanceChecks,
    override val plans: PlanDataSource,
    override val timeOff: TimeOffDataSource,
    override val planReader: PlanReader,
    override val setup: SetupDataSource,
    override val settings: SettingsDataSource,
    override val dashboard: DashboardDataSource,
    override val reporting: ReportingDataSource,
    override val application: ApplicationDataSource,
    override val dataLifecycle: DataLifecycleDataSource,
    override val placeAddresses: PlaceAddressDataSource
) : Repository
