package dev.daymark.location

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Instant

class IosLocationTimeTest {
    @Test
    fun sample_capture_preserves_submillisecond_time_after_deadline() {
        val fix = iosLocationFix(
            25.0, 30.0, 10.0, 1793091900.0005,
            Instant.parse("2026-10-27T09:05:01Z")
        )
        assertTrue(fix.capturedAt > Instant.parse("2026-10-27T09:05:00Z"))
        assertTrue(fix.capturedAt < Instant.parse("2026-10-27T09:05:00.001Z"))
        assertTrue(requireNotNull(fix.ageAtReceipt) > Duration.ZERO)
    }
}
