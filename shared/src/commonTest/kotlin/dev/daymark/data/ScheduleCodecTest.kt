package dev.daymark.data

import dev.daymark.data.local.decodeSchedule
import dev.daymark.data.local.encodeSchedule
import dev.daymark.fixtureSchedule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

class ScheduleCodecTest {
    @Test
    fun legacy_schedule_has_no_cutoff() {
        val schedule =
            decodeSchedule("""{"days":["TUESDAY"],"arrival":"09:00","overrides":{},"graceMinutes":5,"zoneId":"UTC"}""")
        assertNull(schedule.departure)
        assertEquals(emptyMap(), schedule.departureOverrides)
    }

    @Test
    fun schedule_roundtrip_preserves_inherit_custom_and_disabled_cutoffs() {
        val schedule = fixtureSchedule().copy(
            departure = LocalTime(17, 0),
            departureOverrides = mapOf(
                DayOfWeek.TUESDAY to null,
                DayOfWeek.WEDNESDAY to LocalTime(16, 0)
            )
        )
        assertEquals(schedule, decodeSchedule(encodeSchedule(schedule)))
    }
}
