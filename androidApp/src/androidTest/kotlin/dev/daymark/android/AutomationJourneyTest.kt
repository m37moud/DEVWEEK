package dev.daymark.android

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import dev.daymark.attendance.*
import dev.daymark.attendance.AttendanceService
import dev.daymark.attendance.evaluateDay
import dev.daymark.data.*
import dev.daymark.data.local.DaymarkDatabase
import dev.daymark.data.local.PlanVersionEntity
import dev.daymark.data.local.TrackerEntity
import dev.daymark.data.local.attendance.RoomAttendanceRepository
import dev.daymark.data.local.openAndroidDatabase
import dev.daymark.data.local.plan.RoomPlanReader
import dev.daymark.location.*
import dev.daymark.location.LocationFailure
import dev.daymark.location.LocationGateway
import dev.daymark.location.LocationResult
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.models.attendance.DayKey
import dev.daymark.models.attendance.RegisteredPlace
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.plan.*
import dev.daymark.plan.ScheduledCheck
import dev.daymark.plan.WeeklySchedule
import dev.daymark.plan.nextAttendance
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutomationJourneyTest {
    private class MutableClock(var value: Instant) : Clock { override fun now() = value }
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val arrival = Instant.parse("2026-10-27T09:00:00Z")
    private suspend fun withDatabase(block: suspend (DaymarkDatabase, DayKey) -> Unit) {
        val name = "daymark-worker-test-${UUID.randomUUID()}.db"
        val db = openAndroidDatabase(context, name)
        val key = DayKey(UUID.randomUUID().toString(), LocalDate(2026, 10, 27))
        try {
            db.dao().insertTracker(TrackerEntity(key.trackerId, "Test tracker", "2026-10-27T08:00:00Z", arrival.toString(), 12, true))
            db.dao().putVersion(PlanVersionEntity.fromSnapshot(key.trackerId, 1, key.date, arrival,
                TrackerCategory.WORK, WeeklySchedule(DayOfWeek.entries.toSet(), LocalTime(9, 0), emptyMap(), 5, "UTC"),
                RegisteredPlace(0.0, 0.0, 100, 1), "Test place"))
            block(db, key)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    private fun worker(key: DayKey, job: AndroidAttendanceJob): AttendanceWorker =
        TestListenableWorkerBuilder<AttendanceWorker>(context)
            .setInputData(workDataOf("trackerId" to key.trackerId, "date" to key.date.toString()))
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                    AttendanceWorker(appContext, workerParameters, job)
            }).build()
    @Test fun late_worker_and_restarted_consumed_worker_request_zero_additional_fixes() = runBlocking {
        withDatabase { db, key ->
            val clock = MutableClock(arrival)
            var calls = 0
            var next: ScheduledCheck? = null
            val gateway = object : LocationGateway {
                override suspend fun requestFix(budget: Duration): LocationResult { calls++; return LocationResult.Failure(LocationFailure.TIMEOUT) }
            }
            fun job() = AndroidAttendanceJob(AttendanceService(RoomAttendanceRepository(db, clock), gateway, clock), { true }, {},
                { next = nextAttendance(clock.now(), RoomPlanReader(db, clock)) })
            assertEquals(ListenableWorker.Result.success(), worker(key, job()).doWork())
            assertEquals(1, calls)
            assertEquals(key.date.plus(1, DateTimeUnit.DAY), next?.key?.date)
            assertEquals(ListenableWorker.Result.success(), worker(key, job()).doWork())
            assertEquals(1, calls)
            clock.value = Instant.parse("2026-10-28T09:06:00Z")
            val nextKey = key.copy(date = key.date.plus(1, DateTimeUnit.DAY))
            assertEquals(ListenableWorker.Result.success(), worker(nextKey, job()).doWork())
            assertEquals(1, calls)
            assertEquals(AttendanceStatus.ABSENT, RoomAttendanceRepository(db, clock).loadDay(nextKey)?.let {
                evaluateDay(it.plan, it.evidence, it.off, clock.now()).status })
        }
    }
    @Test fun denied_worker_keeps_records_and_disables_automation() = runBlocking {
        withDatabase { db, key ->
            val clock = MutableClock(arrival)
            val repo = RoomAttendanceRepository(db, clock)
            repo.reconcile(clock.now())
            var calls = 0
            val gateway = object : LocationGateway { override suspend fun requestFix(budget: Duration): LocationResult { calls++; error("Denied GPS") } }
            val job = AndroidAttendanceJob(AttendanceService(repo, gateway, clock), { false }, {
                db.dao().putTracker(requireNotNull(db.dao().tracker()).copy(automaticEnabled = false))
            }, {})
            assertEquals(ListenableWorker.Result.success(), worker(key, job).doWork())
            assertFalse(requireNotNull(db.dao().tracker()).automaticEnabled)
            assertNotNull(repo.loadDay(key))
            assertEquals(0, calls)
        }
    }
    @Test fun repeated_recovery_has_one_pending_date_and_no_network_constraint() = runBlocking {
        val manager = WorkManager.getInstance(context)
        val clock = MutableClock(arrival)
        val scheduler = AndroidAttendanceScheduler(manager, clock)
        val key = DayKey(UUID.randomUUID().toString(), LocalDate(2026, 10, 28))
        val check = ScheduledCheck(key, Instant.parse("2026-10-28T09:00:00Z"), Instant.parse("2026-10-28T09:05:00Z"))
        try {
            scheduler.replaceNextAwait(check)
            scheduler.replaceNextAwait(check)
            val infos = manager.getWorkInfosForUniqueWork(attendanceWorkName(key)).get().filterNot { it.state.isFinished }
            assertEquals(1, infos.size)
            assertEquals(NetworkType.NOT_REQUIRED, infos.single().constraints.requiredNetworkType)
            assertEquals(WorkInfo.State.ENQUEUED, infos.single().state)
        } finally { scheduler.cancelTrackerAwait(key.trackerId) }
    }
    @Test fun manifest_has_no_exact_alarm_or_foreground_location_escape_hatch() {
        val info = context.packageManager.getPackageInfo(context.packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS or android.content.pm.PackageManager.GET_SERVICES)
        val permissions = info.requestedPermissions?.toSet().orEmpty()
        assertFalse("android.permission.SCHEDULE_EXACT_ALARM" in permissions)
        assertFalse("android.permission.USE_EXACT_ALARM" in permissions)
        assertFalse("android.permission.FOREGROUND_SERVICE" in permissions)
        assertTrue(info.services.orEmpty().none { it.name == "androidx.work.impl.foreground.SystemForegroundService" })
    }

}
