package dev.daymark.widgets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class QuickCheckRouteTest {
    private val tracker = "f8428c3a-1347-40dc-9667-af38e69c6801"
    @Test
    fun rejects_untrusted_route_shape() {
        val route = QuickCheckRoute(trackerId = tracker)
        assertEquals(route, parseQuickCheckRoute(encodeQuickCheckRoute(route)))
        for (raw in listOf(
            "",
            "https://quick-check?v=1&trackerId=$tracker",
            "daymark://quick-check?v=2&trackerId=$tracker",
            "daymark://quick-check?v=1&trackerId=bad",
            "daymark://quick-check?v=1",
            "daymark://quick-check?v=1&trackerId=$tracker&date=2020-01-01",
            "daymark://quick-check?v=1&trackerId=$tracker#fake",
            "daymark://quick-check/else?v=1&trackerId=$tracker"
        )) assertNull(parseQuickCheckRoute(raw), raw)
    }

    @Test
    fun unknown_and_invalid_snapshot_payloads_are_not_displayed_as_confirmation() {
        assertNull(decodeWidgetSnapshot("{}"))
        assertNull(decodeWidgetSnapshot("not-json"))
        val snapshot = WidgetSnapshot(
            trackerId = tracker,
            generatedAtEpochMillis = 1_798_480_920_000L,
            attendanceDate = "2026-10-27",
            effectiveLocale = "ar",
            displayStatus = "حاضر",
            statusCode = "PRESENT",
            placeLabel = "المكتب / Office",
            deadlineDisplay = "٠٩:٠٥",
            deadlineEpochMillis = 1_798_484_700_000L,
            zoneId = "UTC",
            quickCheckRoute = encodeQuickCheckRoute(QuickCheckRoute(trackerId = tracker))
        )
        val json = encodeWidgetSnapshot(snapshot)
        assertEquals(snapshot, decodeWidgetSnapshot(json))
        assertNull(decodeWidgetSnapshot(json.replace("\"schemaVersion\":1,", "")))
        assertNull(decodeWidgetSnapshot(json.replace("\"schemaVersion\":1", "\"schemaVersion\":2")))
        assertNull(decodeWidgetSnapshot(json.replace("2026-10-27", "yesterday")))
        assertNull(
            decodeWidgetSnapshot(
                json.replace(
                    "\"statusCode\":\"PRESENT\"",
                    "\"statusCode\":\"CONFIRMED_BY_WIDGET\""
                )
            )
        )
        for (secret in listOf(
            "accessToken",
            "refreshToken",
            "latitude",
            "longitude",
            "databasePath",
            "rawFix"
        )) assertFalse(json.contains(secret))
    }
}
