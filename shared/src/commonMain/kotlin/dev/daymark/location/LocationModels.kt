package dev.daymark.location

import kotlin.time.Duration
import kotlin.time.Instant

sealed interface LocationResult {
    data class Fix(
        val latitude: Double,
        val longitude: Double,
        val horizontalAccuracyMetres: Double,
        val capturedAt: Instant,
        val receivedAt: Instant,
        val ageAtReceipt: Duration?,
    ) : LocationResult {
        override fun toString(): String = "LocationResult.Fix(coordinates=redacted, accuracy=$horizontalAccuracyMetres, age=$ageAtReceipt)"
    }
    data class Failure(val reason: LocationFailure) : LocationResult
}

enum class LocationFailure { PERMISSION_DENIED, SERVICES_DISABLED, TIMEOUT, UNAVAILABLE, CANCELLED }

data class LocationCapabilities(
    val foregroundGranted: Boolean,
    val backgroundGranted: Boolean,
    val preciseGranted: Boolean,
    val servicesEnabled: Boolean,
)
