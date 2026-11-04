package dev.daymark.android

import android.content.Context
import dev.daymark.app.*
import dev.daymark.attendance.*
import dev.daymark.auth.*
import dev.daymark.data.*
import dev.daymark.location.*
import dev.daymark.plan.WeeklySchedule
import dev.daymark.reminders.ReminderPort
import dev.daymark.widgets.WidgetSink
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.*
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

internal class AttendanceTestApp(context: Context, name: String, private val widget: WidgetSink = object : WidgetSink {
    override fun publish(json: String) = false
    override fun clear() = true
},private val nativeAuth: NativeGoogleAuth? = null) {
    val db = openAndroidDatabase(context,name)
    var now = Instant.parse("2026-10-27T09:02:00Z")
    var locationRequests = 0
    val clock = object : Clock { override fun now() = now }
    private val location = object : LocationGateway {
        override suspend fun requestFix(budget: Duration): LocationResult { locationRequests++; return LocationResult.Fix(0.0,0.0,10.0,clock.now(),clock.now(),Duration.ZERO) }
    }
    private val permissions = object : LocationPermissionPort {
        override fun current() = LocationCapabilities(true,true,true,true)
        override suspend fun requestForeground() = current()
        override suspend fun requestBackground() = current()
        override fun openSystemSettings() = Unit
    }
    init { runBlocking {
        SetupService(db,object : Clock { override fun now() = Instant.parse("2026-10-27T08:00:00Z") }).complete(
            SetupCommand("أحمد / Ahmed",TrackerCategory.WORK,"Office",RegisteredPlace(0.0,0.0,100,1),
                WeeklySchedule(DayOfWeek.entries.toSet(),LocalTime(9,0),emptyMap(),5,"UTC"),10))
    } }
    val graph: AppGraph by lazy {
        AppGraph(module {
            single<DaymarkDatabase> { db }
            single<Clock> { clock }
            single<LocationGateway> { location }
            single<LocationPermissionPort> { permissions }
            single<NativeGoogleAuth> {
                nativeAuth ?: object : NativeGoogleAuth {
                    override fun authorize(interactive: Boolean, completion: (NativeAuthReply) -> Unit) =
                        error("Optional Google must not be needed")

                    override fun forget(completion: (Boolean) -> Unit) {
                        completion(true)
                    }
                }
            }
            single<ReminderPort> {
                object : ReminderPort {
                    override fun requestPermission(completion: (Boolean) -> Unit) {
                        completion(false)
                    }

                    override fun replace(remindersJson: String, completion: (Boolean) -> Unit) {
                        completion(false)
                    }

                    override fun cancelTracker(trackerId: String, completion: (Boolean) -> Unit) {
                        completion(true)
                    }
                }
            }
            single<WidgetSink> { widget }
        })
    }
}
