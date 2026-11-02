package dev.daymark.app

import dev.daymark.attendance.*
import dev.daymark.auth.GoogleAccess
import dev.daymark.data.DaymarkDatabase
import dev.daymark.location.*
import dev.daymark.plan.*
import dev.daymark.reminders.ReminderPort
import dev.daymark.settings.SettingsRepository
import dev.daymark.widgets.WidgetSink
import dev.daymark.widgets.WidgetPublisher
import kotlin.time.Clock
import dev.daymark.reporting.*

class AppGraph(
    val db: DaymarkDatabase,
    val repository: AttendanceRepository,
    val attendance: AttendanceService,
    val timeOff: TimeOffService,
    val plans: PlanService,
    val planReader: PlanReader,
    val settings: SettingsRepository,
    val location: LocationGateway,
    val permissions: LocationPermissionPort,
    val auth: GoogleAccess,
    val reminders: ReminderPort,
    val widgetSink: WidgetSink,
    val clock: Clock,
    refreshSchedule: suspend () -> Boolean = { false },
    widgetPublisher: WidgetPublisher? = null,
    reportingRuntime: ReportingRuntime? = null,
    stopExternalWork: suspend () -> Unit = {}
) {
    val setup = SetupService(db, clock)
    val platform = hostPlatform()
    val widgets = widgetPublisher ?: WidgetPublisher(db, repository, widgetSink, clock)
    val configuration = ConfigurationCoordinator(
        db, clock, reminders, onScheduleRefresh = refreshSchedule,
        onWidgetRefresh = { widgets.refresh(); true })
    val reporting by lazy { reportingRuntime ?: ReportingRuntime(db, auth, clock, repository) }
    val connection get() = reporting.connection
    val sync get() = reporting.sync
    val lifecycle get() = reporting.lifecycle
    val dataLifecycle by lazy {
        DataLifecycleService(db, reporting.store, stopExternal = {
            lifecycle.stop()
            db.dao().tracker()?.let { configuration.cancelTracker(it.id) }
            stopExternalWork()
        }, forgetCredentials = auth::forget, clearWidget = { widgets.clear(); true })
    }
}

enum class HostPlatform { ANDROID, IOS, TEST }

internal expect fun hostPlatform(): HostPlatform
