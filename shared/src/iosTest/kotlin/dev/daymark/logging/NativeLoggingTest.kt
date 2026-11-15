package dev.daymark.logging

import kotlin.test.Test

class NativeLoggingTest {
    @Test
    fun native_log_levels_accept_unicode_and_literal_format_markers_without_crashing() {
        AppLogger.d("NativeLoggingTest", "50% مرحبا %@ %s")
        AppLogger.i("NativeLoggingTest", "Expected outcome")
        AppLogger.e("NativeLoggingTest", "Expected test failure", IllegalStateException())
    }
}
