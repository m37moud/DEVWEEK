package dev.daymark.logging

import java.util.logging.Level
import java.util.logging.Logger

actual object AppLogger {
    private val logger = Logger.getLogger("Daymark")

    actual fun d(tag: String, message: String) {
        logger.log(Level.FINE, "[$tag] $message")
    }

    actual fun i(tag: String, message: String) {
        logger.log(Level.INFO, "[$tag] $message")
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        logger.log(Level.SEVERE, "[$tag] ${errorMessage(message, throwable)}")
    }
}
