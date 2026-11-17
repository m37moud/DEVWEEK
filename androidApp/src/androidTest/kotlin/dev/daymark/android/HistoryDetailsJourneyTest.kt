package dev.daymark.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.daymark.app.App
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.models.attendance.Correction
import dev.daymark.models.attendance.DayKey
import dev.daymark.settings.LanguagePreference
import dev.daymark.reporting.REPORT_TEXT_LIMIT
import androidx.compose.ui.semantics.SemanticsProperties
import java.util.UUID
import java.io.File
import java.io.FileOutputStream
import android.graphics.Bitmap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryDetailsJourneyTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun attendance_kpi_includes_late_and_opens_shared_range_details_without_gps() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "history-details-${UUID.randomUUID()}.db"
        val fixture = AttendanceTestApp(context, name)
        var shown by mutableStateOf(true)
        try {
            runBlocking {
                fixture.graph.settings.update(language = LanguagePreference.EN)
                val tracker = fixture.db.profileDao().tracker()!!
                fixture.graph.repository.attendance.correct(
                    Correction(
                        DayKey(tracker.id, LocalDate(2026, 10, 27)),
                        AttendanceStatus.LATE,
                        LocalTime(9, 10),
                        null
                    )
                )
            }
            compose.setContent { if (shown) App(fixture.graph) }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("nav-history").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("nav-history").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Attendance").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Attendance").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("history-detail-root").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Apply date range").assertIsDisplayed()
            compose.onNodeWithTag("history-detail-root")
                .performScrollToNode(hasTestTag("history-record-2026-10-27"))
            compose.onNodeWithTag("history-record-2026-10-27").assertIsDisplayed()
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                ?.let { screenshot ->
                    FileOutputStream(
                        File(
                            context.getExternalFilesDir(null),
                            "history-details.png"
                        )
                    ).use {
                        screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    screenshot.recycle()
                }
            compose.onNodeWithTag("history-record-2026-10-27").performClick()
            compose.onNodeWithText("Save").assertIsDisplayed()
            compose.onNodeWithTag("correction-note")
                .performTextInput("x".repeat(REPORT_TEXT_LIMIT + 1))
            compose.onNodeWithTag("correction-save").performClick()
            compose.onNodeWithTag("correction-note")
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithTag("history-record-2026-10-27").performClick()
            compose.onNodeWithTag("correction-note")
                .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
            assertEquals(0, fixture.locationRequests)
        } finally {
            compose.runOnIdle { shown = false }
            compose.waitForIdle()
            fixture.graph.reporting.close()
            fixture.db.close()
            context.deleteDatabase(name)
        }
    }
}
