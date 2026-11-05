package dev.daymark.ui

import dev.daymark.at
import dev.daymark.attendance.TestClock
import dev.daymark.data.local.attendance.RoomAttendanceRepository
import dev.daymark.data.local.dashboard.TodayDashboardReader
import dev.daymark.data.local.openJvmDatabase
import dev.daymark.data.local.plan.TimeOffService
import dev.daymark.data.seedDatabase
import dev.daymark.data.testCommit
import dev.daymark.fixtureDate
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.models.plan.TimeOffCommand
import dev.daymark.plan.TimeOffKind
import dev.daymark.presentation.component.calendarWeeks
import dev.daymark.presentation.features.history.HistoryViewModel
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class TodayDashboardTest {
    @Test
    fun dashboard_place_totals_week_and_next_day_share_authoritative_history_without_automation(): Unit =
        runBlocking {
            val folder = Files.createTempDirectory("daymark-today-dashboard-");
            val db = openJvmDatabase(folder.resolve("test.db").toString())
            try {
                seedDatabase(db); db.dao()
                    .putTracker(db.dao().tracker()!!.copy(automaticEnabled = false))
                val clock = TestClock();
                val repo = RoomAttendanceRepository(db, clock); repo.completeCheck(testCommit())
                val reader = TodayDashboardReader(db, clock)
                val first = reader.observe().first { it.ready }
                assertEquals("Registered place", first.placeName)
                assertEquals(1, first.present); assertEquals(0, first.late); assertEquals(
                    0,
                    first.absent
                )
                assertEquals(7, first.week.size)
                assertEquals(
                    AttendanceStatus.PRESENT,
                    first.week.single { it.date == fixtureDate }.status
                )
                assertTrue(first.week.single {
                    it.date == fixtureDate.plus(
                        1,
                        DateTimeUnit.DAY
                    )
                }.planned)
                val service = TimeOffService(db, clock)
                val command = TimeOffCommand.Add(TimeOffKind.HOLIDAY, fixtureDate, fixtureDate)
                assertEquals(
                    PlanMutationResult.Applied,
                    service.apply(command, service.preview(command).expectedRevision)
                )
                val off = reader.observe().first { it.ready }
                assertEquals(0, off.present)
                assertEquals(fixtureDate.plus(1, DateTimeUnit.DAY), off.nextAttendance?.key?.date)
                assertEquals(
                    AttendanceStatus.HOLIDAY,
                    off.week.single { it.date == fixtureDate }.status
                )
            } finally {
                db.close(); folder.toFile().deleteRecursively()
            }
        }

    @Test
    fun calendar_grid_includes_leap_day_and_never_invents_a_historical_outcome() {
        val grid = calendarWeeks(2028, 2)
        assertEquals(29, grid.flatten().filterNotNull().size)
        assertTrue(grid.all { it.size == 7 })
        assertEquals(LocalDate(2028, 2, 29), grid.flatten().filterNotNull().last())
    }

    @Test
    fun open_history_advances_its_calendar_and_materializes_the_new_day_at_midnight(): Unit =
        kotlinx.coroutines.runBlocking {
            val folder = Files.createTempDirectory("daymark-history-midnight-");
            val db = openJvmDatabase(folder.resolve("test.db").toString())
            val scope =
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
            try {
                seedDatabase(db);
                val clock = TestClock(at("23:59:59.750"));
                val repo = RoomAttendanceRepository(db, clock)
                repo.reconcile(clock.now())
                val model = HistoryViewModel(
                    repository = dev.daymark.testRepository(attendanceSource = repo),
                    initialDate = fixtureDate,
                    scope = scope,
                    clock = clock,
                    today = { clock.now().toLocalDateTime(TimeZone.UTC).date },
                    refreshBoundary = {
                        clock.now().toLocalDateTime(TimeZone.UTC).date.plus(
                            1,
                            DateTimeUnit.DAY
                        ).atStartOfDayIn(TimeZone.UTC)
                    })
                kotlinx.coroutines.withTimeout(5_000) { model.uiState.first { it.ready && it.today == fixtureDate } }
                clock.value = kotlin.time.Instant.parse("2026-10-28T00:00:00Z")
                val after = kotlinx.coroutines.withTimeout(5_000) {
                    model.uiState.first {
                        it.today == LocalDate(
                            2026,
                            10,
                            28
                        ) && it.history.records.any { row ->
                            row.plan.key.date == LocalDate(
                                2026,
                                10,
                                28
                            )
                        }
                    }
                }
                assertEquals(
                    AttendanceStatus.SCHEDULED,
                    after.history.records.single {
                        it.plan.key.date == LocalDate(
                            2026,
                            10,
                            28
                        )
                    }.outcome.status
                )
                assertTrue(after.history.records.any { it.plan.key.date == fixtureDate })
                model.setActive(false)
            } finally {
                scope.coroutineContext[kotlinx.coroutines.Job]?.cancel(); db.close(); folder.toFile()
                    .deleteRecursively()
            }
        }

}
