package dev.daymark.logging

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSLog

@OptIn(ExperimentalForeignApi::class)
actual object AppLogger {
    actual fun d(tag: String, message: String) {
        NSLog("%@", "DEBUG: [$tag] $message")
    }

    actual fun i(tag: String, message: String) {
        NSLog("%@", "INFO: [$tag] $message")
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        NSLog("%@", "ERROR: [$tag] ${errorMessage(message, throwable)}")
    }
}
