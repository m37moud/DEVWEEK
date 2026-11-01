package dev.daymark.android

import android.content.Context
import dev.daymark.app.*
import dev.daymark.attendance.*
import dev.daymark.auth.*
import dev.daymark.data.*
import dev.daymark.location.*
import dev.daymark.plan.*
import dev.daymark.reminders.ReminderPort
import dev.daymark.settings.SettingsRepository
import dev.daymark.widgets.WidgetSink
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.*
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

internal class AttendanceTestApp(context: Context, name: String) {
    val db = openAndroidDatabase(context,name)
    val clock = object : Clock { override fun now() = Instant.parse("2026-10-27T09:02:00Z") }
    private val location = object : LocationGateway {
        override suspend fun requestFix(budget: Duration) = LocationResult.Fix(0.0,0.0,10.0,clock.now(),clock.now(),Duration.ZERO)
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
        val repo = RoomAttendanceRepository(db,clock)
        AppGraph(db,repo,AttendanceService(repo,location,clock),TimeOffService(db,clock),PlanService(db,clock),RoomPlanReader(db,clock),
            SettingsRepository(db),location,permissions,GoogleAccess(object : NativeGoogleAuth {
                override fun authorize(interactive: Boolean,completion: (NativeAuthReply) -> Unit) = error("Optional Google must not be needed")
                override fun forget(completion: (Boolean) -> Unit) { completion(true) }
            }),object : ReminderPort {
                override fun requestPermission(completion: (Boolean) -> Unit) { completion(false) }
                override fun replace(remindersJson: String,completion: (Boolean) -> Unit) { completion(false) }
                override fun cancelTracker(trackerId: String,completion: (Boolean) -> Unit) { completion(true) }
            },object : WidgetSink {
                override fun publish(json: String) = false
                override fun clear() = true
            },clock)
    }
}
