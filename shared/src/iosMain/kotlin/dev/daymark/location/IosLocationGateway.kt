@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.daymark.location

import kotlinx.cinterop.useContents
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import platform.CoreLocation.CLAccuracyAuthorization
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.CoreLocation.kCLErrorDomain
import platform.CoreLocation.kCLErrorLocationUnknown
import platform.Foundation.NSError
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

class IosLocationGateway internal constructor(source: LocationUpdateSource, clock: Clock) :
    LocationGateway {
    constructor(clock: Clock = Clock.System) : this(CoreLocationSource(clock), clock)

    private val bounded = BoundedLocationGateway(source, clock)
    override suspend fun requestFix(budget: Duration): LocationResult = bounded.requestFix(budget)
}

internal fun iosLocationFix(
    latitude: Double, longitude: Double, accuracy: Double, capturedAtEpochSeconds: Double,
    receivedAt: Instant,
): LocationResult.Fix {
    require(capturedAtEpochSeconds.isFinite())
    val seconds = floor(capturedAtEpochSeconds).toLong()
    val nanos = ((capturedAtEpochSeconds - seconds) * 1_000_000_000.0).roundToLong()
    val captured = Instant.fromEpochSeconds(seconds, nanos)
    return LocationResult.Fix(
        latitude,
        longitude,
        accuracy,
        captured,
        receivedAt,
        receivedAt - captured
    )
}

internal fun onIosLocationMain(block: () -> Unit) {
    dispatch_async(dispatch_get_main_queue()) { block() }
}

internal fun iosLocationCapabilities(manager: CLLocationManager): LocationCapabilities {
    val status = manager.authorizationStatus
    val foreground =
        status == kCLAuthorizationStatusAuthorizedWhenInUse || status == kCLAuthorizationStatusAuthorizedAlways
    return LocationCapabilities(
        foreground, false,
        foreground && manager.accuracyAuthorization == CLAccuracyAuthorization.CLAccuracyAuthorizationFullAccuracy,
        CLLocationManager.locationServicesEnabled()
    )
}

private class CoreLocationSource(private val clock: Clock) : LocationUpdateSource {
    override fun request(
        budget: Duration,
        callback: (LocationResult) -> Unit
    ): LocationCancellation {
        val lifetime = Job()
        var delegate: CoreFixDelegate? = null // Read/written only by the main queue.
        lifetime.invokeOnCompletion { onIosLocationMain { delegate?.close(); delegate = null } }
        onIosLocationMain {
            if (lifetime.isActive) {
                delegate = CoreFixDelegate(clock, lifetime, callback)
                delegate?.start()
            }
        }
        return LocationCancellation { lifetime.cancel() }
    }
}

internal class CoreFixDelegate(
    private val clock: Clock,
    private val lifetime: CompletableJob,
    private val callback: (LocationResult) -> Unit,
    private val capabilities: (CLLocationManager) -> LocationCapabilities = ::iosLocationCapabilities,
) : NSObject(), CLLocationManagerDelegateProtocol {
    private val manager = CLLocationManager()

    fun start() {
        locationGateFailure(capabilities(manager))?.let {
            finish(LocationResult.Failure(it)); return
        }
        manager.delegate = this
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.startUpdatingLocation()
    }

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        if (!lifetime.isActive) return
        val capabilityFailure = locationGateFailure(capabilities(manager))
        if (capabilityFailure != null) {
            finish(LocationResult.Failure(capabilityFailure)); return
        }
        for (location in didUpdateLocations.filterIsInstance<CLLocation>().sortedBy {
            it.timestamp.timeIntervalSince1970
        }) {
            if (!lifetime.isActive) return
            val sample = try {
                location.coordinate.useContents {
                    iosLocationFix(
                        latitude, longitude, location.horizontalAccuracy,
                        location.timestamp.timeIntervalSince1970, clock.now()
                    )
                }
            } catch (_: IllegalArgumentException) {
                finish(LocationResult.Failure(LocationFailure.UNAVAILABLE))
                return
            }
            callback(sample)
        }
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        if (!lifetime.isActive) return
        val capabilityFailure = locationGateFailure(capabilities(manager))
        if (capabilityFailure == null && didFailWithError.domain == kCLErrorDomain &&
            didFailWithError.code == kCLErrorLocationUnknown
        ) return
        finish(LocationResult.Failure(capabilityFailure ?: LocationFailure.UNAVAILABLE))
    }

    private fun finish(result: LocationResult) {
        if (lifetime.complete()) callback(result)
    }

    fun close() {
        manager.stopUpdatingLocation(); manager.delegate = null
    }
}
