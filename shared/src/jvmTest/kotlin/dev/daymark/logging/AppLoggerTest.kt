package dev.daymark.logging

import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppLoggerTest {
    @Test
    fun levels_and_literal_text_are_preserved_without_private_exception_details() {
        val records = mutableListOf<LogRecord>()
        val handler = object : Handler() {
            override fun publish(record: LogRecord) {
                records.add(record)
            }

            override fun flush() {}
            override fun close() {}
        }
        val logger = Logger.getLogger("Daymark")
        val previousLevel = logger.level
        val previousParentHandlers = logger.useParentHandlers
        logger.level = Level.ALL
        logger.useParentHandlers = false
        logger.addHandler(handler)
        try {
            val failure = IllegalStateException("private-note", RuntimeException("private-token"))
            failure.addSuppressed(RuntimeException("private-position"))
            AppLogger.d("Test", "100% %@ العربية")
            AppLogger.i("Test", "Report acknowledged")
            AppLogger.e("Test", "Report failed")
            AppLogger.e("Test", "Report failed", failure)

            assertEquals(listOf(Level.FINE, Level.INFO, Level.SEVERE, Level.SEVERE), records.map { it.level })
            assertEquals(
                listOf(
                    "[Test] 100% %@ العربية",
                    "[Test] Report acknowledged",
                    "[Test] Report failed",
                    "[Test] Report failed (IllegalStateException)"
                ),
                records.map { it.message }
            )
            records.forEach { assertNull(it.thrown) }
        } finally {
            logger.removeHandler(handler)
            logger.level = previousLevel
            logger.useParentHandlers = previousParentHandlers
        }
    }
}
