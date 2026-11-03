package dev.daymark.location

import kotlin.time.Duration

interface LocationGateway {
    suspend fun requestFix(budget: Duration): LocationResult
}

internal fun interface LocationCancellation {
    fun cancel()
}

internal fun interface OneShotLocationSource {
    fun request(callback: (LocationResult) -> Unit): LocationCancellation
}
