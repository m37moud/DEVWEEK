package dev.daymark.logging

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import platform.Foundation.NSLog

@OptIn(ExperimentalForeignApi::class)
actual object AppLogger {
    actual fun d(tag: String, message: String) {
        write("DEBUG: [$tag] $message")
    }

    actual fun i(tag: String, message: String) {
        write("INFO: [$tag] $message")
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        write("ERROR: [$tag] ${errorMessage(message, throwable)}")
    }

    private fun write(line: String) = memScoped {
        // C varargs require an explicit UTF-8 pointer, not an Objective-C object specifier.
        NSLog("%s", line.cstr.ptr)
    }
}
