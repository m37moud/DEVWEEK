package dev.daymark.location

import dev.daymark.logging.AppLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Owns exactly one native request, including on timeout, cancellation and late callbacks. */
internal class BoundedLocationGateway(private val source: OneShotLocationSource) : LocationGateway {
    private val TAG = BoundedLocationGateway::class.simpleName ?: "BoundedLocationGateway"
    override suspend fun requestFix(budget: Duration): LocationResult =
        withTimeoutOrNull(budget.coerceAtMost(30.seconds)) {
            val reply = CompletableDeferred<LocationResult>()
            val cancellation = source.request {
                AppLogger.d(tag = TAG, message = "Location result: $it")
                reply.complete(sanitized(it))
            }
            try {
                reply.await()
            } finally {
                reply.cancel()
                cancellation.cancel()
            }
        } ?: LocationResult.Failure(LocationFailure.TIMEOUT)

    private fun sanitized(result: LocationResult): LocationResult {
        AppLogger.d(tag = TAG, message = "Sanitizing location result: $result")
        if (result !is LocationResult.Fix) return result
        val valid = result.latitude.isFinite() && result.latitude in -90.0..90.0 &&
                result.longitude.isFinite() && result.longitude in -180.0..180.0 &&
                result.horizontalAccuracyMetres.isFinite() && result.horizontalAccuracyMetres >= 0.0 &&
                result.capturedAt <= result.receivedAt &&
                (result.ageAtReceipt == null ||
                        (result.ageAtReceipt.isFinite() && result.ageAtReceipt >= Duration.ZERO))
        return if (valid) result else LocationResult.Failure(LocationFailure.UNAVAILABLE)
    }
}
