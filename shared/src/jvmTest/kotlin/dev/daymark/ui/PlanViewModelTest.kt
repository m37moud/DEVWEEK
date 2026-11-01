package dev.daymark.ui

import dev.daymark.*
import dev.daymark.attendance.*
import dev.daymark.data.*
import dev.daymark.location.*
import dev.daymark.plan.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.*
import java.nio.file.Files
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Instant

class PlanViewModelTest {
    private val permissions = object : LocationPermissionPort {
        override fun current() = LocationCapabilities(true,true,true,true)
        override suspend fun requestForeground() = current()
        override suspend fun requestBackground() = current()
        override fun openSystemSettings() = Unit
    }
    private val location = object : LocationGateway { override suspend fun requestFix(budget: Duration) = fixtureFix() }
    @Test fun schedule_change_preserves_today_and_stale_preview_cannot_commit() = runTest {
        val folder = Files.createTempDirectory("daymark-plan-ui-"); val db = openJvmDatabase(folder.resolve("test.db").toString())
        try {
            seedDatabase(db)
            val clock = TestClock(); val repo = RoomAttendanceRepository(db,clock)
            repo.reconcile(clock.now())
            val off = TimeOffService(db,clock)
            val model = PlanViewModel(PlanService(db,clock),off,location,permissions,clock,backgroundScope)
            val draft = model.uiState.first { it.ready }.draft!!
            for (grace in listOf(0,5,120)) {
                model.edit { it.copy(schedule = it.schedule.copy(graceMinutes = grace)) }
                assertEquals(grace,model.uiState.value.draft!!.schedule.graceMinutes)
            }
            model.edit { it.copy(schedule = it.schedule.copy(overrides = mapOf(DayOfWeek.TUESDAY to LocalTime(9,15)))) }
            assertEquals(LocalTime(9,15),model.uiState.value.draft!!.schedule.overrides[DayOfWeek.TUESDAY])
            model.edit { it.copy(schedule = it.schedule.copy(overrides = emptyMap())) }
            assertTrue(model.uiState.value.draft!!.schedule.overrides.isEmpty())
            model.preview(draft.copy(schedule = draft.schedule.copy(arrival = LocalTime(9,30))))
            val preview = model.uiState.first { !it.busy && it.preview != null }.preview!!
            assertEquals(LocalDate(2026,10,28),preview.effectiveFrom)
            model.confirm(preview.expectedRevision)
            model.uiState.first { !it.busy && it.result == PlanMutationResult.Applied }
            assertEquals(at("09:00:00"),repo.loadCurrentDay(clock.now())!!.plan.arrival)
            val reader = RoomPlanReader(db,clock); reader.context()
            assertEquals(LocalTime(9,30),reader.day(LocalDate(2026,10,28)).plan.arrival.toLocalDateTime(TimeZone.UTC).time)
            model.preview(draft)
            val stale = model.uiState.first { !it.busy && it.preview != null }.preview!!
            off.setAvailableBalance(11,null)
            val before = db.dao().tracker()!!.changeRevision
            model.confirm(stale.expectedRevision)
            model.uiState.first { !it.busy && it.result == PlanMutationResult.StalePreview }
            assertEquals(before,db.dao().tracker()!!.changeRevision)
            model.edit { it.copy(schedule = it.schedule.copy(days = emptySet())) }
            assertTrue(model.uiState.value.invalidInput)
            model.edit { it.copy(place = it.place.copy(radiusMetres = 99)) }
            assertTrue(model.uiState.value.invalidInput)
        } finally { db.close(); folder.toFile().deleteRecursively() }
    }
    @Test fun historical_vacation_replace_and_remove_preview_exact_balance_and_outcomes() = runTest {
        val folder = Files.createTempDirectory("daymark-off-ui-"); val db = openJvmDatabase(folder.resolve("test.db").toString())
        try {
            seedDatabase(db)
            val clock = TestClock(Instant.parse("2026-10-29T09:18:00Z"))
            val model = PlanViewModel(PlanService(db,clock),TimeOffService(db,clock),location,permissions,clock,backgroundScope)
            model.uiState.first { it.ready }
            model.previewTimeOff(TimeOffCommand.Add(TimeOffKind.VACATION,fixtureDate,LocalDate(2026,10,28)))
            val added = model.uiState.first { it.timeOffPreview != null }.timeOffPreview!!
            assertEquals(-2,added.balanceDelta); assertEquals(8,added.availableAfter)
            model.confirmTimeOff(added.expectedRevision)
            val entry = model.uiState.first { !it.busy && it.entries.isNotEmpty() }.entries.single()
            model.previewTimeOff(TimeOffCommand.Replace(entry.id,TimeOffKind.VACATION,LocalDate(2026,10,28),LocalDate(2026,10,28)))
            val replaced = model.uiState.first { !it.busy && it.timeOffPreview != null }.timeOffPreview!!
            assertEquals(1,replaced.balanceDelta)
            assertEquals(AttendanceStatus.ABSENT,replaced.resultingOutcomes.first().status)
            model.confirmTimeOff(replaced.expectedRevision)
            model.uiState.first { !it.busy && it.result == PlanMutationResult.Applied }
            model.previewTimeOff(TimeOffCommand.Remove(entry.id))
            val removed = model.uiState.first { !it.busy && it.timeOffPreview != null }.timeOffPreview!!
            assertEquals(1,removed.balanceDelta); assertEquals(10,removed.availableAfter)
            model.confirmTimeOff(removed.expectedRevision)
            model.uiState.first { !it.busy && it.entries.isEmpty() }
            assertEquals(10,TimeOffService(db,clock).observeAvailableBalance().first())
        } finally { db.close(); folder.toFile().deleteRecursively() }
    }
}
