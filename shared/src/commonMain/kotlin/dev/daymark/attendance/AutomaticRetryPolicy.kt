package dev.daymark.attendance

import dev.daymark.models.attendance.CheckRejection
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal const val DEFAULT_AUTOMATIC_ATTEMPT_LIMIT = 1
internal val automaticAttemptLimits = 1..3
internal val automaticSessionLimit = 30.seconds
internal val automaticRetryDelay = 60.seconds

internal fun automaticRetryAt(
    reason: CheckRejection,
    finishedAt: Instant,
    deadline: Instant
): Instant? {
    val retryAt = finishedAt + automaticRetryDelay
    return retryAt.takeIf {
        deadline - retryAt >= automaticSessionLimit &&
                reason in setOf(
            CheckRejection.LOCATION_TIMEOUT,
            CheckRejection.UNAVAILABLE,
            CheckRejection.INACCURATE_FIX,
            CheckRejection.STALE_FIX
        )
    }
}
