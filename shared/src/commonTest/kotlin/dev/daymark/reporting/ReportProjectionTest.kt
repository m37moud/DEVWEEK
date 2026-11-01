package dev.daymark.reporting

import dev.daymark.*
import dev.daymark.attendance.*
import dev.daymark.plan.*
import kotlinx.datetime.*
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.*
import kotlin.time.Instant

class ReportProjectionTest {
    @Test fun leap_day_row_and_explicit_optional_clearing() {
        val date = LocalDate(2028,2,29)
        val plan = fixturePlan(date = date)
        val at = Instant.parse("2028-02-29T09:18:00Z")
        val evidence = AttendanceEvidence(AttendanceStatus.PRESENT,AttendanceSource.MANUAL_CORRECTION,at,"=1+2",at)
        val record = HistoryRecord(plan,evidence,evaluateDay(plan,evidence,null,at))
        val withNote = projectDay(record,7,at).ranges.single()
        assertEquals("'Attendance 2028'!A62:O62",withNote.a1)
        assertEquals(15,withNote.cells.single().size)
        assertEquals(JsonPrimitive("=1+2"),withNote.cells.single()[9])
        assertEquals(JsonPrimitive("2028-02-29"),withNote.cells.single()[1])
        assertEquals(JsonPrimitive("PRESENT"),withNote.cells.single()[5])
        assertEquals(JsonPrimitive("MANUAL_CORRECTION"),withNote.cells.single()[8])
        assertEquals(JsonPrimitive("09:18"),withNote.cells.single()[7])
        assertEquals(JsonPrimitive(at.toString()),withNote.cells.single()[14])
        val without = record.copy(evidence = evidence.copy(note = null))
        assertEquals(JsonPrimitive(""),projectDay(without,8,at).ranges.single().cells.single()[9])
        val absent = record.copy(evidence = null,outcome = DayOutcome(AttendanceStatus.ABSENT,AttendanceSource.NONE,null,null))
        val cleared = projectDay(absent,9,at).ranges.single().cells.single()
        assertEquals(JsonPrimitive(""),cleared[7]); assertEquals(JsonPrimitive(""),cleared[14])
        assertEquals(withNote.a1,projectDay(absent,9,at).ranges.single().a1)
    }
    @Test fun time_off_removal_clears_the_full_previously_published_capacity() {
        val projection = projectTimeOff(emptyList(),4,previousCapacity = 3)
        val range = projection.ranges.single()
        assertEquals("'Time off'!A3:F5",range.a1)
        assertEquals(3,range.cells.size)
        assertTrue(range.cells.flatten().all { it == JsonPrimitive("") })
        assertEquals(listOf("trackerId","date","category"),reportMachineColumns.take(3))
        assertTrue(reportHumanColumns.all { it.contains("/") })
    }
}
