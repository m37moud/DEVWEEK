package dev.daymark.attendance

import dev.daymark.data.local.RoomDatabaseTransactions

import androidx.room3.executeSQL
import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import androidx.room3.withWriteTransaction
import dev.daymark.at
import dev.daymark.data.local.DaymarkDatabase
import dev.daymark.data.local.PlanVersionEntity
import dev.daymark.data.local.attendance.RoomAttendanceRepository
import dev.daymark.data.local.openJvmDatabase
import dev.daymark.data.seedDatabase
import dev.daymark.data.testCommit
import dev.daymark.fixtureDate
import dev.daymark.fixtureFix
import dev.daymark.fixturePlace
import dev.daymark.fixturePlan
import dev.daymark.fixtureSchedule
import dev.daymark.data.local.settings.SettingsRepository
import dev.daymark.location.LocationFailure
import dev.daymark.location.LocationGateway
import dev.daymark.location.LocationResult
import dev.daymark.models.attendance.AttendanceSource
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.models.attendance.AutomaticState
import dev.daymark.models.attendance.CheckOutcome
import dev.daymark.models.attendance.CheckRejection
import dev.daymark.models.attendance.ClaimResult
import dev.daymark.models.attendance.Correction
import dev.daymark.models.attendance.FixVerdict
import dev.daymark.models.attendance.TrackerCategory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

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
    fun approved_retry_survives_repository_restart_and_reserves_one_final_notice() = runTest {
        withDatabase { db, _ ->
            val clock = TestClock(at("09:00:00"))
            val transactions = RoomDatabaseTransactions(db)
            val settings = SettingsRepository(db.profileDao(), db.dirtyProjectionDao(), transactions)
            settings.update(automaticAttemptLimit = 2)
            val key = fixturePlan().key
            var requests = 0
            val gateway = object : LocationGateway {
                override suspend fun requestFix(budget: Duration): LocationResult {
                    requests++
                    if (requests == 1) {
                        clock.value = at("09:00:30")
                        return LocationResult.Failure(LocationFailure.TIMEOUT)
                    }
                    return fixtureFix(capturedAt = clock.now())
                }
            }
            fun repository() = RoomAttendanceRepository(
                db.profileDao(), db.attendanceDao(), db.planningDao(), db.dirtyProjectionDao(), transactions, clock
            )
            val original = repository()
            val first = AttendanceService(original, gateway, clock)
            assertEquals(CheckRejection.LOCATION_TIMEOUT, first.runAutomatic(key).reason)
            val queued = original.recoverAutomatic(clock.now(), locationEnabled = true)
            assertEquals(at("09:01:30"), queued.next?.eligibleAt)
            assertEquals(2, queued.next?.attemptOrdinal)
            assertNull(queued.delivery)
            settings.update(automaticAttemptLimit = 1)

            val restarted = repository()
            val worker = AttendanceService(restarted, gateway, clock)
            clock.value = at("09:01:29.999")
            assertIs<CheckOutcome.Busy>(worker.runAutomatic(key = key, ordinal = 2))
            clock.value = at("09:01:30")
            assertIs<CheckOutcome.Busy>(worker.runAutomatic(key = key, ordinal = 1))
            assertIs<CheckOutcome.Recorded>(worker.runAutomatic(key = key, ordinal = 2))
            assertIs<CheckOutcome.Busy>(worker.runAutomatic(key = key, ordinal = 2))
            assertEquals(2, requests)
            val terminal = restarted.recoverAutomatic(clock.now(), locationEnabled = true)
            assertIs<CheckOutcome.Recorded>(terminal.delivery?.outcome)
            assertNull(terminal.next)
            assertNull(restarted.recoverAutomatic(clock.now(), locationEnabled = true).delivery)
        }
    }

    @Test
    fun retry_that_cannot_fit_ends_with_the_last_saved_failure() = runTest {
        withDatabase { db, _ ->
            val clock = TestClock(at("09:03:00"))
            val transactions = RoomDatabaseTransactions(db)
            SettingsRepository(db.profileDao(), db.dirtyProjectionDao(), transactions).update(automaticAttemptLimit = 3)
            val repository = RoomAttendanceRepository(
                db.profileDao(), db.attendanceDao(), db.planningDao(), db.dirtyProjectionDao(), transactions, clock
            )
            val key = fixturePlan().key
            var requests = 0
            val gateway = object : LocationGateway {
                override suspend fun requestFix(budget: Duration): LocationResult {
                    requests++
                    clock.value = at("09:03:30")
                    return LocationResult.Failure(LocationFailure.TIMEOUT)
                }
            }
            val service = AttendanceService(repository, gateway, clock)
            service.runAutomatic(key)
            clock.value = at("09:04:30.001")
            assertIs<CheckOutcome.Busy>(service.runAutomatic(key = key, ordinal = 2))
            val terminal = repository.recoverAutomatic(clock.now(), locationEnabled = true)
            assertNull(terminal.next)
            assertEquals(CheckRejection.LOCATION_TIMEOUT, terminal.delivery?.outcome?.reason)
            assertNull(repository.recoverAutomatic(clock.now(), locationEnabled = true).delivery)
            assertEquals(1, requests)
        }
    }

    @Test
    fun manual_success_correction_and_automation_off_cancel_waiting_retries() = runTest {
        for (stop in listOf("manual", "correction", "disabled")) {
            withDatabase { db, _ ->
                val clock = TestClock(at("09:00:15"))
                val transactions = RoomDatabaseTransactions(db)
                val settings = SettingsRepository(db.profileDao(), db.dirtyProjectionDao(), transactions)
                settings.update(automaticAttemptLimit = 2)
                val repository = RoomAttendanceRepository(
                    db.profileDao(), db.attendanceDao(), db.planningDao(), db.dirtyProjectionDao(), transactions, clock
                )
                val key = fixturePlan().key
                var requests = 0
                val gateway = object : LocationGateway {
                    override suspend fun requestFix(budget: Duration): LocationResult {
                        requests++
                        if (requests == 1) return LocationResult.Failure(LocationFailure.TIMEOUT)
                        check(stop == "manual")
                        return fixtureFix(capturedAt = clock.now())
                    }
                }
                val service = AttendanceService(repository, gateway, clock)
                service.runAutomatic(key)
                when (stop) {
                    "manual" -> assertIs<CheckOutcome.Recorded>(service.manualCheck())
                    "correction" -> repository.correct(Correction(key, AttendanceStatus.ABSENT, null, null))
                    else -> {
                        settings.setAutomation(expectedTrackerId = key.trackerId, enabled = false)
                        settings.setAutomation(expectedTrackerId = key.trackerId, enabled = true)
                    }
                }
                clock.value = at("09:01:15")
                val recovery = repository.recoverAutomatic(clock.now(), locationEnabled = true)
                assertNull(recovery.next)
                assertNull(recovery.delivery)
                assertIs<CheckOutcome.Busy>(service.runAutomatic(key = key, ordinal = 2))
                assertEquals(if (stop == "manual") 2 else 1, requests)
            }
        }
    }

    @Test
    fun departure_transaction_rejects_later_capture_and_accepts_delayed_cutoff_capture() = runTest {
        withDatabase { db, _ ->
            val clock = TestClock(at("17:00:20"))
            val repo = RoomAttendanceRepository(
                db.profileDao(),
                db.attendanceDao(),
                db.planningDao(),
                db.dirtyProjectionDao(),
                RoomDatabaseTransactions(db),
                clock
            )
            val version = db.planningDao().versions(fixturePlan().key.trackerId).single()
            db.planningDao().putVersion(
                version.copy(
                    scheduleJson = dev.daymark.data.local.encodeSchedule(
                        fixtureSchedule().copy(
                            departure = LocalTime(17, 0)
                        )
                    )
                )
            )
            val late =
                testCommit(verdict = FixVerdict.Accepted(AttendanceStatus.LATE, at("17:00:00.001")))
            assertEquals(
                CheckRejection.AFTER_DEPARTURE,
                assertIs<CheckOutcome.Rejected>(repo.completeCheck(late)).reason
            )
            val cutoff =
                testCommit(verdict = FixVerdict.Accepted(AttendanceStatus.LATE, at("17:00:00")))
            assertIs<CheckOutcome.Recorded>(repo.completeCheck(cutoff))
            assertEquals(at("17:00:00"), repo.loadDay(fixturePlan().key)!!.plan.departure)
            assertEquals(at("17:00:00"), repo.loadDay(fixturePlan().key)!!.evidence!!.confirmedAt)
        }
    }

    @Test
    fun claim_is_unique_and_remains_consumed_after_reopen() = runTest {
        withDatabase { db, path ->
            val clock = TestClock();
            val repo = RoomAttendanceRepository(
                db.profileDao(),
                db.attendanceDao(),
                db.planningDao(),
                db.dirtyProjectionDao(),
                RoomDatabaseTransactions(db),
                clock
            )
            val key = fixturePlan().key
            val claims = coroutineScope {
                (1..8).map {
                    async(Dispatchers.Default) {
                        repo.claimAutomatic(key, "attempt-$it", clock.now())
                    }
                }.awaitAll()
            }
            assertEquals(1, claims.count { it is ClaimResult.Claimed })
            assertEquals(1, db.attendanceDao().countDay(key.trackerId, key.date.toString()))
            db.close()
            val reopened = openJvmDatabase(path.toString())
            try {
                val next = RoomAttendanceRepository(
                    reopened.profileDao(),
                    reopened.attendanceDao(),
                    reopened.planningDao(),
                    reopened.dirtyProjectionDao(),
                    RoomDatabaseTransactions(reopened),
                    clock
                )
                assertIs<ClaimResult.AlreadyConsumed>(
                    next.claimAutomatic(
                        key,
                        "another-attempt",
                        clock.now()
                    )
                )
                next.reconcile(clock.now())
                assertEquals(AutomaticState.INTERRUPTED, next.loadDay(key)?.automaticState)
                assertEquals(
                    1,
                    reopened.attendanceDao().countDay(key.trackerId, key.date.toString())
                )
            } finally {
                reopened.close()
            }
        }
    }

    @Test
    fun concurrent_reconciliation_creates_one_date_row() = runTest {
        withDatabase { db, _ ->
            val clock = TestClock();
            val repo = RoomAttendanceRepository(
                db.profileDao(),
                db.attendanceDao(),
                db.planningDao(),
                db.dirtyProjectionDao(),
                RoomDatabaseTransactions(db),
                clock
            )
            coroutineScope {
                (1..8).map { async(Dispatchers.Default) { repo.reconcile(clock.now()) } }.awaitAll()
            }
            assertEquals(
                1,
                db.attendanceDao().countDay(fixturePlan().key.trackerId, fixtureDate.toString())
            )
        }
    }

    @Test
    fun reconciliation_catches_up_after_restart_and_reevaluates_after_clock_rollback() = runTest {
        withDatabase { db, _ ->
            val clock = TestClock(at("09:02:00"))
            val key = fixturePlan().key
            val transactions = RoomDatabaseTransactions(database = db)
            fun newRepository() = RoomAttendanceRepository(
                profileDao = db.profileDao(),
                attendanceDao = db.attendanceDao(),
                planningDao = db.planningDao(),
                dirtyProjectionDao = db.dirtyProjectionDao(),
                transactions = transactions,
                clock = clock
            )

            val initial = newRepository()
            initial.reconcile(now = clock.now())
            assertIs<ClaimResult.Claimed>(
                initial.claimAutomatic(
                    key = key,
                    attemptId = "attempt",
                    now = clock.now()
                )
            )
            clock.value += 40.days
            val restarted = newRepository()
            restarted.reconcile(now = clock.now())

            val saved = db.attendanceDao().days(trackerId = key.trackerId)
            assertEquals(41, saved.size)
            assertEquals(AttendanceStatus.ABSENT.name, saved.first().cachedStatus)
            assertEquals(AutomaticState.INTERRUPTED.name, saved.first().automaticState)
            clock.value = at("09:02:00")
            restarted.reconcile(now = clock.now())
            val recalculated = db.attendanceDao().days(trackerId = key.trackerId)
            assertEquals(saved.map { it.date }, recalculated.map { it.date })
            assertEquals(AttendanceStatus.AWAITING_CHECK.name, recalculated.first().cachedStatus)
            assertEquals(AutomaticState.INTERRUPTED.name, recalculated.first().automaticState)
        }
    }

    @Test
    fun correction_wins_while_a_real_database_check_is_acquiring_location() = runTest {
        withDatabase { db, _ ->
            val clock = TestClock();
            val repo = RoomAttendanceRepository(
                db.profileDao(),
                db.attendanceDao(),
                db.planningDao(),
                db.dirtyProjectionDao(),
                RoomDatabaseTransactions(db),
                clock
            )
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
            val repo = RoomAttendanceRepository(
                db.profileDao(),
                db.attendanceDao(),
                db.planningDao(),
                db.dirtyProjectionDao(),
                RoomDatabaseTransactions(db),
                clock
            )
            repo.reconcile(clock.now())
            val key = fixturePlan().key
            db.withWriteTransaction {
                val row = requireNotNull(db.attendanceDao().day(key.trackerId, key.date.toString()))
                db.attendanceDao().putDay(row.copy(userEditRevision = row.userEditRevision + 2))
            }
            assertIs<CheckOutcome.StateChanged>(repo.completeCheck(testCommit()))
            assertNull(repo.loadDay(key)?.evidence)
        }
    }

    @Test
    fun failed_check_cannot_downgrade_and_earlier_success_wins() = runTest {
        withDatabase { db, _ ->
            val repo = RoomAttendanceRepository(
                db.profileDao(),
                db.attendanceDao(),
                db.planningDao(),
                db.dirtyProjectionDao(),
                RoomDatabaseTransactions(db),
                TestClock()
            )
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
            val repo = RoomAttendanceRepository(
                db.profileDao(),
                db.attendanceDao(),
                db.planningDao(),
                db.dirtyProjectionDao(),
                RoomDatabaseTransactions(db),
                clock
            )
            repo.reconcile(clock.now())
            val before = requireNotNull(repo.loadDay(fixturePlan().key)).revision
            val dirty = db.dirtyProjectionDao().dirtyProjections(fixturePlan().key.trackerId)
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
            assertEquals(
                dirty,
                db.dirtyProjectionDao().dirtyProjections(fixturePlan().key.trackerId)
            )
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
            val repo = RoomAttendanceRepository(
                db.profileDao(),
                db.attendanceDao(),
                db.planningDao(),
                db.dirtyProjectionDao(),
                RoomDatabaseTransactions(db),
                clock
            )
            repo.reconcile(now)
            assertEquals(
                0,
                db.attendanceDao().countDay(fixturePlan().key.trackerId, fixtureDate.toString())
            )
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
                val repo =
                    RoomAttendanceRepository(
                        db.profileDao(),
                        db.attendanceDao(),
                        db.planningDao(),
                        db.dirtyProjectionDao(),
                        RoomDatabaseTransactions(db),
                        TestClock()
                    )
                repo.reconcile(at("09:02:00"))
                db.planningDao().putVersion(
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
            val repo = RoomAttendanceRepository(
                db.profileDao(),
                db.attendanceDao(),
                db.planningDao(),
                db.dirtyProjectionDao(),
                RoomDatabaseTransactions(db),
                clock
            )
            val gateway = object : LocationGateway {
                override suspend fun requestFix(budget: Duration) =
                    fixtureFix(latitude = 0.00001, longitude = 0.00002)
            }
            assertIs<CheckOutcome.Recorded>(AttendanceService(repo, gateway, clock).manualCheck())
            assertEquals(fixturePlace(), repo.loadDay(fixturePlan().key)?.plan?.place)
        }
    }
}
