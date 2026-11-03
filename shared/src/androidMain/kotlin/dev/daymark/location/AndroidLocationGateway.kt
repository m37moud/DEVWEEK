package dev.daymark.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

class AndroidLocationGateway internal constructor(source: OneShotLocationSource) : LocationGateway {
    constructor(
        context: Context,
        clock: Clock = Clock.System
    ) : this(FusedLocationSource(context.applicationContext, clock))

    private val bounded = BoundedLocationGateway(source)
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

private class FusedLocationSource(private val context: Context, private val clock: Clock) :
    OneShotLocationSource {
    override fun request(callback: (LocationResult) -> Unit): LocationCancellation {
        locationGateFailure(androidLocationCapabilities(context))?.let {
            callback(LocationResult.Failure(it))
            return LocationCancellation { }
        }
        val token = CancellationTokenSource()
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(0)
            .setDurationMillis(30_000)
            .build()
        try {
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(request, token.token)
                .addOnCompleteListener { task ->
                    val capabilityFailure =
                        locationGateFailure(androidLocationCapabilities(context))
                    when {
                        capabilityFailure != null -> callback(
                            LocationResult.Failure(
                                capabilityFailure
                            )
                        )

                        task.isCanceled -> callback(LocationResult.Failure(LocationFailure.CANCELLED))
                        !task.isSuccessful -> callback(
                            LocationResult.Failure(
                                if (task.exception is SecurityException) LocationFailure.PERMISSION_DENIED else LocationFailure.UNAVAILABLE
                            )
                        )

                        else -> {
                            val location = task.result
                            if (location == null) callback(LocationResult.Failure(LocationFailure.TIMEOUT))
                            else callback(
                                androidLocationFix(
                                    location.latitude,
                                    location.longitude,
                                    if (location.hasAccuracy()) location.accuracy.toDouble() else Double.NaN,
                                    location.time,
                                    location.elapsedRealtimeNanos,
                                    SystemClock.elapsedRealtimeNanos(),
                                    clock.now()
                                )
                            )
                        }
                    }
                }
        } catch (_: SecurityException) {
            callback(LocationResult.Failure(LocationFailure.PERMISSION_DENIED))
        } catch (_: RuntimeException) {
            callback(LocationResult.Failure(LocationFailure.UNAVAILABLE))
        }
        return LocationCancellation { token.cancel() }
    }
}
