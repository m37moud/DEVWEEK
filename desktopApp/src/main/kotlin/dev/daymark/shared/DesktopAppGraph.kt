package dev.daymark.shared

import dev.daymark.app.AppGraph
import dev.daymark.auth.NativeGoogleAuth
import dev.daymark.data.local.DaymarkDatabase
import dev.daymark.location.LocationCapabilities
import dev.daymark.location.LocationFailure
import dev.daymark.location.LocationGateway
import dev.daymark.location.LocationPermissionPort
import dev.daymark.location.LocationResult
import dev.daymark.location.PlaceGeocoder
import dev.daymark.reminders.ReminderPort
import dev.daymark.widgets.WidgetSink
import kotlin.time.Duration
import org.koin.dsl.module
import org.koin.dsl.onClose

internal fun desktopAppGraph(database: DaymarkDatabase): AppGraph {
    return AppGraph(module {
        single { MacLocation(macLocationExecutable()) }
        single<DaymarkDatabase> { database }
        single<PlaceGeocoder> { PlaceGeocoder { null } }
        single<LocationGateway> {
            if (System.getProperty("os.name").startsWith("Mac")) get<MacLocation>() else DesktopLocation
        }
        single<LocationPermissionPort> {
            if (System.getProperty("os.name").startsWith("Mac")) get<MacLocation>() else DesktopPermissions
        }
        single { DesktopGoogleAuth() } onClose { it?.close() }
        single<NativeGoogleAuth> { get<DesktopGoogleAuth>() }
        single<ReminderPort> { DesktopReminders }
        single<WidgetSink> { DesktopWidgets }
    })
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

private object DesktopReminders : ReminderPort {
    override fun requestPermission(completion: (Boolean) -> Unit) = completion(false)
    override fun replace(remindersJson: String, completion: (Boolean) -> Unit) = completion(false)
    override fun cancelTracker(trackerId: String, completion: (Boolean) -> Unit) = completion(true)
}

private object DesktopWidgets : WidgetSink {
    override fun publish(json: String) = false
    override fun clear() = true
}
