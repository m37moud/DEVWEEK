package dev.daymark.location

import dev.daymark.attendance.classifyFix
import dev.daymark.fixturePlan
import dev.daymark.models.attendance.CheckMode
import dev.daymark.models.attendance.CheckRejection
import dev.daymark.models.attendance.FixVerdict
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlin.time.Clock
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BoundedLocationSamplingTest {
    @Test
    fun inaccurate_and_stale_samples_wait_for_the_first_acceptable_fix() = runTest {
        for (rejected in listOf(
            sampleFix(accuracy = 50.001), sampleFix(ageSeconds = 31),
            sampleFix().copy(ageAtReceipt = null)
        )) {
            val source = ControlledLocationSource()
            val gateway = BoundedLocationGateway(source, sampleClock)
            val request = async { gateway.requestFix(30.seconds) }
            runCurrent()
            source.reply(rejected)
            runCurrent()
            assertFalse(request.isCompleted)
            source.reply(sampleFix())
            source.reply(sampleFix(accuracy = 1.0))
            runCurrent()
            assertEquals(
                10.0,
                assertIs<LocationResult.Fix>(request.await()).horizontalAccuracyMetres
            )
            assertEquals(1, source.requests)
            assertEquals(1, source.cancellations)
        }
    }

    @Test
    fun timeout_preserves_the_latest_rejected_sample() = runTest {
        val source = ControlledLocationSource()
        val request = async { BoundedLocationGateway(source, sampleClock).requestFix(5.seconds) }
        runCurrent()
        source.reply(sampleFix(accuracy = 80.0))
        source.reply(sampleFix(accuracy = 60.0))
        runCurrent()
        assertFalse(request.isCompleted)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(60.0, assertIs<LocationResult.Fix>(request.await()).horizontalAccuracyMetres)
        assertEquals(1, source.cancellations)
    }

    @Test
    fun accuracy_and_freshness_boundaries_are_inclusive() = runTest {
        val source = ControlledLocationSource()
        val request = async { BoundedLocationGateway(source, sampleClock).requestFix(30.seconds) }
        runCurrent()
        val boundary = sampleFix(accuracy = 50.0, ageSeconds = 30)
        source.reply(boundary)
        runCurrent()
        assertEquals(boundary, request.await())
        assertEquals(1, source.cancellations)
    }

    @Test
    fun receipt_to_selection_delay_counts_towards_freshness() = runTest {
        val source = ControlledLocationSource()
        val now = sampleFix().receivedAt
        val clock = object : Clock {
            override fun now() = now + 1.seconds
        }
        val request = async { BoundedLocationGateway(source, clock).requestFix(30.seconds) }
        runCurrent()
        source.reply(sampleFix(ageSeconds = 30))
        runCurrent()
        assertFalse(request.isCompleted)
        source.reply(sampleFix().copy(receivedAt = clock.now(), capturedAt = clock.now()))
        runCurrent()
        assertIs<LocationResult.Fix>(request.await())
    }

    @Test
    fun stale_only_session_returns_rejected_sample_at_timeout() = runTest {
        val source = ControlledLocationSource()
        val request = async { BoundedLocationGateway(source, sampleClock).requestFix(1.seconds) }
        runCurrent()
        val stale = sampleFix().copy(ageAtReceipt = null)
        source.reply(stale)
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(stale, request.await())
    }

    @Test
    fun cancellation_after_rejected_sample_does_not_return_a_fallback() = runTest {
        val source = ControlledLocationSource()
        val request = async { BoundedLocationGateway(source, sampleClock).requestFix(30.seconds) }
        runCurrent()
        source.reply(sampleFix(accuracy = 80.0))
        runCurrent()
        request.cancelAndJoin()
        source.reply(sampleFix())
        assertEquals(true, request.isCancelled)
        assertEquals(1, source.cancellations)
    }

    @Test
    fun accurate_outside_area_fix_ends_sampling_before_a_later_inside_fix() = runTest {
        val source = ControlledLocationSource()
        val request = async { BoundedLocationGateway(source, sampleClock).requestFix(30.seconds) }
        runCurrent()
        source.reply(sampleFix(latitude = 1.0, longitude = 1.0))
        source.reply(sampleFix(latitude = 0.0, longitude = 0.0))
        runCurrent()
        val verdict = classifyFix(
            fixturePlan(), assertIs<LocationResult.Fix>(request.await()),
            CheckMode.USER_INITIATED, sampleClock.now()
        )
        assertEquals(CheckRejection.OUTSIDE_AREA, assertIs<FixVerdict.Rejected>(verdict).reason)
        assertEquals(1, source.requests)
        assertEquals(1, source.cancellations)
    }

    @Test
    fun nonpositive_budget_never_starts_native_updates() = runTest {
        val source = ControlledLocationSource()
        val gateway = BoundedLocationGateway(source, sampleClock)
        for (budget in listOf(0.seconds, (-1).seconds)) {
            assertEquals(
                LocationFailure.TIMEOUT,
                assertIs<LocationResult.Failure>(gateway.requestFix(budget)).reason
            )
        }
        assertEquals(0, source.requests)
    }

    @Test
    fun terminal_failure_overrides_rejected_samples() = runTest {
        val source = ControlledLocationSource()
        val request = async { BoundedLocationGateway(source, sampleClock).requestFix(30.seconds) }
        runCurrent()
        source.reply(sampleFix(accuracy = 80.0))
        source.reply(LocationResult.Failure(LocationFailure.PERMISSION_DENIED))
        runCurrent()
        assertEquals(
            LocationFailure.PERMISSION_DENIED,
            assertIs<LocationResult.Failure>(request.await()).reason
        )
        assertEquals(1, source.cancellations)
    }
}
