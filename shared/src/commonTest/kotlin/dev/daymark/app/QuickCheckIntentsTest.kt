package dev.daymark.app

import dev.daymark.widgets.QuickCheckRoute
import dev.daymark.widgets.encodeQuickCheckRoute
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class QuickCheckIntentsTest {
    @Test
    fun a_native_open_is_queued_for_the_foreground_host_without_recording_anything() = runTest {
        QuickCheckIntents.request()
        val event = QuickCheckIntents.events.first()
        Uuid.parse(event.token)
        assertTrue(event.allowCurrentTracker)
        assertNull(event.route)
    }

    @Test
    fun repeated_widget_opens_coalesce_and_malformed_routes_allow_navigation_only() = runTest {
        val route = QuickCheckRoute(trackerId = "f8428c3a-1347-40dc-9667-af38e69c6801")
        repeat(3) { QuickCheckIntents.requestRoute(encodeQuickCheckRoute(route)) }
        val event = QuickCheckIntents.events.first()
        assertEquals(route, event.route)
        assertFalse(event.allowCurrentTracker)
        QuickCheckIntents.requestRoute("daymark://quick-check?v=100&trackerId=bad")
        val malformed = QuickCheckIntents.events.first()
        assertNull(malformed.route)
        assertFalse(malformed.allowCurrentTracker)
    }
}
