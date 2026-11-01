package dev.daymark.app

import androidx.compose.ui.window.ComposeUIViewController
import dev.daymark.attendance.*
import dev.daymark.auth.*
import dev.daymark.data.openIosDatabase
import dev.daymark.location.*
import dev.daymark.plan.*
import dev.daymark.reminders.ReminderPort
import dev.daymark.settings.SettingsRepository
import dev.daymark.widgets.WidgetSink
import platform.UIKit.UIViewController
import kotlin.time.Clock

private var applicationGraph: AppGraph? = null

fun MainViewController(auth: NativeGoogleAuth, reminders: ReminderPort, widget: WidgetSink): UIViewController {
    // One graph per application keeps manual checks and native side effects shared across host recreation.
    val graph = applicationGraph ?: run {
        val clock = Clock.System
        val database = openIosDatabase()
        val repository = RoomAttendanceRepository(database, clock)
        val location = IosLocationGateway(clock)
        AppGraph(database, repository, AttendanceService(repository, location, clock), TimeOffService(database, clock),
            PlanService(database, clock), RoomPlanReader(database, clock), SettingsRepository(database), location,
            IosLocationPermissions(), GoogleAccess(auth), reminders, widget, clock)
    }.also { applicationGraph = it }
    return ComposeUIViewController { App(graph) }
}
