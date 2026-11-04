package dev.daymark.logging

/** Use developer-authored tags/messages only; never include credentials, positions, or notes. */
expect object AppLogger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)

    /** Includes only the exception type: messages, causes, and stack traces may contain private data. */
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

internal fun errorMessage(message: String, throwable: Throwable?): String =
    if (throwable == null) message else "$message (${throwable::class.simpleName ?: "Throwable"})"
