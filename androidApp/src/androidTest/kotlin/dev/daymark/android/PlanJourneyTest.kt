package dev.daymark.android

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.daymark.app.App
import dev.daymark.settings.LanguagePreference
import dev.daymark.plan.TimeOffService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PlanJourneyTest {
    @get:Rule val compose = createComposeRule()
    @Test fun vacation_preview_changes_saved_attendance_and_balance_then_can_be_removed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "plan-journey-${UUID.randomUUID()}.db"
        val fixture = AttendanceTestApp(context,name)
        var shown by mutableStateOf(true)
        try {
            runBlocking { fixture.graph.settings.update(language = LanguagePreference.EN) }
            compose.setContent { if (shown) App(fixture.graph) }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("nav-plan").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("nav-plan").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("plan-add-time-off").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("plan-zone").performScrollTo().performClick()
            compose.onNodeWithTag("zone-search").performTextReplacement("Asia/Tokyo")
            compose.onNodeWithTag("zone-choice-Asia/Tokyo").performClick()
            compose.onNodeWithTag("plan-review").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("preview-cancel").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("preview-cancel").performClick()
            Assert.assertEquals("UTC",runBlocking { fixture.graph.repository.loadCurrentDay(fixture.clock.now())!!.plan.zoneId })
            compose.onNodeWithTag("plan-add-time-off").performScrollTo().performClick()
            compose.onNodeWithTag("time-off-preview").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("preview-confirm").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("preview-confirm").performClick()
            compose.waitUntil(10_000) { runBlocking { TimeOffService(fixture.db,fixture.clock).observeEntries().first().isNotEmpty() } }
            Assert.assertEquals(9,runBlocking { TimeOffService(fixture.db,fixture.clock).observeAvailableBalance().first() })
            compose.onNodeWithTag("nav-history").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("history-record-2026-10-27").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("history-record-2026-10-27").performClick()
            compose.onNodeWithTag("history-time-off-action").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("time-off-remove").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("time-off-remove").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("preview-confirm").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("preview-confirm").performClick()
            compose.waitUntil(10_000) { runBlocking { TimeOffService(fixture.db,fixture.clock).observeEntries().first().isEmpty() } }
            Assert.assertEquals(10,runBlocking { TimeOffService(fixture.db,fixture.clock).observeAvailableBalance().first() })
        } finally {
            compose.runOnIdle { shown = false }; compose.waitForIdle()
            fixture.db.close(); context.deleteDatabase(name)
        }
    }
}
