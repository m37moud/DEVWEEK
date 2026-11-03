package dev.daymark.app

import dev.daymark.reporting.RawPatch
import dev.daymark.reporting.RemoteReport
import dev.daymark.reporting.RemoteSnapshot
import dev.daymark.reporting.RemoteWriteOutcome
import dev.daymark.reporting.ReportBinding
import dev.daymark.reporting.ReportTransport
import dev.daymark.reporting.RoomReportStore
import dev.daymark.reporting.SyncCoordinator
import dev.daymark.reporting.SyncPassResult
import dev.daymark.reporting.SyncTrigger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppLifecycleCoordinatorTest {
    @Test
    fun inactive_owner_dispatches_nothing_and_resume_drains_without_interactive_consent() =
        runTest {
            val calls = mutableListOf<SyncTrigger>()
            var cancelled = 0
            val owner = AppLifecycleCoordinator(backgroundScope, runSync = { trigger ->
                calls += trigger
                try {
                    awaitCancellation()
                } finally {
                    cancelled++
                }
            })
            owner.onLocalChange(); runCurrent(); assertTrue(calls.isEmpty())
            owner.onActive(true); runCurrent(); assertEquals(listOf(SyncTrigger.LAUNCH), calls)
            owner.onActive(false); runCurrent(); assertEquals(1, cancelled)
            owner.onLocalChange(); runCurrent(); assertEquals(1, calls.size)
            owner.onActive(true); runCurrent(); assertEquals(SyncTrigger.RESUME, calls.last())
            owner.onActive(false)
        }

    @Test
    fun pending_drain_uses_persisted_delay_and_stops_on_background() = runTest {
        var calls = 0
        val owner = AppLifecycleCoordinator(
            backgroundScope,
            runSync = { calls++; SyncPassResult.Pending },
            nextDelay = { 5_000L })
        owner.onActive(true); runCurrent(); assertEquals(1, calls)
        advanceTimeBy(5_000); runCurrent(); assertEquals(2, calls)
        owner.onActive(false); advanceTimeBy(20_000); runCurrent(); assertEquals(2, calls)
    }

    @Test
    fun backgrounding_preserves_ambiguous_journal_and_reopen_drains_latest_without_consent(): Unit =
        runBlocking {
            val folder = java.nio.file.Files.createTempDirectory("daymark-lifecycle-journal-")
            val path = folder.resolve("test.db").toString()
            var db = dev.daymark.data.openJvmDatabase(path)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            var owner: AppLifecycleCoordinator? = null
            try {
                dev.daymark.data.seedDatabase(db)
                val clock = dev.daymark.attendance.TestClock()
                var repo = dev.daymark.attendance.RoomAttendanceRepository(db, clock)
                repo.completeCheck(dev.daymark.data.testCommit())
                var store = RoomReportStore(db, clock)
                val tracker = db.dao().tracker()!!.id
                store.bind(
                    ReportBinding(
                        tracker,
                        store.beginConnection()!!,
                        "a",
                        "r",
                        annualTabIds = mapOf(2026 to 3)
                    )
                )
                var consent = 0
                val access =
                    dev.daymark.auth.GoogleAccess(object : dev.daymark.auth.NativeGoogleAuth {
                        override fun authorize(
                            interactive: Boolean,
                            completion: (dev.daymark.auth.NativeAuthReply) -> Unit
                        ) {
                            if (interactive) consent++
                            completion(
                                dev.daymark.auth.NativeAuthReply(
                                    dev.daymark.auth.NativeAuthStatus.GRANTED,
                                    "token",
                                    null
                                )
                            )
                        }

                        override fun forget(completion: (Boolean) -> Unit) {
                            completion(true)
                        }
                    })
                val entered = CompletableDeferred<Unit>()
                val server = object : ReportTransport {
                    var block = true
                    val cells =
                        mutableMapOf<String, List<List<kotlinx.serialization.json.JsonPrimitive>>>()

                    override suspend fun prepareTemplate(
                        report: RemoteReport,
                        trackerId: String,
                        zoneId: String,
                        years: Set<Int>,
                        token: String
                    ) = report

                    override suspend fun read(
                        report: RemoteReport,
                        ranges: List<String>,
                        token: String
                    ) = RemoteSnapshot(ranges.associateWith { cells[it].orEmpty() })

                    override suspend fun write(
                        report: RemoteReport,
                        patch: RawPatch,
                        token: String
                    ): RemoteWriteOutcome {
                        entered.complete(Unit)
                        if (block) awaitCancellation()
                        patch.ranges.forEach { cells[it.a1] = it.cells }
                        return RemoteWriteOutcome.Acknowledged
                    }
                }

                fun coordinator() = SyncCoordinator(
                    store,
                    server,
                    clock,
                    token = { (access.accessToken(false) as dev.daymark.auth.AccessResult.Granted).token },
                    zoneId = { "UTC" })

                var sync = coordinator()
                owner = AppLifecycleCoordinator(scope, sync::runPass)
                owner.onActive(true); withTimeout(10_000) { entered.await() }
                owner.stop()
                assertTrue(db.dao().batches(tracker).any { it.status == "AMBIGUOUS" })
                db.close(); db = dev.daymark.data.openJvmDatabase(path)
                repo = dev.daymark.attendance.RoomAttendanceRepository(db, clock)
                repo.correct(
                    dev.daymark.attendance.Correction(
                        dev.daymark.fixturePlan().key,
                        dev.daymark.attendance.AttendanceStatus.ABSENT,
                        null,
                        "Updated while offline"
                    )
                )
                store = RoomReportStore(db, clock); sync = coordinator(); server.block = false
                val done = CompletableDeferred<Unit>()
                owner = AppLifecycleCoordinator(
                    scope,
                    runSync = { trigger -> sync.runPass(trigger).also { done.complete(Unit) } })
                owner.onLocalChange()
                assertTrue(server.cells.isEmpty())
                owner.onActive(true); withTimeout(10_000) { done.await() }; owner.stop()
                val key = "'Attendance 2026'!A302:O302"
                assertEquals("ABSENT", server.cells.getValue(key).single()[5].content)
                assertEquals(
                    "Updated while offline",
                    server.cells.getValue(key).single()[9].content
                )
                assertEquals(0, consent)
            } finally {
                owner?.stop(); scope.cancel(); db.close(); folder.toFile().deleteRecursively()
            }
        }

    @Test
    fun retry_button_is_distinct_from_automatic_continuation() = runTest {
        val calls = mutableListOf<SyncTrigger>()
        val owner =
            AppLifecycleCoordinator(backgroundScope, runSync = { calls += it; SyncPassResult.Idle })
        owner.onActive(true); runCurrent(); owner.retryReport(); runCurrent()
        assertEquals(SyncTrigger.USER_RETRY, calls.last())
        owner.onActive(false)
    }

}
