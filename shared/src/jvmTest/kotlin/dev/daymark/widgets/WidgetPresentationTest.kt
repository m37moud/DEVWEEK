package dev.daymark.widgets

import kotlin.test.*
import kotlin.time.Instant

class WidgetPresentationTest {
    private val snapshot = WidgetSnapshot(
        trackerId = "f8428c3a-1347-40dc-9667-af38e69c6801",
        generatedAtEpochMillis = 0, attendanceDate = "2026-10-27",
        effectiveLocale = "en", displayStatus = "Scheduled", statusCode = "SCHEDULED",
        placeLabel = "", deadlineDisplay = "09:05",
        deadlineEpochMillis = Instant.parse("2026-10-27T09:05:00Z").toEpochMilliseconds(),
        zoneId = "UTC", quickCheckRoute = "", arrivalEpochMillis =
            Instant.parse("2026-10-27T09:00:00Z").toEpochMilliseconds()
    )

    @Test
    fun arrivalAndDeadlineBoundaries() {
        assertEquals(
            WidgetAction.DISABLED,
            snapshot.widgetAction(Instant.parse("2026-10-27T08:59:59Z"))
        )
        assertEquals(
            WidgetAction.CHECK_IN,
            snapshot.widgetAction(Instant.parse("2026-10-27T09:00:00Z"))
        )
        assertEquals(
            "AWAITING_CHECK",
            snapshot.currentStatus(Instant.parse("2026-10-27T09:05:00Z"))
        )
        assertEquals("ABSENT", snapshot.currentStatus(Instant.parse("2026-10-27T09:05:00.001Z")))
    }

    @Test
    fun staleUntrackedAndCompletedDaysNeverOfferChecks() {
        val now = Instant.parse("2026-10-27T10:00:00Z")
        assertNull(snapshot.currentStatus(Instant.parse("2026-10-28T10:00:00Z")))
        assertEquals(
            WidgetAction.OPEN_APP,
            snapshot.widgetAction(Instant.parse("2026-10-28T10:00:00Z"))
        )
        assertEquals(WidgetAction.OPEN_APP, snapshot.copy(isTracked = false).widgetAction(now))
        for (status in listOf("PRESENT", "LATE", "DAY_OFF", "HOLIDAY", "VACATION")) {
            assertEquals(
                WidgetAction.OPEN_APP,
                snapshot.copy(statusCode = status).widgetAction(now)
            )
        }
        assertEquals(WidgetAction.CHECK_IN, snapshot.copy(statusCode = "ABSENT").widgetAction(now))
    }
}
