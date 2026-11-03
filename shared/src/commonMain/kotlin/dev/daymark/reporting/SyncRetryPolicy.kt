package dev.daymark.reporting

import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SyncRetryPolicy(
    private val jitter: () -> Duration = {
        Random.nextLong(
            0,
            1001
        ).milliseconds
    }
) {
    fun nextDelay(attempt: Int, retryAfter: Duration?): Duration? {
        require(attempt >= 0)
        if (attempt >= 6) return null
        val base = minOf(60, 2 shl attempt).seconds + jitter().coerceIn(Duration.ZERO, 1.seconds)
        return maxOf(base, retryAfter?.coerceAtLeast(Duration.ZERO) ?: Duration.ZERO)
    }
}
