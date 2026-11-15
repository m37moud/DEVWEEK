package dev.daymark.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.os.Looper
import dev.daymark.logging.AppLogger
import java.util.concurrent.atomic.AtomicBoolean
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult as NativeLocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task
import java.util.concurrent.Executor
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

class AndroidLocationGateway internal constructor(source: LocationUpdateSource, clock: Clock) :
    LocationGateway {
    constructor(
        context: Context,
        clock: Clock = Clock.System
    ) : this(fusedLocationSource(context.applicationContext, clock), clock)

    private val bounded = BoundedLocationGateway(source, clock)
    override suspend fun requestFix(budget: Duration): LocationResult = bounded.requestFix(budget)
}

fun androidLocationCapabilities(context: Context): LocationCapabilities {
    fun granted(permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
    val foreground = fine || granted(Manifest.permission.ACCESS_COARSE_LOCATION)
    val background =
        foreground && (Build.VERSION.SDK_INT < 29 || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    val services = try {
        if (Build.VERSION.SDK_INT >= 28) manager?.isLocationEnabled == true
        else manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                manager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    } catch (_: SecurityException) {
        false
    }
    return LocationCapabilities(foreground, background, fine, services)
}

internal fun androidLocationFix(
    latitude: Double, longitude: Double, accuracy: Double,
    capturedAtEpochMillis: Long, sampleElapsedNanos: Long, receiptElapsedNanos: Long,
    receivedAt: Instant,
): LocationResult.Fix {
    val age = if (sampleElapsedNanos > 0 && receiptElapsedNanos >= sampleElapsedNanos)
        (receiptElapsedNanos - sampleElapsedNanos).nanoseconds else null
    return LocationResult.Fix(
        latitude, longitude, accuracy,
        Instant.fromEpochMilliseconds(capturedAtEpochMillis), receivedAt, age
    )
}

private fun fusedLocationSource(context: Context, clock: Clock): FusedLocationSource {
    val client = LocationServices.getFusedLocationProviderClient(context)
    return FusedLocationSource(
        clock,
        { androidLocationCapabilities(context) },
        { request, callback ->
            client.requestLocationUpdates(
                request,
                callback,
                Looper.getMainLooper()
            )
        },
        { callback -> client.removeLocationUpdates(callback) }
    )
}

internal class FusedLocationSource(
    private val clock: Clock,
    private val capabilities: () -> LocationCapabilities,
    private val subscribe: (LocationRequest, LocationCallback) -> Task<Void>,
    private val unsubscribe: (LocationCallback) -> Task<Void>,
) : LocationUpdateSource {
    override fun request(
        budget: Duration,
        callback: (LocationResult) -> Unit
    ): LocationCancellation {
        locationGateFailure(capabilities())?.let {
            callback(LocationResult.Failure(it))
            return LocationCancellation { }
        }
        val session = FusedLocationSession(clock, capabilities, callback, unsubscribe)
        try {
            subscribe(locationUpdateRequest(budget), session)
                .addOnCompleteListener(DIRECT_EXECUTOR, session::registered)
        } catch (_: SecurityException) {
            callback(LocationResult.Failure(LocationFailure.PERMISSION_DENIED))
        } catch (failure: RuntimeException) {
            AppLogger.e("FusedLocationSource", "Location registration failed", failure)
            callback(LocationResult.Failure(LocationFailure.UNAVAILABLE))
        }
        return session
    }
}

private fun locationUpdateRequest(budget: Duration): LocationRequest =
    LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
        .setMinUpdateIntervalMillis(1_000L)
        .setWaitForAccurateLocation(true)
        .setMaxUpdateAgeMillis(0)
        .setMaxUpdateDelayMillis(0)
        .setDurationMillis(budget.inWholeMilliseconds.coerceAtLeast(1))
        .build()

private class FusedLocationSession(
    private val clock: Clock,
    private val capabilities: () -> LocationCapabilities,
    private val callback: (LocationResult) -> Unit,
    private val unsubscribe: (LocationCallback) -> Task<Void>,
) : LocationCallback(), LocationCancellation {
    private val closed = AtomicBoolean(false)

    override fun onLocationResult(locations: NativeLocationResult) {
        if (closed.get()) return
        locationGateFailure(capabilities())?.let {
            callback(LocationResult.Failure(it))
            return
        }
        for (location in locations.locations.sortedBy { it.elapsedRealtimeNanos }) {
            if (closed.get()) return
            callback(androidLocationFix(location, clock))
        }
    }

    fun registered(registration: Task<Void>) {
        // Cancellation may precede the asynchronous registration acknowledgement.
        if (closed.get()) removeUpdates()
        else if (!registration.isSuccessful) callback(
            LocationResult.Failure(
                if (registration.exception is SecurityException) LocationFailure.PERMISSION_DENIED
                else LocationFailure.UNAVAILABLE
            )
        )
    }

    override fun cancel() {
        if (closed.compareAndSet(false, true)) removeUpdates()
    }

    private fun removeUpdates() {
        try {
            unsubscribe(this).addOnFailureListener(DIRECT_EXECUTOR) { failure ->
                AppLogger.e("FusedLocationSource", "Location update removal failed", failure)
            }
        } catch (failure: SecurityException) {
            AppLogger.e("FusedLocationSource", "Location update removal denied", failure)
        }
    }
}

private fun androidLocationFix(location: Location, clock: Clock) = androidLocationFix(
    location.latitude, location.longitude,
    if (location.hasAccuracy()) location.accuracy.toDouble() else Double.NaN,
    location.time, location.elapsedRealtimeNanos, SystemClock.elapsedRealtimeNanos(), clock.now()
)

private val DIRECT_EXECUTOR = Executor { it.run() }
