package dev.daymark.android

import androidx.work.*
import dev.daymark.attendance.DayKey
import dev.daymark.plan.ScheduledCheck
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

internal fun attendanceWorkName(key: DayKey) = "daymark-attendance:${key.trackerId}:${key.date}"
private fun trackerTag(id: String) = "daymark-attendance-tracker:$id"
private const val ATTENDANCE_TAG = "daymark-attendance"

class AndroidAttendanceScheduler(private val manager: WorkManager, private val clock: Clock,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
    private val mutex = Mutex()
    fun replaceNext(check: ScheduledCheck?) { scope.launch { replaceNextAwait(check) } }
    fun cancelTracker(trackerId: String) { scope.launch { cancelTrackerAwait(trackerId) } }
    suspend fun cancelTrackerAwait(trackerId: String) = withContext(Dispatchers.IO) {
        mutex.withLock { manager.cancelAllWorkByTag(trackerTag(trackerId)).result.get(); Unit }
    }
    suspend fun replaceNextAwait(check: ScheduledCheck?) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val existing = manager.getWorkInfosByTag(ATTENDANCE_TAG).get().filterNot { it.state.isFinished }
            val name = check?.let { attendanceWorkName(it.key) }
            val trigger = check?.let { "arrival:${it.arrival}" }
            // Recovery never cancels an in-flight check. Replacing pending work cannot release the Room claim.
            for (work in existing.filter { it.state != WorkInfo.State.RUNNING }) {
                if (name == null || name !in work.tags || trigger !in work.tags)
                    manager.cancelWorkById(work.id).result.get()
            }
            if (check == null) return@withLock
            if (existing.any { it.state != WorkInfo.State.RUNNING && name in it.tags && trigger in it.tags }) return@withLock
            val request = OneTimeWorkRequestBuilder<AttendanceWorker>()
                .setInitialDelay((check.arrival - clock.now()).inWholeMilliseconds.coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("trackerId" to check.key.trackerId, "date" to check.key.date.toString()))
                .addTag(ATTENDANCE_TAG).addTag(requireNotNull(name)).addTag(requireNotNull(trigger)).addTag(trackerTag(check.key.trackerId))
                .build()
            // If an early worker is still returning, append its corrected future window instead of cancelling it.
            val running = existing.any { it.state == WorkInfo.State.RUNNING && name in it.tags }
            manager.enqueueUniqueWork(name, if (running) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE, request).result.get()
        }
    }
}
