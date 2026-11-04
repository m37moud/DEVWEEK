package dev.daymark.logging

import android.util.Log

actual object AppLogger {
    actual fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    actual fun i(tag: String, message: String) {
        Log.i(tag, message)
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, errorMessage(message, throwable))
    }
}
