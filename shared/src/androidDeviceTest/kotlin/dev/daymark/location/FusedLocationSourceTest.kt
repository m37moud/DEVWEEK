package dev.daymark.location

import android.location.Location
import android.os.SystemClock
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class FusedLocationSourceTest {
    @Test
    fun native_callback_delivers_multiple_ordered_samples_and_stops_after_cancel() {
        lateinit var listener: LocationCallback
        lateinit var request: LocationRequest
        var removals = 0
        val samples = mutableListOf<LocationResult>()
        val source = FusedLocationSource(
            Clock.System, { LocationCapabilities(true, true, true, true) },
            { configuration, callback ->
                request = configuration
                listener = callback
                Tasks.forResult<Void>(null)
            },
            { removals++; Tasks.forResult<Void>(null) }
        )
        val session = source.request(5.seconds, samples::add)
        val elapsed = SystemClock.elapsedRealtimeNanos()
        fun sample(accuracy: Float, elapsedNanos: Long) = Location("test").apply {
            latitude = 0.0
            longitude = 0.0
            this.accuracy = accuracy
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = elapsedNanos
        }

        val inaccurate = sample(80f, elapsed - 1_000_000)
        val accurate = sample(10f, elapsed)
        val nativeBatch =
            com.google.android.gms.location.LocationResult.create(listOf(accurate, inaccurate))
        listener.onLocationResult(nativeBatch)
        assertEquals(
            listOf(80.0, 10.0),
            samples.map { (it as LocationResult.Fix).horizontalAccuracyMetres })
        assertEquals(5_000L, request.durationMillis)
        assertEquals(0L, request.maxUpdateAgeMillis)
        assertTrue(request.maxUpdateDelayMillis < request.intervalMillis * 2)
        assertEquals(1_000L, request.intervalMillis)
        session.cancel()
        session.cancel()
        listener.onLocationResult(nativeBatch)
        assertEquals(2, samples.size)
        assertEquals(1, removals)
    }

    @Test
    fun cancellation_before_registration_acknowledgement_removes_late_registration() {
        val registration = TaskCompletionSource<Void>()
        var removals = 0
        val source = FusedLocationSource(
            Clock.System, { LocationCapabilities(true, true, true, true) },
            { _, _ -> registration.task },
            { removals++; Tasks.forResult<Void>(null) }
        )
        val samples = mutableListOf<LocationResult>()
        val session = source.request(5.seconds, samples::add)
        session.cancel()
        assertEquals(1, removals)
        registration.setResult(null)
        assertEquals(2, removals)
        assertTrue(samples.isEmpty())
    }

    @Test
    fun registration_denial_is_reported_and_subscription_is_removed() {
        var removals = 0
        val source = FusedLocationSource(
            Clock.System, { LocationCapabilities(true, true, true, true) },
            { _, _ -> Tasks.forException(SecurityException()) },
            { removals++; Tasks.forResult<Void>(null) }
        )
        val samples = mutableListOf<LocationResult>()
        val session = source.request(5.seconds, samples::add)
        assertEquals(
            LocationFailure.PERMISSION_DENIED,
            (samples.single() as LocationResult.Failure).reason
        )
        session.cancel()
        assertEquals(1, removals)
    }
}
