package dev.daymark.location

import kotlin.time.Duration

interface LocationGateway {
    suspend fun requestFix(budget: Duration): LocationResult
}

internal fun interface LocationCancellation {
    fun cancel()
}

/** Emits sensor samples until cancellation; failures terminate the session. */
internal fun interface LocationUpdateSource {
    fun request(budget: Duration, callback: (LocationResult) -> Unit): LocationCancellation
}
