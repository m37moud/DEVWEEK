package dev.daymark.location

import dev.daymark.logging.AppLogger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Owns one bounded subscription, including cancellation and late callbacks. */
internal class BoundedLocationGateway(
    private val source: LocationUpdateSource,
    private val clock: Clock,
) : LocationGateway {
    override suspend fun requestFix(budget: Duration): LocationResult {
        val limit = budget.coerceAtMost(30.seconds)
        var rejectedSample: LocationResult.Fix? = null
        AppLogger.d(TAG, "Location session started")
        try {
            val selected = withTimeoutOrNull(limit) {
                acquire(limit) { rejectedSample = it }
            } ?: rejectedSample ?: LocationResult.Failure(LocationFailure.TIMEOUT)
            val reason = if (selected is LocationResult.Failure) selected.reason.name
            else if (rejectedSample === selected) "QUALITY_NOT_MET" else "FIX_ACQUIRED"
            AppLogger.i(TAG, "Location session ended: $reason")
            return selected
        } finally {
            AppLogger.d(TAG, "Location session closed")
        }
    }

    private suspend fun acquire(
        budget: Duration,
        rejected: (LocationResult.Fix) -> Unit,
    ): LocationResult {
        val updates = Channel<LocationResult>(Channel.UNLIMITED)
        var cancellation: LocationCancellation? = null
        try {
            cancellation = source.request(budget) { updates.trySend(it) }
            return selectSample(updates, rejected)
        } finally {
            updates.cancel()
            cancellation?.cancel()
        }
    }

    private suspend fun selectSample(
        updates: Channel<LocationResult>,
        rejected: (LocationResult.Fix) -> Unit,
    ): LocationResult {
        while (true) {
            val sample = sanitized(updates.receive())
            if (sample is LocationResult.Fix && !acceptable(sample)) rejected(sample)
            else return sample
        }
    }

    private fun acceptable(fix: LocationResult.Fix): Boolean {
        val age = fix.ageAtReceipt ?: return false
        return fix.horizontalAccuracyMetres <= 50.0 && age + (clock.now() - fix.receivedAt) <= 30.seconds
    }

    private fun sanitized(sample: LocationResult): LocationResult {
        if (sample !is LocationResult.Fix) return sample
        val valid = sample.latitude.isFinite() && sample.latitude in -90.0..90.0 &&
                sample.longitude.isFinite() && sample.longitude in -180.0..180.0 &&
                sample.horizontalAccuracyMetres.isFinite() && sample.horizontalAccuracyMetres >= 0.0 &&
                sample.capturedAt <= sample.receivedAt && sample.receivedAt <= clock.now() &&
                (sample.ageAtReceipt == null ||
                        (sample.ageAtReceipt.isFinite() && sample.ageAtReceipt >= Duration.ZERO))
        return if (valid) sample else LocationResult.Failure(LocationFailure.UNAVAILABLE)
    }

    private companion object {
        const val TAG = "BoundedLocationGateway"
    }
}
