package dev.daymark.attendance

import androidx.room3.executeSQL
import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import androidx.room3.withWriteTransaction
import dev.daymark.at
import dev.daymark.data.DaymarkDatabase
import dev.daymark.data.PlanVersionEntity
import dev.daymark.data.openJvmDatabase
import dev.daymark.data.seedDatabase
import dev.daymark.data.testCommit
import dev.daymark.fixtureDate
import dev.daymark.fixtureFix
import dev.daymark.fixturePlace
import dev.daymark.fixturePlan
import dev.daymark.fixtureSchedule
import dev.daymark.location.LocationGateway
import dev.daymark.location.LocationResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Instant

class AttendanceTransactionTest {
    private suspend fun withDatabase(block: suspend (DaymarkDatabase, Path) -> Unit) {
        val folder = Files.createTempDirectory("daymark-room-test-")
        val path = folder.resolve("attendance.db")
        val db = openJvmDatabase(path.toString())
        try {
            seedDatabase(db); block(db, path)
        } finally {
            db.close(); folder.toFile().deleteRecursively()
        }
    }

    @Test
    fun claim_is_unique_and_remains_consumed_after_reopen() = runTest {
        withDatabase { db, path ->
            val clock = TestClock();
            val repo = RoomAttendanceRepository(db, clock)
            val key = fixturePlan().key
            val claims = coroutineScope {
                (1..8).map {
                    async(Dispatchers.Default) {
                        repo.claimAutomatic(key, "attempt-$it", clock.now())
                    }
                }.awaitAll()
            }
            assertEquals(1, claims.count { it is ClaimResult.Claimed })
            assertEquals(1, db.dao().countDay(key.trackerId, key.date.toString()))
            db.close()
            val reopened = openJvmDatabase(path.toString())
            try {
                val next = RoomAttendanceRepository(reopened, clock)
                assertIs<ClaimResult.AlreadyConsumed>(
                    next.claimAutomatic(
                        key,
                        "another-attempt",
                        clock.now()
                    )
                )
                next.reconcile(clock.now())
                assertEquals(AutomaticState.INTERRUPTED, next.loadDay(key)?.automaticState)
                assertEquals(1, reopened.dao().countDay(key.trackerId, key.date.toString()))
            } finally {
                reopened.close()
            }
        }
    }

    @Test
    fun concurrent_reconciliation_creates_one_date_row() = runTest {
        withDatabase { db, _ ->
            val clock = TestClock();
            val repo = RoomAttendanceRepository(db, clock)
            coroutineScope {
                (1..8).map { async(Dispatchers.Default) { repo.reconcile(clock.now()) } }.awaitAll()
            }
            assertEquals(1, db.dao().countDay(fixturePlan().key.trackerId, fixtureDate.toString()))
        }
    }

    @Test
    fun correction_wins_while_a_real_database_check_is_acquiring_location() = runTest {
        withDatabase { db, _ ->
            val clock = TestClock();
            val repo = RoomAttendanceRepository(db, clock)
            val started = CompletableDeferred<Unit>();
            val response = CompletableDeferred<LocationResult>()
            val gateway = object : LocationGateway {
                override suspend fun requestFix(budget: Duration): LocationResult {
                    started.complete(Unit); return response.await()
                }
            }
            val pending = async { AttendanceService(repo, gateway, clock).manualCheck() }
            started.await()
            repo.correct(
                Correction(
                    fixturePlan().key,
                    AttendanceStatus.ABSENT,
                    null,
                    "User correction"
                )
            )
            response.complete(fixtureFix())
            val result = assertIs<CheckOutcome.StateChanged>(pending.await())
            assertEquals(AttendanceSource.MANUAL_CORRECTION, result.currentDay?.source)
            assertEquals(AttendanceStatus.ABSENT, repo.loadDay(fixturePlan().key)?.evidence?.status)
        }
    }

    @Test
    fun changed_user_edit_revision_rejects_an_aba_request() = runTest {
        withDatabase { db, _ ->
            val clock = TestClock();
            val repo = RoomAttendanceRepository(db, clock)
            repo.reconcile(clock.now())
            val key = fixturePlan().key
            db.withWriteTransaction {
                val row = requireNotNull(db.dao().day(key.trackerId, key.date.toString()))
                db.dao().putDay(row.copy(userEditRevision = row.userEditRevision + 2))
            }
            assertIs<CheckOutcome.StateChanged>(repo.completeCheck(testCommit()))
            assertNull(repo.loadDay(key)?.evidence)
        }
    }

    @Test
    fun failed_check_cannot_downgrade_and_earlier_success_wins() = runTest {
        withDatabase { db, _ ->
            val repo = RoomAttendanceRepository(db, TestClock())
            assertIs<CheckOutcome.Recorded>(repo.completeCheck(testCommit()))
            val failed =
                repo.completeCheck(testCommit(verdict = FixVerdict.Rejected(CheckRejection.OUTSIDE_AREA)))
            assertEquals(AttendanceStatus.PRESENT, failed.currentDay?.status)
            assertIs<CheckOutcome.Recorded>(repo.completeCheck(testCommit(at("09:01:00"))))
            assertIs<CheckOutcome.AlreadyRecorded>(repo.completeCheck(testCommit(at("09:01:30"))))
            assertEquals(at("09:01:00"), repo.loadDay(fixturePlan().key)?.evidence?.confirmedAt)
        }
    }

    @Test
    fun failed_transaction_cannot_leave_partial_outbox_or_success_ui() = runTest {
        withDatabase { db, _ ->
            val clock = TestClock();
            val repo = RoomAttendanceRepository(db, clock)
            repo.reconcile(clock.now())
            val before = requireNotNull(repo.loadDay(fixturePlan().key)).revision
            val dirty = db.dao().dirtyProjections(fixturePlan().key.trackerId)
            db.useWriterConnection { connection ->
                connection.executeSQL(
                    "CREATE TRIGGER abort_overview BEFORE UPDATE ON dirty_projections WHEN NEW.projectionKey = 'OVERVIEW' BEGIN SELECT RAISE(ABORT, 'test_abort'); END"
                )
            }
            val gateway = object : LocationGateway {
                override suspend fun requestFix(budget: Duration) = fixtureFix()
            }
            val result = assertIs<CheckOutcome.Rejected>(
                AttendanceService(
                    repo,
                    gateway,
                    clock
                ).manualCheck()
            )
            assertEquals(CheckRejection.LOCAL_SAVE_FAILED, result.reason)
            assertEquals(before, repo.loadDay(fixturePlan().key)?.revision)
            assertEquals(dirty, db.dao().dirtyProjections(fixturePlan().key.trackerId))
            assertNull(repo.loadDay(fixturePlan().key)?.evidence)
            db.useWriterConnection { it.executeSQL("DROP TRIGGER abort_overview") }
            assertIs<CheckOutcome.Recorded>(AttendanceService(repo, gateway, clock).manualCheck())
        }
    }

    @Test
    fun setup_after_deadline_has_no_retroactive_absence_but_today_can_be_corrected() = runTest {
        val folder = Files.createTempDirectory("daymark-after-setup-")
        val db = openJvmDatabase(folder.resolve("attendance.db").toString())
        try {
            val now = at("10:00:00");
            val clock = TestClock(now)
            seedDatabase(db, now, Instant.parse("2026-10-28T09:00:00Z"))
            val repo = RoomAttendanceRepository(db, clock)
            repo.reconcile(now)
            assertEquals(0, db.dao().countDay(fixturePlan().key.trackerId, fixtureDate.toString()))
            assertEquals(0, repo.observeMonth(2026, 10).first().absent)
            repo.correct(
                Correction(
                    fixturePlan().key,
                    AttendanceStatus.LATE,
                    LocalTime(9, 30),
                    null
                )
            )
            assertEquals(
                AttendanceSource.MANUAL_CORRECTION,
                repo.loadDay(fixturePlan().key)?.evidence?.source
            )
            assertEquals(1, repo.observeMonth(2026, 10).first().late)
        } finally {
            db.close(); folder.toFile().deleteRecursively()
        }
    }

    @Test
    fun a_future_configuration_does_not_replace_today_snapshot_or_reject_its_valid_check() =
        runTest {
            withDatabase { db, _ ->
                val repo = RoomAttendanceRepository(db, TestClock())
                repo.reconcile(at("09:02:00"))
                db.dao().putVersion(
                    PlanVersionEntity.fromSnapshot(
                        fixturePlan().key.trackerId,
                        2,
                        LocalDate(2026, 10, 28),
                        Instant.parse("2026-10-28T00:00:00Z"),
                        TrackerCategory.COURSE,
                        fixtureSchedule(arrival = LocalTime(10, 0)),
                        fixturePlace().copy(version = 2),
                        "New place"
                    )
                )
                assertIs<CheckOutcome.Recorded>(repo.completeCheck(testCommit()))
                val day = requireNotNull(repo.loadDay(fixturePlan().key))
                assertEquals(1L, day.plan.scheduleVersion)
                assertEquals(TrackerCategory.WORK, day.plan.category)
                assertEquals(at("09:00:00"), day.plan.arrival)
            }
        }

    @Test
    fun local_schema_has_required_tables_but_no_token_or_raw_check_position_columns() = runTest {
        withDatabase { db, _ ->
            val expected = setOf(
                "settings", "trackers", "plan_versions", "attendance", "time_off",
                "balance_adjustments", "report_bindings", "dirty_projections", "report_batches"
            )
            val tables = db.useReaderConnection { connection ->
                connection.usePrepared("SELECT name FROM sqlite_master WHERE type='table'") { statement ->
                    buildSet { while (statement.step()) add(statement.getText(0)) }
                }
            }
            assertTrue(tables.containsAll(expected))
            for (table in expected) db.useReaderConnection { connection ->
                connection.usePrepared("PRAGMA table_info($table)") { statement ->
                    while (statement.step()) assertFalse(
                        statement.getText(1) in setOf(
                            "accessToken",
                            "refreshToken",
                            "checkLatitude",
                            "checkLongitude"
                        )
                    )
                }
            }
            val clock = TestClock();
            val repo = RoomAttendanceRepository(db, clock)
            val gateway = object : LocationGateway {
                override suspend fun requestFix(budget: Duration) =
                    fixtureFix(latitude = 0.00001, longitude = 0.00002)
            }
            assertIs<CheckOutcome.Recorded>(AttendanceService(repo, gateway, clock).manualCheck())
            assertEquals(fixturePlace(), repo.loadDay(fixturePlan().key)?.plan?.place)
        }
    }
}
