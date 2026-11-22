package dev.daymark.android

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.daymark.app.App
import dev.daymark.history.AttendancePeriodSettings
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.plan.TimeOffKind
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.ThemePreference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class WorkMissionJourneyTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun history_saves_a_mission_and_returns_to_the_same_closing_period() =
        withApp(LanguagePreference.AR, ThemePreference.DARK) { fixture ->
            compose.onNodeWithTag("nav-history").performClick()
            waitForTag("metric-WORK_MISSION")
            compose.onNodeWithTag("history-root").performScrollToNode(hasTestTag("history-record-2026-10-28"))
            compose.onNodeWithTag("history-record-2026-10-28").performClick()
            compose.onNodeWithTag("history-work-mission-action").performScrollTo().performClick()
            waitForTag("work-mission-note")
            compose.onNodeWithTag("work-mission-note").performTextInput("زيارة عميل")
            confirmEdit()

            waitForTag("history-root")
            compose.onNodeWithTag("history-root").performScrollToNode(hasTestTag("metric-WORK_MISSION"))
            compose.onNodeWithTag("metric-WORK_MISSION").performClick()
            waitForTag("history-record-2026-10-28")
            compose.onNodeWithTag("history-detail-root").performScrollToNode(hasTestTag("history-record-2026-10-28"))
            compose.onNodeWithTag("history-record-2026-10-28").assertTextContains("زيارة عميل", substring = true)
            captureEvidence("work-missions-ar-dark")
            compose.onNodeWithTag("history-record-2026-10-28").performClick()
            waitForTag("time-off-remove")
            compose.onNodeWithTag("time-off-remove").performClick()
            confirmReview()
            waitForTag("history-detail-root")
            val date = LocalDate(2026, 10, 28)
            assertEquals(AttendanceStatus.ABSENT, runBlocking {
                fixture.graph.repository.attendance.observeRange(date, date).first().records.single().outcome.status
            })
            assertEquals(0, fixture.locationRequests)
        }

    @Test
    fun plan_saves_selected_days_across_months_without_filling_the_gap() = withApp { fixture ->
        compose.onNodeWithTag("nav-plan").performClick()
        waitForTag("plan-add-time-off")
        compose.onNodeWithTag("plan-add-time-off").performScrollTo().performClick()
        compose.onNodeWithText("Holiday").performClick()
        compose.onNodeWithTag("time-off-separate-days").performClick()
        compose.onNodeWithTag("time-off-select-2026-11-23").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Next month").performScrollTo().performClick()
        compose.onNodeWithTag("time-off-select-2026-12-01").performScrollTo().performClick()
        captureEvidence("separate-days-en-light", captureDialog())
        confirmEdit()

        compose.waitUntil(10_000) {
            runBlocking { fixture.graph.repository.timeOff.observeEntries().first().size == 2 }
        }
        val entries = runBlocking { fixture.graph.repository.timeOff.observeEntries().first() }
        assertEquals(setOf(LocalDate(2026, 11, 23), LocalDate(2026, 12, 1)), entries.map { it.from }.toSet())
        assertEquals(true, entries.all { it.kind == TimeOffKind.HOLIDAY && it.from == it.through })
    }

    private fun captureDialog(): Bitmap =
        requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())

    private fun captureEvidence(
        name: String,
        screenshot: Bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun confirmEdit() {
        compose.onNodeWithTag("time-off-preview").performClick()
        confirmReview()
    }

    private fun confirmReview() {
        waitForTag("preview-confirm")
        compose.onNodeWithTag("preview-confirm").performClick()
    }

    private fun waitForTag(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

    private fun withApp(
        language: LanguagePreference = LanguagePreference.EN,
        theme: ThemePreference = ThemePreference.LIGHT,
        runJourney: (AttendanceTestApp) -> Unit
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = "work-mission-journey-${UUID.randomUUID()}.db"
        val fixture = AttendanceTestApp(context, database)
        fixture.now = Instant.parse("2026-11-19T12:00:00Z")
        var visible by mutableStateOf(true)
        try {
            runBlocking {
                fixture.graph.settings.update(language = language, theme = theme)
                val timeOff = fixture.graph.repository.timeOff
                val period = requireNotNull(timeOff.previewAttendancePeriod(AttendancePeriodSettings(20)))
                timeOff.saveAttendancePeriod(period)
            }
            compose.setContent { if (visible) App(fixture.graph) }
            waitForTag("nav-history")
            runJourney(fixture)
        } finally {
            compose.runOnIdle { visible = false }
            compose.waitForIdle()
            fixture.db.close()
            context.deleteDatabase(database)
        }
    }
}
