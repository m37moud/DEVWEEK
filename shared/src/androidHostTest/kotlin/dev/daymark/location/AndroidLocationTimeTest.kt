package dev.daymark.location

import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class AndroidLocationTimeTest {
    @Test
    fun freshness_uses_native_elapsed_time_instead_of_wall_clock_delta() {
        val fix = androidLocationFix(
            25.0, 30.0, 10.0, 1793091720000L,
            30_000_000_000L, 31_000_000_000L, Instant.parse("2026-10-27T09:02:03Z")
        )
        assertEquals(Instant.parse("2026-10-27T09:02:00Z"), fix.capturedAt)
        assertEquals(1.seconds, fix.ageAtReceipt)
    }

    @Test
    fun future_native_elapsed_timestamp_cannot_invent_a_fresh_sample() {
        val fix = androidLocationFix(
            25.0, 30.0, 10.0, 1793091720000L,
            32_000_000_000L, 31_000_000_000L, Instant.parse("2026-10-27T09:02:03Z")
        )
        assertNull(fix.ageAtReceipt)
    }
}
