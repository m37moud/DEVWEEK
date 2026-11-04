package dev.daymark.di

import dev.daymark.app.ConfigurationCoordinator
import dev.daymark.app.DataLifecycleService
import dev.daymark.app.SetupService
import dev.daymark.attendance.AttendanceRepository
import dev.daymark.attendance.AttendanceService
import dev.daymark.attendance.RoomAttendanceRepository
import dev.daymark.auth.GoogleAccess
import dev.daymark.data.DaymarkDatabase
import dev.daymark.plan.PlanReader
import dev.daymark.plan.PlanService
import dev.daymark.plan.RoomPlanReader
import dev.daymark.plan.TimeOffService
import dev.daymark.reporting.ReportingRuntime
import dev.daymark.reporting.RoomReportStore
import dev.daymark.reporting.GoogleReportTransport
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import dev.daymark.settings.SettingsRepository
import dev.daymark.ui.HistoryViewModel
import dev.daymark.ui.PlanViewModel
import dev.daymark.ui.SettingsViewModel
import dev.daymark.ui.SetupViewModel
import dev.daymark.ui.TodayDashboardReader
import dev.daymark.ui.TodayViewModel
import dev.daymark.widgets.WidgetPublisher
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.koin.dsl.onClose
import kotlin.time.Clock

internal fun attendanceModule() = module {
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
                get<DaymarkDatabase>().dao().tracker()?.let { get<ConfigurationCoordinator>().cancelTracker(it.id) }
                stopExternalWork()
            },
            forgetCredentials = get<GoogleAccess>()::forget,
            clearWidget = { get<WidgetPublisher>().clear(); true },
            settings = get()
        )
    }
}

internal fun viewModelModule() = module {
    viewModel { SetupViewModel(get(), get(), get(), get()) }
    viewModel { TodayViewModel(get(), get(), get(), get(), get(), initiallyActive = false) }
    viewModel { PlanViewModel(get(), get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get()) }
    viewModel { (initialDate: LocalDate) ->
        val repository = get<AttendanceRepository>()
        val clock = get<Clock>()
        HistoryViewModel(
            repository, initialDate, clock = clock,
            today = { repository.loadCurrentDay(clock.now())?.plan?.key?.date ?: initialDate },
            refreshBoundary = {
                repository.loadCurrentDay(clock.now())?.plan?.let { day ->
                    day.key.date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(TimeZone.of(day.zoneId))
                }
            }
        )
    }
}
