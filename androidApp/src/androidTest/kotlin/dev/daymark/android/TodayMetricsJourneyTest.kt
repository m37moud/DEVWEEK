package dev.daymark.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.daymark.app.App
import dev.daymark.history.HistoryMetric
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.models.attendance.Correction
import dev.daymark.models.attendance.DayKey
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LanguagePreference
import java.util.UUID
import java.io.File
import java.io.FileOutputStream
import android.graphics.Bitmap
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodayMetricsJourneyTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun english_tiles_open_displayed_month_details_and_return_to_today_without_gps() =
        verifyTiles(LanguagePreference.EN, AppLanguage.EN)

    @Test
    fun arabic_tiles_open_displayed_month_details_and_return_to_today_without_gps() =
        verifyTiles(LanguagePreference.AR, AppLanguage.AR)

    @Test
    fun english_history_tiles_open_details_and_return_without_gps() =
        verifyTiles(LanguagePreference.EN, AppLanguage.EN, "history")

    @Test
    fun arabic_history_tiles_open_details_and_return_without_gps() =
        verifyTiles(LanguagePreference.AR, AppLanguage.AR, "history")

    private fun verifyTiles(
        preference: LanguagePreference,
        language: AppLanguage,
        destination: String = "today"
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "today-metrics-${UUID.randomUUID()}.db"
        val fixture = AttendanceTestApp(context, name)
        var shown by mutableStateOf(true)
        val arabic = language == AppLanguage.AR
        val labels =
            if (arabic) listOf("إجمالي أيام العمل", "متأخر", "غائب", "الإجازات", "العطلات الرسمية", "المأموريات")
            else listOf("Total work days", "Late", "Absent", "Leave", "Official holidays", "Work missions")
        try {
            runBlocking {
                fixture.graph.settings.update(
                    language = preference,
                    displayName = if (arabic) "تجربة" else "Demo"
                )
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
            if (destination == "history") {
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithTag("nav-history").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithTag("nav-history").performClick()
            }
            val destinationRoot = hasTestTag("$destination-root")
            for ((index, metric) in HistoryMetric.entries.withIndex()) {
                val tile = hasTestTag("metric-${metric.name}") and hasAnyAncestor(destinationRoot)
                val count = DisplayFormats.number(if (index < 2) 1 else 0, language)
                val loadedTile = tile and hasText(labels[index]) and hasText(count)
                compose.waitUntil(10_000) {
                    compose.onAllNodes(loadedTile).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNode(tile).performScrollTo().assertHasClickAction()
                    .assertTextContains(labels[index]).assertTextContains(count)
                if (index == 0 && destination == "today") {
                    compose.onNodeWithTag("today-month-counts").performScrollTo()
                    compose.waitForIdle()
                    InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                        ?.let { screenshot ->
                            FileOutputStream(
                                File(
                                    context.getExternalFilesDir(null),
                                    "today-kpis-${language.name}.png"
                                )
                            ).use {
                                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                            }
                            screenshot.recycle()
                        }
                }
                compose.onNode(tile).performClick()
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithTag("history-detail-root").fetchSemanticsNodes().isNotEmpty() &&
                            compose.onAllNodesWithText(labels[index]).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onAllNodesWithText(labels[index]).onFirst().assertIsDisplayed()
                val monthEnd = DisplayFormats.date(LocalDate(2026, 10, 31), language)
                compose.onNodeWithText("${if (arabic) "حتى" else "Through"} · $monthEnd")
                    .assertIsDisplayed()
                compose.onNodeWithText(if (arabic) "رجوع" else "Back").performClick()
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithTag("$destination-root").fetchSemanticsNodes()
                        .isNotEmpty()
                }
                if (destination == "today") compose.onNodeWithTag("history-root")
                    .assertDoesNotExist()
            }
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
