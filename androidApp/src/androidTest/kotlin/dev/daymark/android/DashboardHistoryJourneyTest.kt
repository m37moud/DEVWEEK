package dev.daymark.android

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.daymark.app.App
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.models.plan.TimeOffCommand
import dev.daymark.plan.*
import dev.daymark.settings.*
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LanguagePreference
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardHistoryJourneyTest {
    @get:Rule val compose=createComposeRule()
    @Test fun future_time_off_keeps_its_status_and_glyph_in_the_week_strip() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "future-week-${UUID.randomUUID()}.db"
        val fixture = AttendanceTestApp(context, name)
        var shown by mutableStateOf(true)
        try {
            runBlocking {
                fixture.graph.settings.update(language = LanguagePreference.EN)
                val repository = fixture.graph.repository
                val current = repository.plans.observePlan().first()!!.current
                val change = current.copy(schedule = current.schedule.copy(
                    days = current.schedule.days - DayOfWeek.SATURDAY
                ))
                Assert.assertEquals(PlanMutationResult.Applied, repository.plans.applyChange(
                    change, repository.plans.previewChange(change).expectedRevision
                ))
                for ((kind, date) in listOf(
                    TimeOffKind.HOLIDAY to LocalDate(2026, 10, 28),
                    TimeOffKind.VACATION to LocalDate(2026, 10, 29)
                )) {
                    val command = TimeOffCommand.Add(kind, date, date)
                    Assert.assertEquals(PlanMutationResult.Applied, repository.timeOff.apply(
                        command, repository.timeOff.preview(command).expectedRevision
                    ))
                }
            }
            compose.setContent { if (shown) App(fixture.graph) }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("today-week").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("today-week").performScrollTo().assertIsDisplayed()
            for ((date, label, glyph) in listOf(
                Triple(LocalDate(2026, 10, 28), "Holiday", "—"),
                Triple(LocalDate(2026, 10, 29), "Vacation", "—"),
                Triple(LocalDate(2026, 10, 30), "Planned", "○"),
                Triple(LocalDate(2026, 10, 31), "Day off", "—")
            )) {
                val description = "${DisplayFormats.date(date, AppLanguage.EN)}: $label"
                compose.onNodeWithContentDescription(description).performScrollTo().assertIsDisplayed()
                compose.onNode(
                    hasText(glyph) and hasAnyAncestor(hasContentDescription(description)),
                    useUnmergedTree = true
                ).assertExists()
            }
            Assert.assertEquals(0, fixture.locationRequests)
        } finally {
            compose.runOnIdle { shown = false }
            compose.waitForIdle()
            fixture.graph.reporting.close()
            fixture.db.close()
            context.deleteDatabase(name)
        }
    }

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
