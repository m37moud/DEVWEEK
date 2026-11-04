package dev.daymark.shared

import dev.daymark.app.AppGraph
import dev.daymark.attendance.AttendanceService
import dev.daymark.attendance.RoomAttendanceRepository
import dev.daymark.auth.GoogleAccess
import dev.daymark.auth.NativeAuthReply
import dev.daymark.auth.NativeAuthStatus
import dev.daymark.auth.NativeGoogleAuth
import dev.daymark.data.DaymarkDatabase
import dev.daymark.location.LocationCapabilities
import dev.daymark.location.LocationFailure
import dev.daymark.location.LocationGateway
import dev.daymark.location.LocationPermissionPort
import dev.daymark.location.LocationResult
import dev.daymark.plan.PlanService
import dev.daymark.plan.RoomPlanReader
import dev.daymark.plan.TimeOffService
import dev.daymark.reminders.ReminderPort
import dev.daymark.settings.SettingsRepository
import dev.daymark.widgets.WidgetSink
import kotlin.time.Clock
import kotlin.time.Duration

internal fun desktopAppGraph(database: DaymarkDatabase): AppGraph {
    val clock = Clock.System
    val repository = RoomAttendanceRepository(database, clock)
    val macLocation =
        if (System.getProperty("os.name").startsWith("Mac")) MacLocation(macLocationExecutable()) else null
    return AppGraph(
        db = database,
        repository = repository,
        attendance = AttendanceService(repository, macLocation ?: DesktopLocation, clock),
        timeOff = TimeOffService(database, clock),
        plans = PlanService(database, clock),
        planReader = RoomPlanReader(database, clock),
        settings = SettingsRepository(database),
        location = macLocation ?: DesktopLocation,
        permissions = macLocation ?: DesktopPermissions,
        auth = GoogleAccess(DesktopGoogleAuth),
        reminders = DesktopReminders,
        widgetSink = DesktopWidgets,
        clock = clock
    )
}

// shortcut: desktop is a development host; implement native adapters before offering desktop delivery.
private object DesktopLocation : LocationGateway {
    override suspend fun requestFix(budget: Duration) =
        LocationResult.Failure(LocationFailure.UNAVAILABLE)
}

private object DesktopPermissions : LocationPermissionPort {
    override fun current() = LocationCapabilities(false, false, false, false)
    override suspend fun requestForeground() = current()
    override suspend fun requestBackground() = current()
    override fun openSystemSettings() = Unit
}

private object DesktopGoogleAuth : NativeGoogleAuth {
    override fun authorize(interactive: Boolean, completion: (NativeAuthReply) -> Unit) {
        completion(NativeAuthReply(NativeAuthStatus.FAILED, null, "authorization_failed"))
    }

    override fun forget(completion: (Boolean) -> Unit) = completion(true)
}

private object DesktopReminders : ReminderPort {
    override fun requestPermission(completion: (Boolean) -> Unit) = completion(false)
    override fun replace(remindersJson: String, completion: (Boolean) -> Unit) = completion(false)
    override fun cancelTracker(trackerId: String, completion: (Boolean) -> Unit) = completion(true)
}

private object DesktopWidgets : WidgetSink {
    override fun publish(json: String) = false
    override fun clear() = true
}
