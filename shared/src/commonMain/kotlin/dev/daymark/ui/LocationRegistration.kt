package dev.daymark.ui

import dev.daymark.location.LocationResult
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal fun registrationIssue(fix: LocationResult.Fix, now: Instant): SetupIssue? {
    val age = fix.ageAtReceipt
    return when {
        !fix.latitude.isFinite() || fix.latitude !in -90.0..90.0 || !fix.longitude.isFinite() || fix.longitude !in -180.0..180.0 ||
                !fix.horizontalAccuracyMetres.isFinite() || fix.horizontalAccuracyMetres < 0 || fix.capturedAt > fix.receivedAt || fix.receivedAt > now -> SetupIssue.LOCATION_UNAVAILABLE

        age == null || !age.isFinite() || age.isNegative() || age + (now - fix.receivedAt) > 30.seconds -> SetupIssue.LOCATION_STALE
        fix.horizontalAccuracyMetres > 50 -> SetupIssue.LOCATION_INACCURATE
        else -> null
    }
}
