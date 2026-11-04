package dev.daymark.android

import android.content.Context
import androidx.room3.withWriteTransaction
import androidx.work.WorkManager
import dev.daymark.attendance.*
import dev.daymark.app.AppGraph
import dev.daymark.auth.NativeGoogleAuth
import dev.daymark.reminders.ReminderPort
import dev.daymark.widgets.WidgetSink
import dev.daymark.widgets.WidgetPublisher
import androidx.glance.appwidget.updateAll
import dev.daymark.widgets.initializeWidgetResources
import dev.daymark.data.*
import dev.daymark.location.*
import dev.daymark.plan.*
import dev.daymark.reporting.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.dsl.module
import org.koin.dsl.onClose
import kotlin.time.Clock

class AndroidAppGraph(context: Context) {
    private val app = context.applicationContext

    init {
        initializeWidgetResources(app); initializeReportConnectivity(app)
    }

    private val dependencies = AppGraph(
        module {
            single<DaymarkDatabase> { openAndroidDatabase(app) }
            single<LocationGateway> { AndroidLocationGateway(app) }
            single { AndroidGoogleAuth(app) }
            single<NativeGoogleAuth> { get<AndroidGoogleAuth>() }
            single { AndroidAttendanceScheduler(WorkManager.getInstance(app), get(), scope) }
            factory { AndroidAttendanceJob(get(), ::permitted, ::disableAutomation, ::recover) }
            single<ReminderPort> { AndroidReminderPort }
            single<WidgetSink> { AndroidWidgetPublisher(app) }
            single {
                ReportingRuntime(
                    get(),
                    get(),
                    get(),
                    get(),
                    enqueueNetwork = { enqueueReportSync(app) },
                    httpClient = get(), store = get(), reportTransport = get()
                )
            } onClose { it?.close() }
        },
        refreshSchedule = { recoverSafely(publishWidget = false) },
        stopExternalWork = { stopExternal() }
    )
    val clock: Clock by dependencies.koin.inject()
    val database: DaymarkDatabase by dependencies.koin.inject()
    val attendanceRepository: AttendanceRepository by dependencies.koin.inject()
    val attendance: AttendanceService by dependencies.koin.inject()
    val widgets: WidgetPublisher by dependencies.koin.inject()
    val googleAuth: AndroidGoogleAuth by dependencies.koin.inject()
    val location: LocationGateway by dependencies.koin.inject()
    private val planReader: PlanReader by dependencies.koin.inject()
    fun requestWidgetUpdate() {
        scope.launch {
            try {
                DaymarkWidget().updateAll(app)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { /* A future cache/app update can retry rendering. */
            }
        }
    }

    val reporting: ReportingRuntime by dependencies.koin.inject()
    private var shared: AppGraph? = null
    fun sharedGraph(permissions: LocationPermissionPort): AppGraph = shared ?: dependencies.also {
        it.koin.declare(permissions)
        shared = it
    }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val scheduler: AndroidAttendanceScheduler by dependencies.koin.inject()
    private val failed = MutableStateFlow(false)
    val recoveryFailed = failed.asStateFlow()
    fun requestRecovery() { scope.launch { recoverSafely() } }
    suspend fun recoverSafely(publishWidget: Boolean = true): Boolean = try { recoverInternal(publishWidget); failed.value = false; true }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed.value = true; false }
    private val recoveryMutex = Mutex()
    private suspend fun stopExternal() {
        database.dao().tracker()?.let { scheduler.cancelTrackerAwait(it.id) }
        withContext(Dispatchers.IO) { WorkManager.getInstance(app).cancelUniqueWork(REPORT_WORK).result.get() }
        recoveryMutex.withLock { /* Wait for any older native recovery before the final widget clear. */ }
    }
    private fun permitted(): Boolean = androidLocationCapabilities(app).let { it.foregroundGranted && it.preciseGranted && it.backgroundGranted }
    private suspend fun disableAutomation(trackerId: String) = database.withWriteTransaction {
        database.dao().tracker()?.takeIf { it.automaticEnabled && it.id == trackerId }?.let { database.dao().putTracker(it.copy(automaticEnabled = false, changeRevision = it.changeRevision + 1)) }
    }

    fun attendanceJob(): AndroidAttendanceJob = dependencies.koin.get()
    suspend fun recover() = recoverInternal(publishWidget = true)
    private suspend fun recoverInternal(publishWidget: Boolean) = recoveryMutex.withLock {
        if (shared?.dataLifecycle?.deleting?.value == true) return@withLock
        attendanceRepository.reconcile(clock.now())
        if (!permitted()) database.dao().tracker()?.let { disableAutomation(it.id) }
        scheduler.replaceNextAwait(nextAttendance(clock.now(), planReader))
        if (publishWidget) widgets.refresh()
    }
}

/** iOS reminders have no Android scheduler; automation uses the separate WorkManager adapter. */
private object AndroidReminderPort : ReminderPort {
    override fun requestPermission(completion: (Boolean) -> Unit) { completion(false) }
    override fun replace(remindersJson: String, completion: (Boolean) -> Unit) { completion(false) }
    override fun cancelTracker(trackerId: String, completion: (Boolean) -> Unit) { completion(true) }
}
