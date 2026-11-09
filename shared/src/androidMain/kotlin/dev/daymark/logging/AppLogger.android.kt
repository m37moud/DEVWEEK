package dev.daymark.logging

import android.util.Log

actual object AppLogger {
    actual fun d(tag: String, message: String) {
        runCatching { Log.d(tag, message) }
    }

    actual fun i(tag: String, message: String) {
        runCatching { Log.i(tag, message) }
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        runCatching { Log.e(tag, errorMessage(message, throwable)) }
    }
}
