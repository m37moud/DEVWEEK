package dev.daymark.android

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.WorkManager
import dev.daymark.app.AppGraph
import dev.daymark.attendance.*
import dev.daymark.attendance.AttendanceRepository
import dev.daymark.attendance.AttendanceService
import dev.daymark.auth.NativeGoogleAuth
import dev.daymark.data.*
import dev.daymark.data.local.DaymarkDatabase
import dev.daymark.data.local.dao.ProfileDao
import dev.daymark.data.local.attendance.RoomAttendanceRepository
import dev.daymark.models.attendance.CheckMode
import dev.daymark.notifications.AttendanceResultNotifier
import dev.daymark.repository.SettingsDataSource
import dev.daymark.data.local.openAndroidDatabase
import dev.daymark.data.local.reporting.ReportingRuntime
import dev.daymark.location.*
import dev.daymark.location.AndroidLocationGateway
import dev.daymark.location.LocationGateway
import dev.daymark.location.LocationPermissionPort
import dev.daymark.location.androidLocationCapabilities
import dev.daymark.plan.*
import dev.daymark.plan.PlanReader
import dev.daymark.plan.nextAttendance
import dev.daymark.reminders.ReminderPort
import dev.daymark.reporting.*
import dev.daymark.reporting.initializeReportConnectivity
import dev.daymark.widgets.WidgetPublisher
import dev.daymark.widgets.WidgetSink
import dev.daymark.widgets.initializeWidgetResources
import kotlin.time.Clock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.dsl.module
import org.koin.dsl.onClose

class AndroidAppGraph(context: Context) {
    private val app = context.applicationContext
    val resultNotifications = AndroidAttendanceResults(app)

    init {
        initializeWidgetResources(app); initializeReportConnectivity(app)
    }

    private val dependencies = AppGraph(
        module {
            single<DaymarkDatabase> { openAndroidDatabase(app) }
            single<LocationGateway> { AndroidLocationGateway(app) }
            single<PlaceGeocoder> { AndroidPlaceGeocoder(app) }
            single { AndroidGoogleAuth(app) }
            single<NativeGoogleAuth> { get<AndroidGoogleAuth>() }
            single { AndroidAttendanceScheduler(WorkManager.getInstance(app), get(), scope) }
            factory {
                AndroidAttendanceJob(
                    attendance = get(), capabilities = { androidLocationCapabilities(app) },
                    disableAutomation = ::disableAutomation, recoverSchedule = ::recover
                )
            }
            single<ReminderPort> { AndroidReminderPort }
            single<dev.daymark.notifications.AttendanceResultPort> { resultNotifications }
            single<WidgetSink> { AndroidWidgetPublisher(app) }
            single {
                ReportingRuntime(
                    get(), get(), get(), get(),
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
    internal val isDeleting get() = dependencies.dataLifecycle.deleting.value
    val clock: Clock by dependencies.koin.inject()
    internal val profileDao: ProfileDao by dependencies.koin.inject()
    private val automaticAttendance: RoomAttendanceRepository by dependencies.koin.inject()
    private val settings: SettingsDataSource by dependencies.koin.inject()
    private val attendanceResults: AttendanceResultNotifier by dependencies.koin.inject()
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
    fun requestRecovery() {
        scope.launch { recoverSafely() }
    }

    suspend fun recoverSafely(publishWidget: Boolean = true): Boolean = try {
        recoverInternal(publishWidget); failed.value = false; true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        failed.value = true; false
    }

    private val recoveryMutex = Mutex()
    private suspend fun stopExternal() {
        WidgetCheckService.cancelAndJoin()
        app.stopService(android.content.Intent(app, WidgetCheckService::class.java))
        profileDao.tracker()?.let { scheduler.cancelTrackerAwait(it.id) }
        withContext(Dispatchers.IO) {
            WorkManager.getInstance(app).cancelUniqueWork(REPORT_WORK).result.get()
        }
        recoveryMutex.withLock { /* Wait for any older native recovery before the final widget clear. */ }
    }

    private suspend fun disableAutomation(trackerId: String) =
        settings.setAutomation(expectedTrackerId = trackerId, enabled = false)

    fun attendanceJob(): AndroidAttendanceJob = dependencies.koin.get()
    suspend fun recover() = recoverInternal(publishWidget = true)
    private suspend fun recoverInternal(publishWidget: Boolean) = recoveryMutex.withLock {
        if (shared?.dataLifecycle?.deleting?.value == true) return@withLock
        val now = clock.now()
        attendanceRepository.reconcile(now = now)
        val capabilities = androidLocationCapabilities(app)
        val canRun = capabilities.permitsBackgroundAttendance
        val recovery = automaticAttendance.recoverAutomatic(
            now = now, locationEnabled = capabilities.servicesEnabled
        )
        if (!canRun) profileDao.tracker()?.let { disableAutomation(trackerId = it.id) }
        recovery.delivery?.let { delivery ->
            attendanceResults.completed(key = delivery.key, mode = CheckMode.AUTOMATIC, outcome = delivery.outcome)
        }
        val next = recovery.next?.takeIf { canRun } ?: nextAttendance(now = now, plans = planReader)
        scheduler.replaceNextAwait(check = next)
        if (publishWidget) widgets.refresh()
    }
}

/** iOS reminders have no Android scheduler; automation uses the separate WorkManager adapter. */
private object AndroidReminderPort : ReminderPort {
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
