@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.daymark.location

import kotlinx.coroutines.Job
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLErrorDomain
import platform.CoreLocation.kCLErrorLocationUnknown
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.dateWithTimeIntervalSince1970
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock

class CoreFixDelegateTest {
    @Test
    fun native_batches_and_later_callbacks_emit_in_capture_order_until_cancelled() {
        val samples = mutableListOf<LocationResult>()
        val lifetime = Job()
        val delegate = CoreFixDelegate(Clock.System, lifetime, samples::add) {
            LocationCapabilities(true, false, true, true)
        }
        val manager = CLLocationManager()
        val inaccurate = nativeFix(80.0, 1.0)
        val accurate = nativeFix(10.0, 2.0)
        delegate.locationManager(manager, listOf(accurate, inaccurate))
        delegate.locationManager(manager, listOf(nativeFix(5.0, 3.0)))
        assertEquals(
            listOf(80.0, 10.0, 5.0),
            samples.map { assertIs<LocationResult.Fix>(it).horizontalAccuracyMetres })
        assertTrue(lifetime.isActive)
        lifetime.cancel()
        delegate.locationManager(manager, listOf(accurate))
        assertEquals(3, samples.size)
        delegate.close()
    }

    @Test
    fun transient_location_unknown_does_not_end_the_session() {
        val samples = mutableListOf<LocationResult>()
        val lifetime = Job()
        val delegate = CoreFixDelegate(Clock.System, lifetime, samples::add) {
            LocationCapabilities(true, false, true, true)
        }
        delegate.locationManager(
            CLLocationManager(),
            NSError(kCLErrorDomain, kCLErrorLocationUnknown, null)
        )
        assertTrue(lifetime.isActive)
        assertTrue(samples.isEmpty())
        delegate.locationManager(CLLocationManager(), listOf(nativeFix(10.0, 2.0)))
        assertEquals(1, samples.size)
        lifetime.cancel()
        delegate.close()
    }

    @Test
    fun permission_revocation_ends_the_session_even_for_location_unknown() {
        val samples = mutableListOf<LocationResult>()
        val lifetime = Job()
        val delegate = CoreFixDelegate(Clock.System, lifetime, samples::add) {
            LocationCapabilities(false, false, false, true)
        }
        delegate.locationManager(
            CLLocationManager(),
            NSError(kCLErrorDomain, kCLErrorLocationUnknown, null)
        )
        assertFalse(lifetime.isActive)
        assertEquals(
            LocationFailure.PERMISSION_DENIED,
            assertIs<LocationResult.Failure>(samples.single()).reason
        )
        delegate.close()
    }

    private fun nativeFix(accuracy: Double, seconds: Double) = CLLocation(
        coordinate = CLLocationCoordinate2DMake(0.0, 0.0),
        altitude = 0.0,
        horizontalAccuracy = accuracy,
        verticalAccuracy = 0.0,
        timestamp = NSDate.dateWithTimeIntervalSince1970(seconds)
    )
}
