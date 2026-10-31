package dev.daymark.location

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal class ControlledLocationSource : OneShotLocationSource {
    var requests = 0
    var cancellations = 0
    private var callback: ((LocationResult) -> Unit)? = null
    override fun request(callback: (LocationResult) -> Unit): LocationCancellation {
        requests++
        this.callback = callback
        return LocationCancellation { cancellations++ }
    }
    fun reply(result: LocationResult) { requireNotNull(callback)(result) }
}

internal fun sampleFix(
    latitude: Double = 25.123456,
    longitude: Double = 30.654321,
    accuracy: Double = 10.0,
    capturedAt: Instant = Instant.parse("2026-10-27T09:02:00Z"),
    ageSeconds: Long = 0,
): LocationResult.Fix = LocationResult.Fix(
    latitude, longitude, accuracy, capturedAt,
    Instant.parse("2026-10-27T09:02:00Z"), ageSeconds.seconds,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal fun timeoutContract(factory: (OneShotLocationSource) -> LocationGateway) = runTest {
    val source = ControlledLocationSource()
    val request = async { factory(source).requestFix(10.minutes) }
    runCurrent()
    advanceTimeBy(29_999)
    runCurrent()
    assertFalse(request.isCompleted)
    advanceTimeBy(1)
    runCurrent()
    assertEquals(LocationFailure.TIMEOUT, assertIs<LocationResult.Failure>(request.await()).reason)
    assertEquals(1, source.requests)
    assertEquals(1, source.cancellations)
    source.reply(sampleFix()) // A late callback must be harmless, not resume a dead continuation.
    assertEquals(1, source.requests)
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal fun cancellationContract(factory: (OneShotLocationSource) -> LocationGateway) = runTest {
    val source = ControlledLocationSource()
    val request = async { factory(source).requestFix(30.seconds) }
    runCurrent()
    request.cancel(CancellationException("Caller left the screen"))
    runCurrent()
    assertTrue(request.isCancelled)
    assertEquals(1, source.requests)
    assertEquals(1, source.cancellations)
    source.reply(sampleFix())
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal fun invalidDataContract(factory: (OneShotLocationSource) -> LocationGateway) = runTest {
    val cases = listOf(
        sampleFix(latitude = Double.NaN), sampleFix(longitude = Double.POSITIVE_INFINITY),
        sampleFix(latitude = 90.1), sampleFix(longitude = -180.1),
        sampleFix(accuracy = -1.0), sampleFix(accuracy = Double.NaN),
        sampleFix(ageSeconds = -1),
        sampleFix(capturedAt = Instant.parse("2026-10-27T09:02:01Z")),
    )
    for (fix in cases) {
        val source = ControlledLocationSource()
        val request = async { factory(source).requestFix(30.seconds) }
        runCurrent()
        source.reply(fix)
        runCurrent()
        assertEquals(LocationFailure.UNAVAILABLE, assertIs<LocationResult.Failure>(request.await()).reason)
        assertEquals(1, source.cancellations)
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal fun denialContract(factory: (OneShotLocationSource) -> LocationGateway) = runTest {
    val source = ControlledLocationSource()
    val request = async { factory(source).requestFix(30.seconds) }
    runCurrent()
    source.reply(LocationResult.Failure(LocationFailure.PERMISSION_DENIED))
    runCurrent()
    assertEquals(LocationFailure.PERMISSION_DENIED, assertIs<LocationResult.Failure>(request.await()).reason)
    advanceTimeBy(60_000)
    assertEquals(1, source.requests)
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal fun successContract(factory: (OneShotLocationSource) -> LocationGateway) = runTest {
    val source = ControlledLocationSource()
    val request = async { factory(source).requestFix(30.seconds) }
    runCurrent()
    source.reply(sampleFix())
    runCurrent()
    val fix = assertIs<LocationResult.Fix>(request.await())
    assertEquals(10.0, fix.horizontalAccuracyMetres)
    assertEquals(1, source.cancellations)
    source.reply(LocationResult.Failure(LocationFailure.UNAVAILABLE))
}

class LocationPrivacyTest {
    @Test fun debug_strings_cannot_expose_sensor_coordinates() {
        val text = sampleFix().toString()
        assertFalse(text.contains("25.123456"))
        assertFalse(text.contains("30.654321"))
    }
}
