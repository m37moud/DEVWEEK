package dev.daymark.android

import android.content.Context
import androidx.room3.withWriteTransaction
import androidx.work.WorkManager
import dev.daymark.attendance.*
import dev.daymark.app.AppGraph
import dev.daymark.auth.GoogleAccess
import dev.daymark.settings.SettingsRepository
import dev.daymark.reminders.ReminderPort
import dev.daymark.widgets.WidgetSink
import dev.daymark.data.*
import dev.daymark.location.*
import dev.daymark.plan.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

class AndroidAppGraph(context: Context) {
    private val app = context.applicationContext
    val clock: Clock = Clock.System
    val database: DaymarkDatabase by lazy { openAndroidDatabase(app) }
    val attendanceRepository by lazy { RoomAttendanceRepository(database, clock) }
    val location by lazy { AndroidLocationGateway(app) }
    val attendance by lazy { AttendanceService(attendanceRepository, location, clock) }
    val settings by lazy { SettingsRepository(database) }
    val timeOff by lazy { TimeOffService(database, clock) }
    val plans by lazy { PlanService(database, clock) }
    private var shared: AppGraph? = null
    fun sharedGraph(permissions: LocationPermissionPort): AppGraph = shared ?: AppGraph(database, attendanceRepository, attendance,
        timeOff, plans, RoomPlanReader(database, clock), settings, location, permissions,
        GoogleAccess((app as DaymarkApplication).googleAuth), AndroidReminderPort, DevelopmentWidgetSink, clock).also { shared = it }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val scheduler by lazy { AndroidAttendanceScheduler(WorkManager.getInstance(app), clock, scope) }
    private val failed = MutableStateFlow(false)
    val recoveryFailed = failed.asStateFlow()
    fun requestRecovery() { scope.launch { recoverSafely() } }
    suspend fun recoverSafely(): Boolean = try { recover(); failed.value = false; true }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed.value = true; false }
    private val recoveryMutex = Mutex()
    private fun permitted(): Boolean = androidLocationCapabilities(app).let { it.foregroundGranted && it.preciseGranted && it.backgroundGranted }
    private suspend fun disableAutomation(trackerId: String) = database.withWriteTransaction {
        database.dao().tracker()?.takeIf { it.automaticEnabled && it.id == trackerId }?.let { database.dao().putTracker(it.copy(automaticEnabled = false, changeRevision = it.changeRevision + 1)) }
    }
    fun attendanceJob() = AndroidAttendanceJob(attendance, ::permitted, ::disableAutomation, ::recover)
    suspend fun recover() = recoveryMutex.withLock {
        attendanceRepository.reconcile(clock.now())
        if (!permitted()) database.dao().tracker()?.let { disableAutomation(it.id) }
        scheduler.replaceNextAwait(nextAttendance(clock.now(), RoomPlanReader(database, clock)))
    }
}

/** iOS reminders have no Android scheduler; automation uses the separate WorkManager adapter. */
private object AndroidReminderPort : ReminderPort {
    override fun requestPermission(completion: (Boolean) -> Unit) { completion(false) }
    override fun replace(remindersJson: String, completion: (Boolean) -> Unit) { completion(false) }
    override fun cancelTracker(trackerId: String, completion: (Boolean) -> Unit) { completion(true) }
}
/** Development-only boundary until Task 12 supplies the native widget cache. Never reports a publication success. */
private object DevelopmentWidgetSink : WidgetSink {
    override fun publish(json: String) = false
    override fun clear() = false
}
