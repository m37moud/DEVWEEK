package dev.daymark.di.ktor

import dev.daymark.app.hostPlatform
import dev.daymark.attendance.AttendanceRepository
import dev.daymark.attendance.AttendanceService
import dev.daymark.attendance.outcome
import dev.daymark.data.local.RoomApplicationDataSource
import dev.daymark.data.local.address.RoomPlaceAddressDataSource
import dev.daymark.data.local.attendance.RoomAttendanceRepository
import dev.daymark.data.local.dashboard.TodayDashboardReader
import dev.daymark.data.local.plan.PlanService
import dev.daymark.data.local.plan.PlanningSnapshotReader
import dev.daymark.data.local.plan.RoomPlanReader
import dev.daymark.data.local.plan.TimeOffService
import dev.daymark.data.local.settings.SettingsRepository
import dev.daymark.data.local.setup.SetupService
import dev.daymark.notifications.AttendanceResultNotifier
import dev.daymark.notifications.AttendanceResultPort
import dev.daymark.notifications.UnsupportedAttendanceResultPort
import dev.daymark.plan.PlanReader
import dev.daymark.repository.AppRepository
import dev.daymark.repository.ApplicationDataSource
import dev.daymark.repository.AttendanceChecks
import dev.daymark.repository.DashboardDataSource
import dev.daymark.repository.PlaceAddressDataSource
import dev.daymark.repository.PlanDataSource
import dev.daymark.repository.Repository
import dev.daymark.repository.SettingsDataSource
import dev.daymark.repository.SetupDataSource
import dev.daymark.repository.TimeOffDataSource
import dev.daymark.widgets.WidgetPublisher
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.time.Clock

internal fun appModules(
    refreshSchedule: suspend () -> Boolean,
    stopExternalWork: suspend () -> Unit
): List<Module> = listOf(
    databaseModule(),
    sharedServicesModule(),
    reportingModule(),
    lifecycleModule(refreshSchedule, stopExternalWork),
    viewModelModule()
)

private fun sharedServicesModule() = module {
    single { PlanningSnapshotReader(get(), get(), get()) }
    single<PlaceAddressDataSource> { RoomPlaceAddressDataSource(get(), get(), get(), get()) }
    single<AttendanceChecks> { get<AttendanceService>() }
    single<PlanDataSource> { get<PlanService>() }
    single<TimeOffDataSource> { get<TimeOffService>() }
    single<SetupDataSource> { get<SetupService>() }
    single<SettingsDataSource> { get<SettingsRepository>() }
    single<DashboardDataSource> { get<TodayDashboardReader>() }
    single<ApplicationDataSource> {
        RoomApplicationDataSource(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            hostPlatform()
        )
    }
    single<Repository> {
        AppRepository(
            attendance = get(),
            checks = get(),
            plans = get(),
            timeOff = get(),
            planReader = get(),
            setup = get(),
            settings = get(),
            dashboard = get(),
            reporting = get(),
            application = get(),
            dataLifecycle = get(),
            placeAddresses = get()
        )
    }

    single<Clock> { Clock.System }
    single {
        RoomAttendanceRepository(
            get(),
            get(),
            get(),
            get(),
            get(),
            get()
        )
    }
    single<AttendanceRepository> { get<RoomAttendanceRepository>() }
    single<AttendanceResultPort> { UnsupportedAttendanceResultPort }
    single {
        AttendanceResultNotifier(get<SettingsDataSource>(), get()) {
            get<AttendanceRepository>().loadCurrentDay(get<Clock>().now())?.let { day ->
                day.plan.key to day.outcome(get<Clock>().now())
            }
        }
    }
    single {
        AttendanceService(
            get(),
            get(),
            get(),
            onCheckCompleted = { key, mode, outcome ->
                if (mode == dev.daymark.models.attendance.CheckMode.USER_INITIATED)
                    get<AttendanceResultNotifier>().completed(key = key, mode = mode, outcome = outcome)
            }
        )
    }
    single { TimeOffService(get(), get(), get(), get(), get(), get(), get(), get()) }
    single { PlanService(get(), get(), get(), get(), get(), get(), get()) }
    single<PlanReader> { RoomPlanReader(get(), get(), get()) }
    single { SettingsRepository(get(), get(), get()) }
    single { SetupService(get(), get(), get(), get(), get(), get()) }
    single { WidgetPublisher(get(), get(), get(), get(), get(), get(), settings = get()) }
    single { TodayDashboardReader(get(), get(), get(), get()) }
}
