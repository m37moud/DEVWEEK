package dev.daymark.shared

import dev.daymark.location.LocationFailure
import dev.daymark.location.LocationResult
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

internal fun macLocationReply(reply: String, receivedAt: Instant): LocationResult {
    val fields = reply.trim().split('\t')
    if (fields.size == 2 && fields[0] == "ERROR") {
        val reason = LocationFailure.entries.firstOrNull { it.name == fields[1] }
        return LocationResult.Failure(reason ?: LocationFailure.UNAVAILABLE)
    }
    return nativeFix(fields, receivedAt) ?: LocationResult.Failure(LocationFailure.UNAVAILABLE)
}

private fun nativeFix(fields: List<String>, receivedAt: Instant): LocationResult.Fix? {
    if (fields.size != 7 || fields[0] != "FIX") return null
    val latitude = fields[1].toDoubleOrNull() ?: return null
    val longitude = fields[2].toDoubleOrNull() ?: return null
    val accuracy = fields[3].toDoubleOrNull() ?: return null
    val capturedMillis = fields[4].toLongOrNull() ?: return null
    val nativeReceiptMillis = fields[5].toLongOrNull() ?: return null
    val ageMillis = fields[6].toDoubleOrNull() ?: return null
    if (!latitude.isFinite() || latitude !in -90.0..90.0 ||
        !longitude.isFinite() || longitude !in -180.0..180.0 ||
        !accuracy.isFinite() || accuracy < 0 || !ageMillis.isFinite() || ageMillis < 0 ||
        capturedMillis > nativeReceiptMillis || nativeReceiptMillis > receivedAt.toEpochMilliseconds()
    ) return null
    val nativeReceivedAt = Instant.fromEpochMilliseconds(nativeReceiptMillis)
    val capturedAt = Instant.fromEpochMilliseconds(capturedMillis)
    val nativeAge = maxOf(ageMillis.milliseconds, nativeReceivedAt - capturedAt)
    val totalAge = nativeAge + (receivedAt - nativeReceivedAt)
    if (!totalAge.isFinite()) return null
    return LocationResult.Fix(
        latitude, longitude, accuracy, capturedAt, receivedAt, totalAge
    )
}
