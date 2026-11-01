package dev.daymark.android

import android.content.Context
import androidx.room3.withWriteTransaction
import androidx.work.WorkManager
import dev.daymark.attendance.*
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
    val attendance by lazy { AttendanceService(attendanceRepository, AndroidLocationGateway(app), clock) }
    val timeOff by lazy { TimeOffService(database, clock) }
    val plans by lazy { PlanService(database, clock) }
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
