package dev.daymark.shared

import dev.daymark.location.LocationFailure
import dev.daymark.location.LocationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

class MacLocationReplyTest {
    private val received = Instant.fromEpochMilliseconds(1_000_000)

    @Test
    fun native_sample_keeps_capture_time_accuracy_and_age_including_bridge_delay() {
        val fix = assertIs<LocationResult.Fix>(
            macLocationReply("FIX\t30.0\t31.0\t12.0\t998000\t999000\t1000", received)
        )
        assertEquals(Instant.fromEpochMilliseconds(998_000), fix.capturedAt)
        assertEquals(received, fix.receivedAt)
        assertEquals(2_000, fix.ageAtReceipt?.inWholeMilliseconds)
        assertEquals(12.0, fix.horizontalAccuracyMetres)
    }

    @Test
    fun malformed_or_invalid_native_evidence_never_becomes_a_fix() {
        listOf(
            "", "FIX\t30\t31", "FIX\tNaN\t31\t12\t998000\t999000\t1000",
            "FIX\t91\t31\t12\t998000\t999000\t1000",
            "FIX\t30\t181\t12\t998000\t999000\t1000",
            "FIX\t30\t31\t-1\t998000\t999000\t1000",
            "FIX\t30\t31\t12\t1000001\t999000\t1000",
            "FIX\t30\t31\t12\t998000\t1000001\t1000",
            "FIX\t30\t31\t12\t998000\t999000\t-1",
            "FIX\t30\t31\t12\t998000\t999000\tNaN"
        ).forEach { reply ->
            assertEquals(LocationResult.Failure(LocationFailure.UNAVAILABLE), macLocationReply(reply, received))
        }
    }

    @Test
    fun age_cannot_contradict_the_native_capture_timestamp() {
        val fix = assertIs<LocationResult.Fix>(
            macLocationReply("FIX\t30\t31\t12\t960000\t999000\t0", received)
        )
        assertEquals(40_000, fix.ageAtReceipt?.inWholeMilliseconds)
    }

    @Test
    fun native_failures_stay_distinct() {
        LocationFailure.entries.forEach { reason ->
            assertEquals(LocationResult.Failure(reason), macLocationReply("ERROR\t${reason.name}", received))
        }
    }
}
