package dev.daymark.app

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class QuickCheckIntentsTest {
    @Test fun a_native_open_is_queued_for_the_foreground_host_without_recording_anything() = runTest {
        QuickCheckIntents.request()
        assertEquals(Unit, QuickCheckIntents.events.first())
    }
}
