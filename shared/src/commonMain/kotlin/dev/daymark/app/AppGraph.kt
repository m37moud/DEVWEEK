package dev.daymark.app

import dev.daymark.attendance.*
import dev.daymark.auth.GoogleAccess
import dev.daymark.data.DaymarkDatabase
import dev.daymark.location.*
import dev.daymark.plan.*
import dev.daymark.reminders.ReminderPort
import dev.daymark.settings.SettingsRepository
import dev.daymark.widgets.WidgetSink
import kotlin.time.Clock

class AppGraph(val db: DaymarkDatabase, val repository: AttendanceRepository, val attendance: AttendanceService,
    val timeOff: TimeOffService, val plans: PlanService, val planReader: PlanReader, val settings: SettingsRepository,
    val location: LocationGateway, val permissions: LocationPermissionPort, val auth: GoogleAccess,
    val reminders: ReminderPort, val widgetSink: WidgetSink, val clock: Clock,
    refreshSchedule: suspend () -> Boolean = { false }) {
    val setup = SetupService(db, clock)
    val platform = hostPlatform()
    val configuration = ConfigurationCoordinator(db,clock,reminders,onScheduleRefresh = refreshSchedule)
}

enum class HostPlatform { ANDROID, IOS, TEST }
internal expect fun hostPlatform(): HostPlatform
