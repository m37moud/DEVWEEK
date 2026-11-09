package dev.daymark.di.ktor

import dev.daymark.app.hostPlatform
import dev.daymark.attendance.AttendanceRepository
import dev.daymark.attendance.AttendanceService
import dev.daymark.auth.GoogleAccess
import dev.daymark.data.local.DaymarkDatabase
import dev.daymark.data.local.address.RoomPlaceAddressDataSource
import dev.daymark.repository.PlaceAddressDataSource
import dev.daymark.data.local.RoomApplicationDataSource
import dev.daymark.data.local.application.ConfigurationCoordinator
import dev.daymark.data.local.application.DataLifecycleService
import dev.daymark.data.local.attendance.RoomAttendanceRepository
import dev.daymark.data.local.dashboard.TodayDashboardReader
import dev.daymark.data.local.plan.PlanService
import dev.daymark.data.local.plan.RoomPlanReader
import dev.daymark.data.local.plan.TimeOffService
import dev.daymark.data.local.reporting.ReportingRuntime
import dev.daymark.data.local.reporting.RoomReportStore
import dev.daymark.data.local.settings.SettingsRepository
import dev.daymark.data.local.setup.SetupService
import dev.daymark.data.remote.data_source.data.ktor.GoogleReportTransport
import dev.daymark.data.remote.data_source.domain.ReportConnectionTransport
import dev.daymark.data.remote.data_source.domain.ReportTransport
import dev.daymark.plan.PlanReader
import dev.daymark.presentation.features.app.AppViewModel
import dev.daymark.presentation.features.history.HistoryViewModel
import dev.daymark.presentation.features.plan.PlanViewModel
import dev.daymark.presentation.features.settings.SettingsViewModel
import dev.daymark.presentation.features.setup.SetupViewModel
import dev.daymark.presentation.features.today.TodayViewModel
import dev.daymark.repository.AppRepository
import dev.daymark.repository.ApplicationDataSource
import dev.daymark.repository.AttendanceChecks
import dev.daymark.repository.DashboardDataSource
import dev.daymark.repository.DataLifecycleDataSource
import dev.daymark.repository.PlanDataSource
import dev.daymark.repository.ReportingDataSource
import dev.daymark.repository.Repository
import dev.daymark.repository.SettingsDataSource
import dev.daymark.repository.SetupDataSource
import dev.daymark.repository.TimeOffDataSource
import dev.daymark.widgets.WidgetPublisher
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.koin.dsl.onClose

internal fun attendanceModule() = module {
    single<PlaceAddressDataSource> { RoomPlaceAddressDataSource(get(), get()) }
    single<AttendanceChecks> { get<AttendanceService>() }
    single<PlanDataSource> { get<PlanService>() }
    single<TimeOffDataSource> { get<TimeOffService>() }
    single<SetupDataSource> { get<SetupService>() }
    single<SettingsDataSource> { get<SettingsRepository>() }
    single<DashboardDataSource> { get<TodayDashboardReader>() }
    single<ReportingDataSource> { get<ReportingRuntime>() }
    single<DataLifecycleDataSource> { get<DataLifecycleService>() }
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
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get()
        )
    }

    single<Clock> { Clock.System }
    single<AttendanceRepository> { RoomAttendanceRepository(get(), get()) }
    single { AttendanceService(get(), get(), get()) }
    single { TimeOffService(get(), get(), get()) }
    single { PlanService(get(), get(), get()) }
    single<PlanReader> { RoomPlanReader(get(), get()) }
    single { SettingsRepository(get()) }
    single { SetupService(get(), get(), get()) }
    single { GoogleAccess(get()) }
    single { WidgetPublisher(get(), get(), get(), get(), settings = get()) }
    single { TodayDashboardReader(get(), get()) }
    single { RoomReportStore(get(), get()) }
    single {
        HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
            }
        }
    }
    single { GoogleReportTransport(get()) }
    single<ReportConnectionTransport> { get<GoogleReportTransport>() }
    single<ReportTransport> { get<GoogleReportTransport>() }
    single {
        ReportingRuntime(
            get(),
            get(),
            get(),
            get(),
            httpClient = get(),
            store = get(),
            reportTransport = get()
        )
    } onClose { it?.close() }
}

internal fun lifecycleModule(
    refreshSchedule: suspend () -> Boolean,
    stopExternalWork: suspend () -> Unit
) = module {
    single {
        ConfigurationCoordinator(
            get(), get(), get(), onScheduleRefresh = refreshSchedule,
            onWidgetRefresh = { get<WidgetPublisher>().refresh(); true }, reader = get()
        )
    }
    single {
        DataLifecycleService(
            get(),
            get<ReportingRuntime>().store,
            stopExternal = {
                get<ReportingRuntime>().lifecycle.stop()
                get<DaymarkDatabase>().dao().tracker()
                    ?.let { get<ConfigurationCoordinator>().cancelTracker(it.id) }
                stopExternalWork()
            },
            forgetCredentials = get<GoogleAccess>()::forget,
            clearWidget = { get<WidgetPublisher>().clear(); true },
            settings = get()
        )
    }
}

internal fun viewModelModule() = module {
    viewModel { AppViewModel(get()) }
    viewModel { SetupViewModel(get(), get(), get(), get()) }
    viewModel { TodayViewModel(get(), get(), get(), initiallyActive = false) }
    viewModel { PlanViewModel(get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get()) }
    viewModel { (initialDate: LocalDate) ->
        val repository = get<Repository>()
        val clock = get<Clock>()
        HistoryViewModel(
            repository, initialDate, clock = clock,
            today = {
                repository.attendance.loadCurrentDay(clock.now())?.plan?.key?.date ?: initialDate
            },
            refreshBoundary = {
                repository.attendance.loadCurrentDay(clock.now())?.plan?.let { day ->
                    day.key.date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(TimeZone.of(day.zoneId))
                }
            }
        )
    }
}
