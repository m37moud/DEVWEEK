package dev.daymark.android

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.daymark.app.App
import dev.daymark.models.plan.TimeOffEntry
import dev.daymark.plan.TimeOffKind
import dev.daymark.settings.LanguagePreference
import java.util.UUID
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanJourneyTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun attendance_settings_protect_the_active_draft_and_load_the_map_only_when_editing_place() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "settings-journey-${UUID.randomUUID()}.db"
        val fixture = AttendanceTestApp(context, name)
        var shown by mutableStateOf(value = true)
        try {
            runBlocking { fixture.graph.settings.update(language = LanguagePreference.EN) }
            compose.setContent { if (shown) App(graph = fixture.graph) }
            waitForTag("nav-settings")
            compose.onNodeWithTag("nav-settings").performClick()
            waitForTag("settings-routine-edit")

            compose.onNodeWithContentDescription("Monday · Scheduled").assertExists()
            compose.onNodeWithTag("setup-place-map").assertDoesNotExist()
            compose.onNodeWithTag("settings-routine-edit").performScrollTo().performClick()
            compose.onNodeWithTag("plan-zone").performScrollTo().performClick()
            compose.onNodeWithTag("zone-search").performTextReplacement("Asia/Tokyo")
            compose.onNodeWithTag("zone-choice-Asia/Tokyo").performClick()
            compose.onNodeWithTag("settings-place-edit").assertIsNotEnabled()
            compose.onNodeWithTag("plan-zone").assertTextContains("Asia/Tokyo", substring = true)
            compose.onNodeWithTag("settings-routine-edit").performScrollTo().performClick()
            compose.onNodeWithTag("plan-review").assertDoesNotExist()

            compose.onNodeWithTag("settings-place-edit").performScrollTo().performClick()
            compose.onNodeWithTag("settings-routine-edit").assertIsNotEnabled()
            compose.onNodeWithTag("registered-place-address").assertExists()
            compose.onNodeWithTag("settings-place-edit").performScrollTo().performClick()
            compose.onNodeWithTag("setup-place-map").assertDoesNotExist()
        } finally {
            compose.runOnIdle { shown = false }
            compose.waitForIdle()
            fixture.db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun vacation_preview_changes_saved_attendance_and_balance_then_can_be_removed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "plan-journey-${UUID.randomUUID()}.db"
        val fixture = AttendanceTestApp(context, name)
        var shown by mutableStateOf(true)
        try {
            runBlocking { fixture.graph.settings.update(language = LanguagePreference.EN) }
            compose.setContent { if (shown) App(fixture.graph) }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("nav-plan").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("nav-settings").performClick()
            waitForTag("settings-routine-edit")
            compose.onNodeWithTag("plan-review").assertDoesNotExist()
            compose.onNodeWithTag("settings-routine-edit").performScrollTo().performClick()
            waitForTag("plan-zone")
            compose.onNodeWithTag("plan-zone").performScrollTo().performClick()
            compose.onNodeWithTag("zone-search").performTextReplacement("Asia/Tokyo")
            compose.onNodeWithTag("zone-choice-Asia/Tokyo").performClick()
            compose.onNodeWithTag("plan-review").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("preview-cancel").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("preview-cancel").performClick()
            compose.onNodeWithTag("settings-routine-edit").performScrollTo().performClick()
            compose.onNodeWithTag("plan-zone").assertDoesNotExist()
            compose.onNodeWithTag("settings-routine-edit").performScrollTo().performClick()
            compose.onNodeWithTag("plan-zone").assertTextContains("UTC", substring = true)
            Assert.assertEquals(
                "UTC",
                runBlocking { fixture.graph.repository.attendance.loadCurrentDay(fixture.clock.now())!!.plan.zoneId })
            compose.onNodeWithTag("nav-plan").performClick()
            waitForTag("plan-add-time-off")
            compose.onNodeWithTag("plan-add-time-off").performScrollTo().performClick()
            compose.onNodeWithTag("time-off-preview").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("preview-confirm").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("preview-confirm").performClick()
            compose.waitUntil(10_000) {
                runBlocking {
                    fixture.graph.repository.timeOff.observeEntries().first().isNotEmpty()
                }
            }
            Assert.assertEquals(
                9,
                runBlocking {
                    fixture.graph.repository.timeOff.observeAvailableBalance().first()
                })
            compose.onNodeWithTag("nav-history").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("history-root").fetchSemanticsNodes().isNotEmpty()
            }
            val history = compose.onNodeWithTag("history-root")
            history.performScrollToNode(hasTestTag("history-calendar"))
            val vacationDay = hasTestTag("calendar-date-2026-10-27") and
                    hasContentDescription("Vacation", substring = true)
            compose.waitUntil(10_000) { compose.onAllNodes(vacationDay).fetchSemanticsNodes().isNotEmpty() }
            history.performScrollToNode(hasTestTag("history-record-2026-10-27"))
            compose.onNodeWithTag("history-record-2026-10-27").performClick()
            compose.onNodeWithTag("history-time-off-action").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("time-off-remove").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("time-off-remove").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("preview-confirm").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("preview-confirm").performClick()
            compose.waitUntil(10_000) {
                runBlocking {
                    fixture.graph.repository.timeOff.observeEntries().first().isEmpty()
                }
            }
            Assert.assertEquals(
                10,
                runBlocking {
                    fixture.graph.repository.timeOff.observeAvailableBalance().first()
                })
        } finally {
            compose.runOnIdle { shown = false }; compose.waitForIdle()
            fixture.db.close(); context.deleteDatabase(name)
        }
    }

    @Test
    fun add_time_off_saves_a_single_pretracking_day_for_both_kinds() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "past-time-off-${UUID.randomUUID()}.db"
        val fixture = AttendanceTestApp(context, name)
        fixture.now = Instant.parse("2026-10-29T12:00:00Z")
        val pastDate = LocalDate(2026, 10, 26)
        val timeOff = fixture.graph.repository.timeOff
        var shown by mutableStateOf(true)
        try {
            runBlocking { fixture.graph.settings.update(language = LanguagePreference.EN) }
            compose.setContent { if (shown) App(fixture.graph) }
            waitForTag("nav-plan")
            compose.onNodeWithTag("nav-plan").performClick()
            waitForTag("plan-add-time-off")

            for (kind in listOf(TimeOffKind.VACATION, TimeOffKind.HOLIDAY)) {
                compose.onNodeWithTag("plan-add-time-off").performScrollTo().performClick()
                compose.onNodeWithText(if (kind == TimeOffKind.HOLIDAY) "Holiday" else "Vacation")
                    .performClick()
                pickCalendarDay("time-off-from", "October 26, 2026")
                if (kind == TimeOffKind.HOLIDAY) {
                    pickCalendarDay("time-off-through", "October 26, 2026")
                }
                submitTimeOff()
                compose.waitUntil(10_000) {
                    runBlocking { timeOff.observeEntries().first().any { it.kind == kind } }
                }
                val entry: TimeOffEntry = runBlocking {
                    timeOff.observeEntries().first().single { it.kind == kind }
                }
                Assert.assertEquals(pastDate, entry.from)
                Assert.assertEquals(pastDate, entry.through)
                val balance = runBlocking { timeOff.observeAvailableBalance().first() }
                Assert.assertEquals(if (kind == TimeOffKind.VACATION) 9 else 10, balance)
            }
            compose.onNodeWithTag("nav-today").performClick()
            waitForTag("today-vacation-balance")
            compose.onNodeWithTag("today-vacation-balance").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("today-time-off-action").performScrollTo().performClick()
            waitForTag("time-off-preview")
            compose.onNodeWithTag("time-off-from").assertIsDisplayed()
        } finally {
            compose.runOnIdle { shown = false }
            compose.waitForIdle()
            fixture.db.close()
            context.deleteDatabase(name)
        }
    }

    private fun pickCalendarDay(field: String, accessibleDate: String) {
        compose.onNodeWithTag(field).performClick()
        compose.onNode(hasText(accessibleDate, substring = true) and hasClickAction()).performClick()
        compose.onNodeWithTag("time-off-date-save").performClick()
    }

    private fun submitTimeOff() {
        compose.onNodeWithTag("time-off-preview").performClick()
        waitForTag("preview-confirm")
        compose.onNodeWithTag("preview-confirm").assertIsEnabled().performClick()
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
