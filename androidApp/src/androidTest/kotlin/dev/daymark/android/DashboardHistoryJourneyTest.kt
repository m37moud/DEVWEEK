package dev.daymark.android

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.daymark.app.App
import dev.daymark.plan.*
import dev.daymark.settings.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DashboardHistoryJourneyTest {
    @get:Rule val compose=createComposeRule()
    @Test fun dashboard_calendar_confirmation_time_and_before_after_preview_use_saved_records() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="dashboard-journey-${UUID.randomUUID()}.db"; val fixture=AttendanceTestApp(context,name)
        var shown by mutableStateOf(true)
        try {
            runBlocking { fixture.graph.settings.update(language=LanguagePreference.EN) }
            compose.setContent { if (shown) App(fixture.graph) }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("today-place").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("today-place").assertTextContains("Office",substring=true)
            compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("today-check") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("today-check").performScrollTo().performClick()
            compose.waitUntil(10_000) { fixture.locationRequests==1 && compose.onAllNodesWithText("Present").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("today-month-counts").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("today-report-state").performScrollTo().assertTextEquals("Saved on this phone · Google is not connected")
            compose.onNodeWithTag("nav-history").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("history-root").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("history-root").performScrollToNode(hasTestTag("history-calendar"))
            compose.onNodeWithTag("history-calendar").assertIsDisplayed()
            compose.onNodeWithTag("history-root").performScrollToNode(hasTestTag("history-record-2026-10-27"))
            compose.onNodeWithTag("history-confirmed-2026-10-27",useUnmergedTree=true).performScrollTo().assertIsDisplayed().assertTextContains(DisplayFormats.time(fixture.clock.now(),"UTC",AppLanguage.EN),substring=true)
            compose.onNodeWithTag("nav-plan").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("plan-add-time-off").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("plan-add-time-off").performScrollTo().performClick()
            compose.onNodeWithText("Holiday").performClick()
            compose.onNodeWithTag("time-off-preview").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("preview-before-2026-10-27").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("preview-before-2026-10-27").assertTextContains("Present",substring=true)
            compose.onNodeWithTag("preview-after-2026-10-27").assertTextContains("Holiday",substring=true)
            compose.onNodeWithTag("preview-evidence-2026-10-27").assertTextContains("Location checked manually",substring=true)
            compose.onNodeWithTag("preview-confirm").performClick()
            compose.waitUntil(10_000) { runBlocking { fixture.db.dao().tracker()?.let { fixture.db.dao().timeOff(it.id).isNotEmpty() }==true } }
            compose.onNodeWithTag("nav-today").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("today-next").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("today-next").performScrollTo().assertTextContains(DisplayFormats.date(LocalDate(2026,10,28),AppLanguage.EN),substring=true)
            Assert.assertEquals(1,fixture.locationRequests)
        } finally {
            compose.runOnIdle { shown=false }; compose.waitForIdle()
            fixture.graph.reporting.close(); fixture.db.close(); context.deleteDatabase(name)
        }
    }
}
