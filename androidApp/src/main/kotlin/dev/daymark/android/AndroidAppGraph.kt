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
import dev.daymark.widgets.WidgetPublisher
import androidx.glance.appwidget.updateAll
import dev.daymark.widgets.initializeWidgetResources
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
    init { initializeWidgetResources(app) }
    val clock: Clock = Clock.System
    val database: DaymarkDatabase by lazy { openAndroidDatabase(app) }
    val attendanceRepository by lazy { RoomAttendanceRepository(database, clock) }
    val location by lazy { AndroidLocationGateway(app) }
    val attendance by lazy { AttendanceService(attendanceRepository, location, clock) }
    val settings by lazy { SettingsRepository(database) }
    val timeOff by lazy { TimeOffService(database, clock) }
    val plans by lazy { PlanService(database, clock) }
    val widgetSink by lazy { AndroidWidgetPublisher(app) }
    val widgets by lazy { WidgetPublisher(database,attendanceRepository,widgetSink,clock) }
    fun requestWidgetUpdate() { scope.launch { try { DaymarkWidget().updateAll(app) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { /* A future cache/app update can retry rendering. */ } } }
    private var shared: AppGraph? = null
    fun sharedGraph(permissions: LocationPermissionPort): AppGraph = shared ?: AppGraph(database, attendanceRepository, attendance,
        timeOff, plans, RoomPlanReader(database, clock), settings, location, permissions,
        GoogleAccess((app as DaymarkApplication).googleAuth), AndroidReminderPort, widgetSink, clock, refreshSchedule = { recoverSafely(publishWidget = false) },widgetPublisher = widgets).also { shared = it }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val scheduler by lazy { AndroidAttendanceScheduler(WorkManager.getInstance(app), clock, scope) }
    private val failed = MutableStateFlow(false)
    val recoveryFailed = failed.asStateFlow()
    fun requestRecovery() { scope.launch { recoverSafely() } }
    suspend fun recoverSafely(publishWidget: Boolean = true): Boolean = try { recoverInternal(publishWidget); failed.value = false; true }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed.value = true; false }
    private val recoveryMutex = Mutex()
    private fun permitted(): Boolean = androidLocationCapabilities(app).let { it.foregroundGranted && it.preciseGranted && it.backgroundGranted }
    private suspend fun disableAutomation(trackerId: String) = database.withWriteTransaction {
        database.dao().tracker()?.takeIf { it.automaticEnabled && it.id == trackerId }?.let { database.dao().putTracker(it.copy(automaticEnabled = false, changeRevision = it.changeRevision + 1)) }
    }
    fun attendanceJob() = AndroidAttendanceJob(attendance, ::permitted, ::disableAutomation, ::recover)
    suspend fun recover() = recoverInternal(publishWidget = true)
    private suspend fun recoverInternal(publishWidget: Boolean) = recoveryMutex.withLock {
        attendanceRepository.reconcile(clock.now())
        if (!permitted()) database.dao().tracker()?.let { disableAutomation(it.id) }
        scheduler.replaceNextAwait(nextAttendance(clock.now(), RoomPlanReader(database, clock)))
        if (publishWidget) widgets.refresh()
    }
}

/** iOS reminders have no Android scheduler; automation uses the separate WorkManager adapter. */
private object AndroidReminderPort : ReminderPort {
    override fun requestPermission(completion: (Boolean) -> Unit) { completion(false) }
    override fun replace(remindersJson: String, completion: (Boolean) -> Unit) { completion(false) }
    override fun cancelTracker(trackerId: String, completion: (Boolean) -> Unit) { completion(true) }
}
