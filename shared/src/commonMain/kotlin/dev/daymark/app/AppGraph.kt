package dev.daymark.app

import dev.daymark.attendance.AttendanceRepository
import dev.daymark.attendance.AttendanceService
import dev.daymark.data.DaymarkDatabase
import dev.daymark.di.attendanceModule
import dev.daymark.di.lifecycleModule
import dev.daymark.di.viewModelModule
import dev.daymark.location.LocationPermissionPort
import dev.daymark.logging.AppLogger
import dev.daymark.plan.PlanReader
import dev.daymark.reminders.ReminderPort
import dev.daymark.reporting.ReportingRuntime
import dev.daymark.settings.SettingsRepository
import dev.daymark.widgets.WidgetPublisher
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import kotlin.time.Clock

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
    val repository: AttendanceRepository by koin.inject()
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
