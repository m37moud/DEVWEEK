package dev.daymark.plan

import androidx.room3.withReadTransaction
import dev.daymark.attendance.*
import dev.daymark.data.DaymarkDatabase
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

/** Each planning pass reads a consistent snapshot; the claim transaction revalidates it before GPS. */
class RoomPlanReader(private val db: DaymarkDatabase, private val clock: Clock) : PlanReader {
    private var snapshot: PlanningStore? = null
    override suspend fun context(): ScheduleContext? {
        val store = db.withReadTransaction { db.dao().planning(clock.now()) }
        snapshot = store
        return store?.let { ScheduleContext(it.tracker.id, it.active.schedule().zoneId,
            kotlin.time.Instant.parse(it.tracker.trackingStartsAt), it.tracker.automaticEnabled) }
    }
    override suspend fun day(date: LocalDate): PlannedDay {
        val store = requireNotNull(snapshot) { "Read the schedule context first" }
        val evidence = store.rows[date]?.evidence()
        return PlannedDay(store.plan(date), timeOffOn(store.entries, date), evidence?.let {
            it.source == AttendanceSource.MANUAL_CORRECTION || it.status in listOf(AttendanceStatus.PRESENT, AttendanceStatus.LATE)
        } == true, store.rows[date]?.automaticState?.let { it != AutomaticState.UNCLAIMED.name } == true)
    }
}
