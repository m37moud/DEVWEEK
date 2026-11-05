package dev.daymark.app

import dev.daymark.attendance.AttendanceService
import dev.daymark.data.local.DaymarkDatabase
import dev.daymark.data.local.application.ConfigurationCoordinator
import dev.daymark.data.local.application.DataLifecycleService
import dev.daymark.data.local.reporting.ReportingRuntime
import dev.daymark.data.local.settings.SettingsRepository
import dev.daymark.data.local.setup.SetupService
import dev.daymark.di.ktor.attendanceModule
import dev.daymark.di.ktor.lifecycleModule
import dev.daymark.di.ktor.viewModelModule
import dev.daymark.location.LocationPermissionPort
import dev.daymark.logging.AppLogger
import dev.daymark.plan.PlanReader
import dev.daymark.reminders.ReminderPort
import dev.daymark.repository.Repository
import dev.daymark.widgets.WidgetPublisher
import kotlin.time.Clock
import org.koin.core.module.Module
import org.koin.dsl.koinApplication

class AppGraph(
    platformModule: Module,
    refreshSchedule: suspend () -> Boolean = { false },
    stopExternalWork: suspend () -> Unit = {}
) {
    val container = koinApplication {
        modules(
            attendanceModule(),
            lifecycleModule(refreshSchedule, stopExternalWork),
            viewModelModule(),
            platformModule
        )
    }
    val koin get() = container.koin
    val db: DaymarkDatabase by koin.inject()
    val repository: Repository by koin.inject()
    val attendance: AttendanceService by koin.inject()
    val planReader: PlanReader by koin.inject()
    val settings: SettingsRepository by koin.inject()
    val setup: SetupService by koin.inject()
    val permissions: LocationPermissionPort by koin.inject()
    val reminders: ReminderPort by koin.inject()
    val clock: Clock by koin.inject()
    val widgets: WidgetPublisher by koin.inject()
    val configuration: ConfigurationCoordinator by koin.inject()
    val reporting: ReportingRuntime by koin.inject()
    val dataLifecycle: DataLifecycleService by koin.inject()
    val platform = hostPlatform()
    val connection get() = reporting.connection
    val sync get() = reporting.sync
    val lifecycle get() = reporting.lifecycle

    init {
        AppLogger.i("AppGraph", "Dependency container initialized")
    }

    fun close() {
        container.close()
        AppLogger.d("AppGraph", "Dependency container closed")
    }
}

enum class HostPlatform { ANDROID, IOS, TEST }

internal expect fun hostPlatform(): HostPlatform
