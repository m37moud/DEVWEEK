package dev.daymark.app

import androidx.compose.ui.window.ComposeUIViewController
import dev.daymark.auth.NativeGoogleAuth
import dev.daymark.data.DaymarkDatabase
import dev.daymark.data.openIosDatabase
import dev.daymark.location.IosLocationGateway
import dev.daymark.location.IosLocationPermissions
import dev.daymark.location.LocationGateway
import dev.daymark.location.LocationPermissionPort
import dev.daymark.reminders.ReminderPort
import dev.daymark.widgets.WidgetSink
import platform.UIKit.UIViewController
import org.koin.dsl.module
import kotlin.time.Clock

private var applicationGraph: AppGraph? = null

fun MainViewController(
    auth: NativeGoogleAuth,
    reminders: ReminderPort,
    widget: WidgetSink
): UIViewController {
    // One graph per application keeps manual checks and native side effects shared across host recreation.
    val graph = applicationGraph ?: AppGraph(module {
        single<DaymarkDatabase> { openIosDatabase() }
        single<LocationGateway> { IosLocationGateway(get<Clock>()) }
        single<LocationPermissionPort> { IosLocationPermissions() }
        single<NativeGoogleAuth> { auth }
        single<ReminderPort> { reminders }
        single<WidgetSink> { widget }
    }).also { applicationGraph = it }
    return ComposeUIViewController { App(graph) }
}
