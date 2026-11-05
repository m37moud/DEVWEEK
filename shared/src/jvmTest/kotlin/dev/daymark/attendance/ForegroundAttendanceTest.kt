package dev.daymark.attendance

import dev.daymark.at
import dev.daymark.data.local.attendance.RoomAttendanceRepository
import dev.daymark.data.local.openJvmDatabase
import dev.daymark.data.seedDatabase
import dev.daymark.fixtureFix
import dev.daymark.fixturePlan
import dev.daymark.location.LocationGateway
import dev.daymark.models.attendance.AttendanceSource
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.models.attendance.Correction
import dev.daymark.models.attendance.DayOutcome
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalTime

class ForegroundAttendanceTest {
    @Test
    fun repository_deadline_observer_persists_absence_and_stops_after_cancellation() = runBlocking {
        val folder = Files.createTempDirectory("daymark-deadline-")
        val db = openJvmDatabase(folder.resolve("attendance.db").toString())
        val clock = TestClock(at("09:04:59.900"))
        var observation: Job? = null
        try {
            seedDatabase(db)
            val repo = RoomAttendanceRepository(db, clock)
            repo.reconcile(clock.now())
            val key = fixturePlan().key
            val before = repo.loadDay(key)!!.revision
            val events = Channel<DayOutcome?>(Channel.UNLIMITED)
            observation =
                launch(Dispatchers.Default) { repo.observeDay(key).collect { events.send(it) } }
            assertEquals(
                AttendanceStatus.AWAITING_CHECK,
                withTimeout(5_000) { events.receive() }?.status
            )
            clock.value = at("09:05:00.001")
            withTimeout(5_000) { while (events.receive()?.status != AttendanceStatus.ABSENT) Unit }
            assertEquals(before + 1, repo.loadDay(key)!!.revision)
            assertNull(repo.loadDay(key)!!.evidence)
            observation.cancelAndJoin()
            clock.value = Instant.parse("2026-10-28T09:06:00Z")
            delay(150)
            assertEquals(1, db.dao().days(key.trackerId).size)
        } finally {
            observation?.cancelAndJoin(); db.close(); folder.toFile().deleteRecursively()
        }
    }

    @Test
    fun late_check_and_explicit_present_correction_keep_literal_note_after_reopen() = runBlocking {
        val folder = Files.createTempDirectory("daymark-history-")
        val path = folder.resolve("attendance.db").toString()
        val db = openJvmDatabase(path)
        val clock = TestClock(at("09:18:00"))
        try {
            seedDatabase(db)
            val repo = RoomAttendanceRepository(db, clock)
            val location = object : LocationGateway {
                override suspend fun requestFix(budget: Duration) =
                    fixtureFix(capturedAt = clock.now())
            }
            assertEquals(
                AttendanceStatus.LATE,
                AttendanceService(repo, location, clock).manualCheck().currentDay?.status
            )
            repo.correct(
                Correction(
                    fixturePlan().key,
                    AttendanceStatus.PRESENT,
                    LocalTime(9, 18),
                    "اجتماع / Meeting =1+2"
                )
            )
        } finally {
            db.close()
        }
        val reopened = openJvmDatabase(path)
        try {
            val repo = RoomAttendanceRepository(reopened, clock)
            val saved = repo.loadCurrentDay(clock.now())!!
            assertEquals(AttendanceStatus.PRESENT, saved.evidence?.status)
            assertEquals(AttendanceSource.MANUAL_CORRECTION, saved.evidence?.source)
            assertEquals("اجتماع / Meeting =1+2", saved.evidence?.note)
            val month = repo.observeMonth(2026, 10).first()
            assertEquals(1, month.present); assertEquals(0, month.late); assertEquals(
                0,
                month.absent
            )
        } finally {
            reopened.close(); folder.toFile().deleteRecursively()
        }
    }
}
